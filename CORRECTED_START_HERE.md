# 🚀 CORRECTED - START HERE

## ✅ What Changed

### Before (Wrong):
- Scroll wheel sent motor commands
- User entered steps per pulse
- Confusing jog/mark home workflow

### Now (Correct):
- **Scroll wheel tracks visually** (no motor movement)
- **Steps per pulse auto-calculated** (~267 steps)
- **Prime button moves pump** to tracked position
- **10+ pulse test** for better measurement accuracy

---

## ⚡ 3-Step Quick Start

### 1. Build Project
```bash
./gradlew build
```
Should compile successfully now.

### 2. Add Calibration Button
In your Settings/Calibration screen:

```kotlin
var showPulseCalibration by remember { mutableStateOf(false) }

Button(onClick = { showPulseCalibration = true }) {
    Text("Calibrate Pulse Mode")
}

if (showPulseCalibration) {
    PulseCalibrationDialog(
        pump = pump,
        pumpIndex = pumpIndex,
        onDismiss = { showPulseCalibration = false },
        onSave = { mlPerPulse ->
            viewModel.updatePumpMlPerPulse(pumpIndex, mlPerPulse)
            showPulseCalibration = false
        },
        onDispensePulses = { count ->
            viewModel.dispensePulsesForCalibration(pumpIndex, count)
        },
        onPrimeToPulseHome = {
            viewModel.primePumpToHome(pumpIndex)
        }
    )
}
```

### 3. Implement G-Code Sender
In `SettingsViewModel.kt`, uncomment TODOs and add your motor communication.

---

## 📖 Corrected Workflow

```
1. Drag scroll wheel → Tracks offset (NO motor movement)
                ↓
2. Click "Prime to Home" → Motor moves to home (G-code sent)
                ↓
3. Click "Dispense 10 Pulses" → Motor dispenses (G-code sent)
                ↓
4. Measure output → Calculate mL/pulse → Save
```

---

## 🎯 Key Points

| Feature | How It Works |
|---------|--------------|
| **Scroll Wheel** | Visual only - tracks where home should be |
| **Steps/Pulse** | Auto-calculated: ~267 (1.8° motor, 1:4 gear, 3 rollers) |
| **Prime Button** | Actually moves motor to home position |
| **Test Volume** | 10+ pulses = larger volume = better accuracy |
| **Saves** | Only mL/pulse (steps/pulse is known) |

---

## 🔧 ViewModel Functions

```kotlin
// Scroll wheel calls this automatically (no motor command)
viewModel.updatePumpTrackedOffset(pumpIndex, offsetSteps)

// Prime button sends G-code to move pump
viewModel.primePumpToHome(pumpIndex)

// Dispense button sends G-code
viewModel.dispensePulsesForCalibration(pumpIndex, pulseCount)

// Save button stores mL/pulse
viewModel.updatePumpMlPerPulse(pumpIndex, mlPerPulse)
```

---

## 📐 The Math (Automatic)

```
Motor: 1.8° = 200 steps/rev
Gear:  1:4  = 800 steps/pump rev
Pump:  3 rollers = 267 steps/pulse ← Auto-calculated

Test: 10 pulses * 267 = 2670 steps
Measure: 5.0 mL
Result: 5.0 / 10 = 0.5 mL/pulse ← You only calibrate this!
```

---

## ✅ Testing

1. **UI Test** (no motor):
   - Open dialog
   - Drag scroll wheel
   - Verify offset tracked

2. **Motor Test**:
   - Click "Prime to Home"
   - Verify motor moves
   - Click "Dispense 10 Pulses"
   - Measure output

3. **Calibration Test**:
   - Enter measurement
   - Verify calculation
   - Save and verify persists

---

## 📂 Files Changed

1. ✅ `PulseCalibrationDialog.kt` - Rewritten with corrected workflow
2. ✅ `SettingsViewModel.kt` - Updated functions
3. ✅ `PulseCalibrationDialogUsageExample.kt` - Updated examples
4. ✅ `CORRECTED_IMPLEMENTATION.md` - Full explanation

---

## 🆘 Need Help?

- **Full explanation**: `CORRECTED_IMPLEMENTATION.md`
- **Usage examples**: `PulseCalibrationDialogUsageExample.kt`
- **Math details**: `PULSE_MODE_MATH.md`

---

## 🎉 Summary

✅ Scroll wheel = visual tracking only  
✅ Prime button = actual motor movement  
✅ Steps/pulse = auto-calculated  
✅ Only calibrate mL/pulse  
✅ 10+ pulse test = better accuracy  

**Build it and try it!**
