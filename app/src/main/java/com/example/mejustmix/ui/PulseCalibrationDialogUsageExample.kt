package com.example.mejustmix.ui

/**
 * UPDATED EXAMPLE: How to use PulseCalibrationDialog with corrected workflow
 * 
 * CORRECTED WORKFLOW:
 * 1. Scroll wheel TRACKS offset visually (doesn't move pump)
 * 2. Prime button MOVES pump to tracked offset
 * 3. Dispense pulses for calibration
 * 4. Measure and calculate mL/pulse
 * 
 * Steps per pulse is KNOWN (~267), only mL/pulse needs calibration.
 */

/*

// In your composable function, add a state for showing the dialog:
var showPulseCalibrationForIndex by rememberSaveable { mutableStateOf<Int?>(null) }

// Add this somewhere in your UI (e.g., in calibration chooser):
Button(
    onClick = { 
        showPulseCalibrationForIndex = pumpIndex
    },
    modifier = Modifier.fillMaxWidth()
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pulse Mode Calibration", fontWeight = FontWeight.Bold)
        Text("Calibrate mL per pulse", style = MaterialTheme.typography.labelSmall)
    }
}

// Add the dialog itself after your other dialogs:
if (showPulseCalibrationForIndex != null) {
    val pumpIndex = showPulseCalibrationForIndex!!
    val pump = uiState.pumps[pumpIndex]
    
    PulseCalibrationDialog(
        pump = pump,
        pumpIndex = pumpIndex,
        onDismiss = { 
            showPulseCalibrationForIndex = null 
        },
        onSave = { mlPerPulse ->
            // Save ONLY the mL per pulse (steps per pulse is known from motor specs)
            settingsViewModel.updatePumpMlPerPulse(pumpIndex, mlPerPulse)
            showPulseCalibrationForIndex = null
        },
        onDispensePulses = { pulseCount ->
            // Dispense the test pulses (pump should be at home)
            settingsViewModel.dispensePulsesForCalibration(pumpIndex, pulseCount)
        },
        onPrimeToPulseHome = {
            // Prime pump to move to the tracked home position
            settingsViewModel.primePumpToHome(pumpIndex)
        }
    )
}

// IMPORTANT: The scroll wheel in Step 1 automatically updates the tracked offset
// You don't need to wire up anything for the scroll wheel - it just updates
// pump.pulseHomeOffset internally as the user drags it.

*/

/**
 * KEY DIFFERENCES from old version:
 * 
 * OLD (WRONG):
 * - onJogPump callback that sent G-code on every drag
 * - onMarkHome callback to save position
 * - onSave took (stepsPerPulse, mlPerPulse)
 * - User manually set steps per pulse
 * 
 * NEW (CORRECT):
 * - No jogPump - scroll wheel just tracks visually
 * - onPrimeToPulseHome - one button to actually move pump
 * - onSave takes only mlPerPulse
 * - Steps per pulse is auto-calculated (~267)
 * - System remembers offset and applies during real dispensing
 */

/**
 * EXAMPLE: Minimal integration in any composable
 */
/*
@Composable
fun MyPulseCalibrationButton(
    pumpIndex: Int,
    pump: PumpConfig,
    viewModel: SettingsViewModel
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Button(onClick = { showDialog = true }) {
        Text("Calibrate Pulse Mode")
    }
    
    if (showDialog) {
        PulseCalibrationDialog(
            pump = pump,
            pumpIndex = pumpIndex,
            onDismiss = { showDialog = false },
            onSave = { mlPerPulse ->
                // Only save mL/pulse, steps/pulse is known
                viewModel.updatePumpMlPerPulse(pumpIndex, mlPerPulse)
                showDialog = false
            },
            onDispensePulses = { pulseCount ->
                // Dispense test pulses
                viewModel.dispensePulsesForCalibration(pumpIndex, pulseCount)
            },
            onPrimeToPulseHome = {
                // Actually move pump to home
                viewModel.primePumpToHome(pumpIndex)
            }
        )
    }
}
*/

/**
 * HOW IT WORKS:
 * 
 * Step 1 - Visual Homing:
 * - User drags scroll wheel
 * - Scroll wheel tracks offset in pump.pulseHomeOffset
 * - NO motor commands sent yet
 * - Just remembers "pump needs to move X steps to reach home"
 * 
 * Step 2 - Prime to Home:
 * - User clicks "Prime to Home" button
 * - System calculates: stepsToMove = pump.pulseHomeOffset % stepsPerPulse
 * - Sends G-code: "G0 {axis}{stepsToMove}"
 * - After successful prime, sets pump.pulseHomeOffset = 0
 * - Pump is now physically at home position
 * 
 * Step 3 - Dispense:
 * - User clicks "Dispense 10 Pulses"
 * - System calculates: totalSteps = 10 * 267 = 2670 steps
 * - Sends G-code: "G0 {axis}2670"
 * - Updates pump.pulseHomeOffset = 2670 % 267 = 0 (back at home!)
 * 
 * Step 4 - Measure:
 * - User enters measured volume (e.g., 5.0 mL)
 * - System calculates: mlPerPulse = 5.0 / 10 = 0.5 mL/pulse
 * - Saves this value to pump.mlPerPulse
 * 
 * During Normal Dispensing:
 * - User requests 2mL of cyan
 * - System: 2mL / 0.5mL/pulse = 4 pulses
 * - System: 4 pulses * 267 steps = 1068 steps
 * - Before dispensing: Check pump.pulseHomeOffset, prime if needed
 * - Dispense: Send "G0 X1068"
 * - Update pump.pulseHomeOffset for next time
 */
