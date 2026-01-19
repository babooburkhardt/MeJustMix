package com.example.mejustmix.services

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingDeque

data class FluidNCStatus(
    val state: String = "Disconnected",
    val message: String = ""
)

class FluidNCService(
    private val context: Context,
    private val onStatusChange: (FluidNCStatus) -> Unit,
    private val onGCodeSent: (String) -> Unit
) {
    companion object {
        private const val TAG = "FluidNCService"
        private const val DEFAULT_PORT = 23 // Telnet Port
        private const val CONNECTION_TIMEOUT_MS = 5000
        private const val HEARTBEAT_INTERVAL_MS = 2000L
        private const val COMMAND_TIMEOUT_MS = 10000L
        private const val WAKELOCK_TIMEOUT_MS = 600000L // 10 minutes
        private const val MAX_RECONNECT_ATTEMPTS = 60
        private const val GRACE_PERIOD_MS = 3000L // <--- NEW: 3 Seconds Grace
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var statusSuppressionJob: Job? = null // <--- NEW: Timer job

    private val commandQueue = LinkedBlockingDeque<String>()

    private var isProcessingCommand = false
    private var currentCommand: String? = null

    private var reconnectAttempts = 0
    private var isManualDisconnect = false
    private var isVerified = false
    private var bypassVerification = false

    // <--- NEW: Suppression Flag
    private var isSuppressingStatus = false 

    private var lastIp: String = ""
    private var lastPort: Int = DEFAULT_PORT

    private val wakeLock: PowerManager.WakeLock by lazy {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeJustMix:FluidNCService")
    }

    fun setBypassVerification(bypass: Boolean) {
        this.bypassVerification = bypass
        if (bypass && socket?.isConnected == true) {
            isVerified = true
            processNextCommand()
        }
    }

    // <--- NEW: Wrapper to intercept status updates
    private fun notifyStatus(status: FluidNCStatus) {
        // 1. If we manually disconnected, always tell the truth immediately
        if (isManualDisconnect) {
            onStatusChange(status)
            return
        }

        // 2. If we just got Connected, we survived! Cancel grace period.
        if (status.state == "Connected" || status.state == "Idle" || status.state == "Run") {
            if (isSuppressingStatus) {
                Log.i(TAG, "Grace period saved us! Reconnected silently.")
            }
            isSuppressingStatus = false
            statusSuppressionJob?.cancel()
            onStatusChange(status)
            return
        }

        // 3. If we are currently suppressing errors, SHHH! Don't tell the UI yet.
        if (isSuppressingStatus) {
            Log.d(TAG, "Suppressing status: ${status.state} (Grace period active)")
            return
        }

        // 4. Otherwise, behave normally
        onStatusChange(status)
    }

    fun connect(ipAddress: String, port: Int = DEFAULT_PORT) {
        lastIp = ipAddress
        lastPort = port
        // Don't reset manual disconnect here if we are in a silent reconnect loop
        if (!isSuppressingStatus) {
            isManualDisconnect = false
        }
        
        isVerified = bypassVerification
        // Don't reset attempts if suppressing, so we keep counting
        if (!isSuppressingStatus) {
            reconnectAttempts = 0
        }

        scope.launch {
            try {
                disconnectInternal() 
                
                notifyStatus(FluidNCStatus("Connecting...", "Target: $ipAddress:$port"))
                
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT_MS)
                
                socket = newSocket
                outputStream = newSocket.getOutputStream()
                
                if (!wakeLock.isHeld) wakeLock.acquire(WAKELOCK_TIMEOUT_MS)

                // Resume logic
                if (!commandQueue.isEmpty()) {
                    Log.i(TAG, "Resuming with ${commandQueue.size} pending commands")
                }

                // Start Reader
                startReader(newSocket)
                
                // Handshake
                if (bypassVerification) {
                    notifyStatus(FluidNCStatus("Connected", "Bypassed verification"))
                } else {
                    try {
                        sendRaw("\n") // Flush
                        delay(100)
                        sendRaw("?")  // Query
                    } catch (e: Exception) {
                        Log.e(TAG, "Handshake failed", e)
                    }
                }
                
                startHeartbeat()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                notifyStatus(FluidNCStatus("Error", "Failed: ${e.message}"))
                handleDisconnect()
            }
        }
    }

    private fun startReader(socket: Socket) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && socket.isConnected) {
                    val line = reader.readLine() 
                    if (line != null) {
                        if (line.isNotEmpty()) processLine(line.trim())
                    } else {
                        throw Exception("Socket closed by remote")
                    }
                }
            } catch (e: Exception) {
                if (!isManualDisconnect) {
                    Log.e(TAG, "Reader error", e)
                    handleDisconnect()
                }
            }
        }
    }

    private fun processLine(line: String) {
        val cleanLine = line.trim()
        
        if (cleanLine == "?" || cleanLine.isEmpty()) return

        // 1. Verify connection
        if (!isVerified && !bypassVerification) {
            if (cleanLine.contains("FluidNC") || cleanLine.contains("Grbl") || cleanLine.startsWith("<") || cleanLine == "ok") {
                isVerified = true
                reconnectAttempts = 0
                notifyStatus(FluidNCStatus("Connected", "Verified (Telnet)"))
                processNextCommand()
            }
        }

        // 2. Logic
        if (cleanLine.startsWith("<")) {
            val state = parseState(cleanLine)
            notifyStatus(FluidNCStatus(state, cleanLine))
        } else if (cleanLine.equals("ok", ignoreCase = true)) {
            if (isProcessingCommand) {
                isProcessingCommand = false
                currentCommand = null
                processNextCommand()
            }
        } else if (cleanLine.startsWith("error:") || cleanLine.startsWith("ALARM:")) {
            Log.e(TAG, "FluidNC Error: $cleanLine")
            notifyStatus(FluidNCStatus("Error", cleanLine))
            if (isProcessingCommand) {
                isProcessingCommand = false
                currentCommand = null
                processNextCommand()
            }
        }
    }

    fun sendRealtime(command: String) {
        sendRaw(command) 
    }

    fun sendMultiple(commands: List<String>) {
        Log.d(TAG, "Queueing ${commands.size} commands")
        for (cmd in commands) commandQueue.offer(cmd)
        processNextCommand()
    }

    private fun processNextCommand() {
        if (isProcessingCommand || !isVerified || commandQueue.isEmpty()) return

        val command = commandQueue.poll() ?: return

        isProcessingCommand = true
        currentCommand = command

        sendRaw(command + "\n")
        onGCodeSent(command) 

        scope.launch {
            delay(COMMAND_TIMEOUT_MS)
            if (isProcessingCommand && currentCommand == command) {
                Log.w(TAG, "Command timeout - forcing next")
                isProcessingCommand = false
                currentCommand = null
                processNextCommand()
            }
        }
    }

    private fun sendRaw(text: String) {
        try {
            outputStream?.let {
                it.write(text.toByteArray())
                it.flush()
            } ?: run {
                handleDisconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send", e)
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        scope.launch {
            // <--- NEW: START GRACE PERIOD
            if (!isManualDisconnect && !isSuppressingStatus) {
                isSuppressingStatus = true
                statusSuppressionJob?.cancel()
                statusSuppressionJob = scope.launch {
                    delay(GRACE_PERIOD_MS)
                    if (isSuppressingStatus) {
                        // Time's up! We are still broken.
                        isSuppressingStatus = false
                        onStatusChange(FluidNCStatus("Disconnected", "Connection lost > 3s"))
                    }
                }
            }

            disconnectInternal()
            if (!isManualDisconnect) {
                attemptReconnect()
            }
        }
    }

    private suspend fun attemptReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            
            // <--- NEW: Retry aggressively (200ms) at first to beat the 3s timer
            val delayMs = if (reconnectAttempts <= 5) 200L else 2000L
            
            notifyStatus(FluidNCStatus("Reconnecting", "Attempt $reconnectAttempts..."))
            delay(delayMs)
            connect(lastIp, lastPort)
        } else {
            notifyStatus(FluidNCStatus("Error", "Could not connect"))
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                if (isVerified && socket?.isConnected == true) {
                    try {
                        sendRaw("?") 
                    } catch (e: Exception) {}
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun parseState(line: String): String {
        return line.trim('<', '>').split('|').firstOrNull() ?: "Connected"
    }

    fun disconnect() {
        isManualDisconnect = true
        scope.launch {
            disconnectInternal()
            notifyStatus(FluidNCStatus("Disconnected"))
        }
    }

    private fun disconnectInternal() {
        try {
            heartbeatJob?.cancel()
            readerJob?.cancel()
            socket?.close()
            
            // If handling unexpected drop, push command back to front
            if (!isManualDisconnect && currentCommand != null) {
                commandQueue.offerFirst(currentCommand)
            }
            if (isManualDisconnect) {
                commandQueue.clear()
            }

            isProcessingCommand = false
            currentCommand = null
            isVerified = false
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        } finally {
            socket = null
            outputStream = null
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}