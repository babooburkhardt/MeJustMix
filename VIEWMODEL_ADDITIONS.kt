package com.example.mejustmix.ui

// ADD THESE IMPORTS to your SettingsViewModel.kt
import com.example.mejustmix.utils.PulseModeCalculator

// ADD THESE FUNCTIONS to your SettingsViewModel class:

/**
 * Jog a pump by a specific number of steps.
 * This rotates the pump to position it at the home position.
 * 
 * @param pumpIndex Index of the pump to jog (0-4)
 * @param steps Number of steps to move (positive = forward, negative = backward)
 */
fun jogPump(pumpIndex: Int, steps: Int) {
    val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
    
    // TODO: Send G-code command to your motor controller
    // Example format (adjust for your controller):
    // val gcode = "G0 ${pump.axis}$steps"
    // sendGCodeToController(gcode)
    
    // For now, just update the UI state to track position
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = pump.pulseHomeOffset + steps
        )
        state.copy(pumps = updatedPumps)
    }
}

/**
 * Mark a pump as homed by snapping to the nearest pulse boundary.
 * Call this when the user has visually aligned the pump and clicks "Mark as Home".
 * 
 * @param pumpIndex Index of the pump to mark as homed
 */
fun markPumpAsHomed(pumpIndex: Int) {
    _uiState.update { state ->
        val pump = state.pumps.getOrNull(pumpIndex) ?: return@update state
        
        // Calculate nearest pulse boundary
        val nearestHome = PulseModeCalculator.snapToPulseBoundary(
            pump.pulseHomeOffset,
            pump.stepsPerPulse
        )
        
        // Update pump to be at exact home position
        val updatedPumps = state.pumps.toMutableList()
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = nearestHome
        )
        
        state.copy(pumps = updatedPumps)
    }
    
    // Optional: Show confirmation toast
    showToast("${_uiState.value.pumps[pumpIndex].name} pump homed successfully")
}

/**
 * Update a pump's pulse calibration values after calibration is complete.
 * 
 * @param pumpIndex Index of the pump
 * @param stepsPerPulse Steps for one complete pulse (typically ~267)
 * @param mlPerPulse Measured mL dispensed per pulse
 */
fun updatePumpCalibration(pumpIndex: Int, stepsPerPulse: Float, mlPerPulse: Float) {
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        val pump = updatedPumps[pumpIndex]
        
        updatedPumps[pumpIndex] = pump.copy(
            stepsPerPulse = stepsPerPulse,
            mlPerPulse = mlPerPulse
        )
        
        state.copy(pumps = updatedPumps)
    }
    
    saveSettings()
    showToast("${_uiState.value.pumps[pumpIndex].name} calibration saved")
}

/**
 * Dispense a specific number of pulses for calibration testing.
 * 
 * @param pumpIndex Index of the pump
 * @param pulseCount Number of pulses to dispense
 * @param stepsPerPulse Steps per pulse for this pump
 */
fun dispensePulses(pumpIndex: Int, pulseCount: Int, stepsPerPulse: Float) {
    val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
    
    val totalSteps = (pulseCount * stepsPerPulse).toInt()
    
    // TODO: Send G-code command to dispense
    // Example format (adjust for your controller):
    // val gcode = "G0 ${pump.axis}$totalSteps"
    // sendGCodeToController(gcode)
    
    showToast("Dispensing $pulseCount pulses ($totalSteps steps) from ${pump.name}")
}

// EXAMPLE: How to connect to your G-code sender
// (You'll need to implement this based on your existing communication system)

private fun sendGCodeToController(gcode: String) {
    // TODO: Replace with your actual G-code sending logic
    // Examples:
    
    // If using HTTP:
    // val url = "http://${_uiState.value.ipAddress}:${_uiState.value.webPortalPort}/gcode"
    // httpClient.post(url) { body = gcode }
    
    // If using WebSocket:
    // webSocket.send(gcode)
    
    // If using Serial:
    // serialPort.write(gcode.toByteArray())
    
    // Placeholder for now:
    println("G-code: $gcode")
}

/* 
 * USAGE EXAMPLES:
 * 
 * // In your UI composable where you show PulseCalibrationDialog:
 * PulseCalibrationDialog(
 *     pump = selectedPump,
 *     pumpIndex = selectedIndex,
 *     onDismiss = { showDialog = false },
 *     onSave = { stepsPerPulse, mlPerPulse ->
 *         viewModel.updatePumpCalibration(selectedIndex, stepsPerPulse, mlPerPulse)
 *         showDialog = false
 *     },
 *     onDispensePulses = { pulseCount, stepsPerPulse ->
 *         viewModel.dispensePulses(selectedIndex, pulseCount, stepsPerPulse)
 *     },
 *     onJogPump = { steps, stepsPerPulse ->
 *         viewModel.jogPump(selectedIndex, steps)
 *     },
 *     onMarkHome = {
 *         viewModel.markPumpAsHomed(selectedIndex)
 *     }
 * )
 * 
 * // Or in a test screen:
 * PulseHomeTestScreen(
 *     pumps = uiState.pumps,
 *     onNavigateBack = { navController.popBackStack() },
 *     onJogPump = { pumpIndex, steps -> 
 *         viewModel.jogPump(pumpIndex, steps) 
 *     },
 *     onMarkHome = { pumpIndex -> 
 *         viewModel.markPumpAsHomed(pumpIndex) 
 *     }
 * )
 */
