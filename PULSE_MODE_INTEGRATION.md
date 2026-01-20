# Pulse Mode Scroll Wheel - Integration Guide

## Overview

This implementation provides an interactive scroll wheel for homing peristaltic pumps in pulse mode. Users can digitally rotate the pump rollers to align them with a home position, ensuring consistent dispensing.

## Key Features

- **Interactive drag-to-rotate interface** - Visual representation of 3-roller pump
- **Automatic step calculations** - Based on motor specifications (1.8° stepper, 1:4 gear reduction, 3 rollers)
- **Per-pump homing** - Each pump can be independently homed
- **Real-time feedback** - Shows distance from home position
- **Snap-to-home** - Automatically aligns to nearest pulse boundary

## Motor Specifications

Your system uses:
- **Stepper Motor**: 1.8° step angle (200 steps/revolution)
- **Gear Reduction**: 1:4 ratio
- **Pump Configuration**: 3-roller peristaltic pump

### Calculated Values:
```
Steps per motor revolution: 360° / 1.8° = 200 steps
Steps per pump revolution: 200 × 4 = 800 steps
Steps per pulse (1 roller rotation): 800 / 3 ≈ 266.67 steps
```

## Files Created

### 1. `PulseHomeScrollWheel.kt`
Main scroll wheel components:
- `PulseHomeScrollWheel` - Full-featured wheel with all controls
- `CompactPulseHomeWheel` - Simplified version for dialogs
- `calculateStepsPerPulse()` - Utility function for step calculations

### 2. `PulseHomeTestScreen.kt`
Standalone test screen for development:
- Select individual pumps to home
- Visual feedback and status
- Motor specification display

### 3. `PulseModeCalculator.kt`
Utility functions for pulse mode calculations:
- Step/pulse/mL conversions
- Home position calculations
- Dispensing error calculations

### 4. Modified: `PulseCalibrationDialog.kt`
Integrated scroll wheel into Step 2 (Visual Homing)

## Integration Instructions

### Step 1: Update Your ViewModel

Add handler functions to your ViewModel (likely `SettingsViewModel` or `MixViewModel`):

```kotlin
// In your ViewModel
fun jogPump(pumpIndex: Int, steps: Int) {
    // Send jog command to your motor controller
    // Example: sendGCode("G0 ${pumps[pumpIndex].axis}$steps")
    
    // Update pump's home offset
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        val pump = updatedPumps[pumpIndex]
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = pump.pulseHomeOffset + steps
        )
        state.copy(pumps = updatedPumps)
    }
}

fun markPumpAsHomed(pumpIndex: Int) {
    // Snap to nearest home position
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        val pump = updatedPumps[pumpIndex]
        
        // Calculate nearest home
        val nearestHome = PulseModeCalculator.snapToPulseBoundary(
            pump.pulseHomeOffset,
            pump.stepsPerPulse
        )
        
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = nearestHome
        )
        state.copy(pumps = updatedPumps)
    }
    
    // Optionally show a toast
    showToast("${pumps[pumpIndex].name} homed successfully")
}
```

### Step 2: Add Navigation (Optional)

If you want a dedicated test screen, add to your navigation:

```kotlin
// In your navigation composable
composable("pulse_home_test") {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    PulseHomeTestScreen(
        pumps = uiState.pumps,
        onNavigateBack = { navController.popBackStack() },
        onJogPump = { pumpIndex, steps -> viewModel.jogPump(pumpIndex, steps) },
        onMarkHome = { pumpIndex -> viewModel.markPumpAsHomed(pumpIndex) }
    )
}
```

### Step 3: Use in Calibration Dialog

The scroll wheel is already integrated into `PulseCalibrationDialog`. To use it:

```kotlin
PulseCalibrationDialog(
    pump = currentPump,
    pumpIndex = selectedPumpIndex,
    onDismiss = { /* dismiss */ },
    onSave = { stepsPerPulse, mlPerPulse ->
        // Save calibration values
        viewModel.updatePumpCalibration(selectedPumpIndex, stepsPerPulse, mlPerPulse)
    },
    onDispensePulses = { pulseCount, stepsPerPulse ->
        // Dispense test pulses
        val totalSteps = (pulseCount * stepsPerPulse).toInt()
        viewModel.dispensePulses(selectedPumpIndex, totalSteps)
    },
    onJogPump = { steps, stepsPerPulse ->
        // Jog the pump
        viewModel.jogPump(selectedPumpIndex, steps)
    },
    onMarkHome = {
        // Mark as homed
        viewModel.markPumpAsHomed(selectedPumpIndex)
    }
)
```

## Usage Flow

### For Users:

1. **Open calibration dialog** for a specific pump
2. **Step 1**: Set steps per pulse (auto-calculated: ~267 steps)
3. **Step 2**: Use scroll wheel to home the pump
   - Drag the visual wheel to rotate rollers
   - Or use fine-adjustment buttons (±10%)
   - Click "Snap to Nearest Home" to auto-align
   - Click "Mark as Home Position" when aligned
4. **Step 3**: Dispense test pulses
5. **Step 4**: Measure output and save calibration

### Positioning Guidelines for Users:

The home position should be where a roller is **just past the compression point** where it releases the tube. This ensures:
- Each pulse starts and ends at the same mechanical position
- Consistent volume dispensing
- No partial pulses

## Communication with Motor Controller

Your `onJogPump` handler needs to send commands to your motor controller. Example:

```kotlin
fun jogPump(pumpIndex: Int, steps: Int) {
    val pump = pumps[pumpIndex]
    val axis = pump.axis // "X", "Y", "Z", etc.
    
    // Construct G-code command
    val direction = if (steps > 0) "" else "-"
    val absSteps = kotlin.math.abs(steps)
    val gcode = "G0 $axis$direction$absSteps\n"
    
    // Send to your motor controller
    sendGCodeCommand(gcode)
    
    // Update local state
    updatePumpOffset(pumpIndex, steps)
}
```

## Step Calculation Details

The scroll wheel handles all step calculations internally. You only need to:

1. **Receive step deltas** from `onOffsetChange` callback
2. **Send steps to motor controller** via your existing G-code interface
3. **Track cumulative offset** for UI feedback

The wheel automatically:
- Converts drag gestures to steps
- Calculates distance from home
- Determines when pump is aligned (within 1 step tolerance)

## Motor Specs Configuration

If your motor specs differ, update in `PulseModeCalculator.kt`:

```kotlin
object MotorSpecs {
    const val STEP_ANGLE_DEGREES = 1.8f    // Change if using different motor
    const val GEAR_REDUCTION = 4f           // Change if different gearing
    const val ROLLER_COUNT = 3              // Change if different pump
}
```

Or pass custom values to `calculateStepsPerPulse()`:

```kotlin
val stepsPerPulse = calculateStepsPerPulse(
    stepAngle = 0.9f,      // 0.9° motor
    gearReduction = 5f,    // 1:5 reduction
    rollerCount = 2        // 2-roller pump
)
```

## Utility Functions

Use `PulseModeCalculator` for all pulse-related calculations:

```kotlin
// Convert volume to steps
val steps = PulseModeCalculator.pulsesToSteps(
    PulseModeCalculator.millilitersToPulses(targetMl, pump.mlPerPulse),
    pump.stepsPerPulse
)

// Check if pump is homed
val isHomed = PulseModeCalculator.isAtPulseBoundary(
    pump.pulseHomeOffset,
    pump.stepsPerPulse
)

// Calculate dispensing error
val error = PulseModeCalculator.dispensingError(requestedMl, pump.mlPerPulse)
```

## Testing

### Test the Scroll Wheel Independently:

1. Navigate to `PulseHomeTestScreen`
2. Select a pump
3. Drag the wheel and verify:
   - Visual rollers rotate
   - Step count updates
   - Distance from home calculates correctly
   - "Aligned at pulse boundary" appears when aligned

### Test with Motor Controller:

1. Connect to your hardware
2. Use calibration dialog
3. Verify motor responds to drag gestures
4. Check that "Mark as Home" aligns pump correctly

## Troubleshooting

### Wheel not responding to drags:
- Check that `pointerInput` modifier is applied
- Verify `onOffsetChange` callback is connected

### Steps calculation seems wrong:
- Verify motor specs in `MotorSpecs`
- Check gear reduction ratio
- Confirm roller count

### Motor moves opposite direction:
- Invert step sign in `jogPump()` handler
- Or adjust in your G-code generator

### Pump doesn't stay aligned:
- Check for mechanical slippage
- Verify motor driver is holding position
- Consider adding detent at home position

## Future Enhancements

Potential improvements:
- Add haptic feedback when aligned
- Animate rotation smoothly
- Add sound effects for engagement
- Store home positions persistently
- Auto-home on startup sequence
- Add multiple home positions per pump

## Support

For questions or issues:
1. Check that motor specs match your hardware
2. Verify G-code commands reach motor controller
3. Test with `PulseHomeTestScreen` in isolation
4. Review step calculations with `PulseModeCalculator`
