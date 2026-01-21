package com.example.mejustmix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.mejustmix.services.KSPigmentDatabase
import com.example.mejustmix.services.PigmentStrengths
import com.example.mejustmix.ui.PumpConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Manages multiple paint mixer machine profiles.
 * Handles persistence, active machine selection, and machine discovery.
 */
class MachineManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("machine_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _machines = MutableStateFlow<List<MachineProfile>>(emptyList())
    val machines: StateFlow<List<MachineProfile>> = _machines.asStateFlow()
    
    private val _activeMachine = MutableStateFlow<MachineProfile?>(null)
    val activeMachine: StateFlow<MachineProfile?> = _activeMachine.asStateFlow()
    
    init {
        loadMachines()
    }
    
    /**
     * Load all machine profiles from SharedPreferences.
     */
    private fun loadMachines() {
        try {
            val json = prefs.getString("machines_json", null)
            if (json != null) {
                val type = object : TypeToken<List<MachineProfile>>() {}.type
                val loadedMachines: List<MachineProfile> = gson.fromJson(json, type)
                _machines.value = loadedMachines
                
                // Restore active machine
                val activeMachine = loadedMachines.firstOrNull { it.isActive }
                _activeMachine.value = activeMachine
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Save all machine profiles to SharedPreferences.
     */
    private fun saveMachines() {
        try {
            val json = gson.toJson(_machines.value)
            prefs.edit().putString("machines_json", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Add a new machine profile.
     */
    fun addMachine(profile: MachineProfile) {
        val newProfile = profile.copy(id = UUID.randomUUID().toString())
        _machines.update { currentMachines ->
            currentMachines + newProfile
        }
        saveMachines()
    }
    
    /**
     * Remove a machine profile by ID.
     */
    fun removeMachine(id: String) {
        _machines.update { currentMachines ->
            currentMachines.filterNot { it.id == id }
        }
        
        // If removed machine was active, clear active machine
        if (_activeMachine.value?.id == id) {
            _activeMachine.value = null
        }
        
        saveMachines()
    }
    
    /**
     * Set the active machine by ID.
     */
    fun setActiveMachine(id: String) {
        _machines.update { currentMachines ->
            currentMachines.map { machine ->
                machine.copy(isActive = machine.id == id)
            }
        }
        
        _activeMachine.value = _machines.value.firstOrNull { it.id == id }
        saveMachines()
    }
    
    /**
     * Update an existing machine profile.
     */
    fun updateMachine(updatedProfile: MachineProfile) {
        _machines.update { currentMachines ->
            currentMachines.map { machine ->
                if (machine.id == updatedProfile.id) updatedProfile else machine
            }
        }
        
        // Update active machine if it was updated
        if (_activeMachine.value?.id == updatedProfile.id) {
            _activeMachine.value = updatedProfile
        }
        
        saveMachines()
    }
    
    /**
     * Update connection status for a machine.
     */
    fun updateConnectionStatus(id: String, isConnected: Boolean) {
        val timestamp = if (isConnected) System.currentTimeMillis() else null
        
        _machines.update { currentMachines ->
            currentMachines.map { machine ->
                if (machine.id == id) {
                    machine.copy(
                        isConnected = isConnected,
                        lastConnected = timestamp ?: machine.lastConnected
                    )
                } else {
                    machine
                }
            }
        }
        
        // Update active machine if it was updated
        if (_activeMachine.value?.id == id) {
            _activeMachine.value = _machines.value.firstOrNull { it.id == id }
        }
        
        saveMachines()
    }
    
    /**
     * Find a machine by BLE device name.
     */
    fun findMachineByBLEName(deviceName: String): MachineProfile? {
        return _machines.value.firstOrNull { 
            it.connectionType == ConnectionType.BLE && it.bleDeviceName == deviceName 
        }
    }
    
    /**
     * Find a machine by BLE MAC address.
     */
    fun findMachineByBLEAddress(address: String): MachineProfile? {
        return _machines.value.firstOrNull { 
            it.connectionType == ConnectionType.BLE && it.bleAddress == address 
        }
    }
    
    /**
     * Get all BLE machines.
     */
    fun getBLEMachines(): List<MachineProfile> {
        return _machines.value.filter { it.connectionType == ConnectionType.BLE }
    }
    
    /**
     * Get all WiFi machines.
     */
    fun getWiFiMachines(): List<MachineProfile> {
        return _machines.value.filter { it.connectionType == ConnectionType.WIFI }
    }
    
    /**
     * Check if any machines are configured.
     */
    fun hasMachines(): Boolean {
        return _machines.value.isNotEmpty()
    }
}
