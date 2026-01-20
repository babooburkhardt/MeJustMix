# Quick Start Guide - Pulse Mode Scroll Wheel

## 🚀 5-Minute Integration

### Step 1: Copy These Functions to Your ViewModel (2 minutes)

Open `SettingsViewModel.kt` and add:

```kotlin
import com.example.mejustmix.utils.PulseModeCalculator

fun jogPump(pumpIndex: Int, steps: Int) {
    val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
    
    // TODO: Replace with your actual G-code sender
    sendGCodeToController("G0 ${pump.axis}$steps")
    
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = pump.pulseHomeOffset + steps
        )
        state.copy(pumps = updatedPumps)
    }
}

fun markPumpAsHomed(pumpIndex: Int) {
    _uiState.update { state ->
        val pump = state.pumps.getOrNull(pumpIndex) ?: return@update state
        val nearestHome = PulseModeCalculator.snapToPulseBoundary(
            pump.pulseHomeOffset,
            pump.stepsPerPulse
        )
        val updatedPumps = state.pumps.toMutableList()
        updatedPumps[pumpIndex] = pump.copy(pulseHomeOffset = nearestHome)
        state.copy(pumps = updatedPumps)
    }
}
```

### Step 2: Connect Your Calibration Dialog (2 minutes)

Wherever you call `PulseCalibrationDialog`, add these callbacks:

```kotlin
PulseCalibrationDialog(
    pump = selectedPump,
    pumpIndex = selectedIndex,
    onJogPump = { steps, _ -> viewModel.jogPump(selectedIndex, steps) },
    onMarkHome = { viewModel.markPumpAsHomed(selectedIndex) },
    // ... your existing callbacks
)
```

### Step 3: Test! (1 minute)

1. Launch app
2. Open calibration for any pump
3. Get to Step 2 (Visual Homing)
4. You should see the scroll wheel
5. Try dragging it - you should see the visual rollers rotate

**That's it!** The scroll wheel is now integrated.

---

## 📋 What You Get

✅ **Drag-to-rotate interface** - Intuitive touch control  
✅ **Real-time feedback** - See step count and distance from home  
✅ **Auto-calculated steps** - 266.67 steps per pulse (from your motor specs)  
✅ **Snap-to-home button** - Automatically align to pulse boundary  
✅ **Per-pump independence** - Each pump homes separately  

---

## 🔧 Motor Specs (Already Configured)

Your system:
- **Stepper**: 1.8° (200 steps/rev)
- **Gear Reduction**: 1:4
- **Pump**: 3 rollers
- **Steps per Pulse**: ~267 steps (auto-calculated)

**Different specs?** Update in `PulseModeCalculator.kt` → `MotorSpecs`

---

## 📝 Complete Example

Here's a full example of using the scroll wheel in your calibration flow:

```kotlin
// In your settings screen or wherever you trigger calibration
var showCalibrationDialog by remember { mutableStateOf(false) }
var selectedPumpIndex by remember { mutableIntStateOf(0) }

Button(onClick = { 
    selectedPumpIndex = 0  // Cyan pump
    showCalibrationDialog = true 
}) {
    Text("Calibrate Cyan Pump")
}

if (showCalibrationDialog) {
    val pump = uiState.pumps[selectedPumpIndex]
    
    PulseCalibrationDialog(
        pump = pump,
        pumpIndex = selectedPumpIndex,
        onDismiss = { showCalibrationDialog = false },
        onSave = { stepsPerPulse, mlPerPulse ->
            // Save calibration
            viewModel.updatePumpCalibration(
                selectedPumpIndex, 
                stepsPerPulse, 
                mlPerPulse
            )
            showCalibrationDialog = false
        },
        onDispensePulses = { pulseCount, stepsPerPulse ->
            // Dispense test pulses
            viewModel.dispensePulses(
                selectedPumpIndex, 
                pulseCount, 
                stepsPerPulse
            )
        },
        onJogPump = { steps, stepsPerPulse ->
            // Jog for homing
            viewModel.jogPump(selectedPumpIndex, steps)
        },
        onMarkHome = {
            // Mark as homed
            viewModel.markPumpAsHomed(selectedPumpIndex)
        }
    )
}
```

---

## 🧪 Testing Without Hardware

Want to test the UI before connecting hardware?

1. Comment out the G-code sending in `jogPump()`
2. Just keep the state update
3. The UI will work perfectly
4. You can test drag gestures, snap-to-home, etc.

```kotlin
fun jogPump(pumpIndex: Int, steps: Int) {
    val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
    
    // TODO: Uncomment when hardware is ready
    // sendGCodeToController("G0 ${pump.axis}$steps")
    
    // This part works for UI testing
    _uiState.update { state ->
        val updatedPumps = state.pumps.toMutableList()
        updatedPumps[pumpIndex] = pump.copy(
            pulseHomeOffset = pump.pulseHomeOffset + steps
        )
        state.copy(pumps = updatedPumps)
    }
}
```

---

## 🎯 User Workflow

**What your users will do:**

1. **Open calibration** → Select pump to calibrate
2. **Step 1** → System shows steps per pulse (~267)
3. **Step 2 - HOME THE PUMP** ⭐
   - User sees visual 3-roller wheel
   - Drags finger to rotate
   - Aligns roller just past compression point
   - Clicks "Snap to Home" to auto-align
   - Clicks "Mark as Home" when aligned
4. **Step 3** → Dispense test pulses
5. **Step 4** → Measure output and save

---

## ⚠️ Common Gotchas

### Motor moves backwards?
**Fix**: Invert step sign in `jogPump()`:
```kotlin
sendGCodeToController("G0 ${pump.axis}${-steps}")  // Note the minus
```

### Can't see scroll wheel?
**Check**: 
- Did you add the import for `CompactPulseHomeWheel`?
- Is it showing up in Step 2 of calibration?

### Steps don't match your motor?
**Fix**: Update motor specs in `PulseModeCalculator.kt`:
```kotlin
object MotorSpecs {
    const val STEP_ANGLE_DEGREES = 0.9f   // If using 0.9° motor
    const val GEAR_REDUCTION = 5f          // If using 1:5 gearing
    const val ROLLER_COUNT = 2             // If using 2-roller pump
}
```

---

## 📚 Need More Detail?

- **Full integration guide**: `PULSE_MODE_INTEGRATION.md`
- **Math and formulas**: `PULSE_MODE_MATH.md`
- **Visual diagrams**: `VISUAL_DIAGRAMS.md`
- **Complete summary**: `IMPLEMENTATION_SUMMARY.md`
- **ViewModel code**: `VIEWMODEL_ADDITIONS.kt`

---

## 🎉 You're Done!

The scroll wheel should now be working. Users can:
- ✅ Drag to rotate pump
- ✅ See real-time step feedback
- ✅ Snap to pulse boundaries
- ✅ Mark home positions

**Next**: Connect your G-code sender and test with actual hardware!

---

## 🆘 Stuck?

1. **UI not showing?** → Check imports and callback connections
2. **Motor not responding?** → Verify G-code format for your controller
3. **Math seems wrong?** → Check motor specs match your hardware
4. **Need help?** → Review the documentation files above

**Remember**: The only code you MUST write is the G-code sending function. Everything else is already implemented and ready to use!
