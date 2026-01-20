# ✅ CORRECTED PULSE MODE IMPLEMENTATION

## 🎯 Key Changes Made

### What Was Wrong Before:
- ❌ Scroll wheel sent G-code commands on every drag
- ❌ User manually entered steps per pulse
- ❌ Confusing workflow with "jog" and "mark home" buttons

### What's Correct Now:
- ✅ Scroll wheel just **tracks offset visually** (no motor commands)
- ✅ Steps per pulse is **known from motor specs** (~267 steps)
- ✅ **Prime button** actually moves pump to tracked position
- ✅ System **remembers offset** and applies during real dispensing
- ✅ **Larger test volume** (10+ pulses) reduces measurement error

---

## 📋 Corrected Workflow

### Step 1: Visual Homing
**User Action**: Drag scroll wheel to track where home should be  
**What Happens**: `pump.pulseHomeOffset` is updated (no motor movement)  
**Why**: User visually aligns with physical pump position

### Step 2: Prime to Home  
**User Action**: Click "Prime to Home" button  
**What Happens**: Motor moves `pulseHomeOffset % stepsPerPulse` steps  
**Result**: Pump is now physically at home, `pulseHomeOffset = 0`

### Step 3: Dispense Test Pulses
**User Action**: Click "Dispense 10 Pulses"  
**What Happens**: Motor moves `10 * 267 = 2670` steps  
**Result**: Pump dispenses, `pulseHomeOffset` updated with remainder

### Step 4: Measure & Calculate
**User Action**: Enter measured volume (e.g., 5.0 mL)  
**What Happens**: `mlPerPulse = 5.0 / 10 = 0.5 mL/pulse`  
**Result**: Calibration saved, ready for pulse mode dispensing

---

## 🔧 ViewModel Functions (Updated)

### 1. `updatePumpTrackedOffset(pumpIndex, offsetSteps)`
**Called by**: Scroll wheel (automatically)  
**Does**: Updates `pump.pulseHomeOffset` visually  
**Motor**: NO - just tracks position

### 2. `primePumpToHome(pumpIndex)`
**Called by**: "Prime to Home" button  
**Does**: Sends G-code to move pump to home  
**Motor**: YES - `G0 {axis}{steps}`  
**After**: Sets `pulseHomeOffset = 0`

### 3. `dispensePulsesForCalibration(pumpIndex, pulseCount)`
**Called by**: "Dispense N Pulses" button  
**Does**: Sends G-code to dispense test volume  
**Motor**: YES - `G0 {axis}{pulseCount * 267}`  
**After**: Updates `pulseHomeOffset` with remainder

### 4. `updatePumpMlPerPulse(pumpIndex, mlPerPulse)`
**Called by**: "Save Calibration" button  
**Does**: Saves measured mL per pulse  
**Motor**: NO - just saves value

---

## 📐 Motor Math (Automatic)

### Known Values:
```
Step Angle: 1.8° → 200 steps/motor revolution
Gear Reduction: 1:4 → 800 steps/pump revolution
Rollers: 3 → 800/3 = 266.67 ≈ 267 steps/pulse
```

### During Calibration:
```
User tracks offset: 750 steps (visual)
Prime to home: 750 % 267 = 216 steps → Motor moves 216 steps
Now at home: pulseHomeOffset = 0

Dispense 10 pulses: 10 * 267 = 2670 steps → Motor moves 2670 steps
After dispense: pulseHomeOffset = 2670 % 267 = 0 (back at home!)

User measures: 5.0 mL
Calculate: 5.0 / 10 = 0.5 mL/pulse → SAVED
```

### During Normal Use:
```
User wants 2mL cyan:
  2mL / 0.5mL/pulse = 4 pulses
  4 pulses * 267 = 1068 steps
  
Before dispensing:
  Check: pulseHomeOffset = 0? Yes, at home
  Dispense: G0 X1068
  After: pulseHomeOffset = 1068 % 267 = 0

Next time: Still at home, ready for next dispense!
```

---

## 🎨 UI Flow

### Calibration Dialog:
```
┌─────────────────────────────────────┐
│ Step 1: Visual Homing                │
│                                      │
│  [Scroll Wheel - drag to track]     │
│  Current offset: 750 steps           │
│  [Next: Prime to Home]               │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 2: Prime to Home                │
│                                      │
│  Will move: 216 steps                │
│  [Prime Pump to Home (216 steps)]   │
│  ✓ Primed to Home Position           │
│  [Next: Test Dispense]               │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 3: Test Dispense                │
│                                      │
│  Pulse count: [5][10][20][50]        │
│  [Dispense 10 Pulses]                │
│  ✓ Dispensed!                        │
│  [Next: Measure]                     │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 4: Measure                      │
│                                      │
│  Measured: [5.0] mL                  │
│  Result: 0.500 mL/pulse              │
│  [Save Calibration]                  │
└─────────────────────────────────────┘
```

---

## 💡 Why This Is Better

### Reduces Measurement Error:
- ✅ **10 pulses** dispenses larger volume (~5mL vs ~0.5mL)
- ✅ Easier to measure accurately with syringe
- ✅ Dividing by 10 averages out any single-pulse variation
- ✅ Error per pulse: ±0.05mL on 5mL = ±0.005mL/pulse (vs ±0.05mL/pulse on 0.5mL)

### Simpler User Experience:
- ✅ No manual entry of steps per pulse (it's calculated)
- ✅ Scroll wheel doesn't cause unwanted motor movement
- ✅ Clear "prime" button to actually move pump
- ✅ System remembers where pump is for next time

### Better System Design:
- ✅ Scroll wheel is just visual UI (no motor control)
- ✅ Motor commands only on button clicks
- ✅ Offset tracked persistently across uses
- ✅ Automatic application during normal dispensing

---

## 📝 Integration Example

```kotlin
// In your settings/calibration screen:
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
            // Only mL/pulse, steps/pulse is auto-calculated
            viewModel.updatePumpMlPerPulse(pumpIndex, mlPerPulse)
            showPulseCalibration = false
        },
        onDispensePulses = { pulseCount ->
            // Dispense test pulses for calibration
            viewModel.dispensePulsesForCalibration(pumpIndex, pulseCount)
        },
        onPrimeToPulseHome = {
            // Actually move pump to tracked home
            viewModel.primePumpToHome(pumpIndex)
        }
    )
}
```

---

## 🔧 G-Code Implementation

In `SettingsViewModel.kt`, you need to implement:

```kotlin
private fun sendGCodeToController(gcode: String) {
    // YOUR IMPLEMENTATION
    // Examples:
    // - HTTP POST to ESP32
    // - WebSocket message
    // - Serial port write
}
```

Then uncomment the TODOs in:
1. `primePumpToHome()` - Sends prime command
2. `dispensePulsesForCalibration()` - Sends dispense command

---

## ✅ Testing Checklist

- [ ] Build project (should compile cleanly)
- [ ] Open calibration dialog
- [ ] Step 1: Drag scroll wheel (motor shouldn't move)
- [ ] Step 1: Verify offset tracked correctly
- [ ] Step 2: Click "Prime to Home" (motor should move)
- [ ] Step 2: Verify pump at home position
- [ ] Step 3: Click "Dispense 10 Pulses" (motor should move)
- [ ] Step 3: Measure output volume
- [ ] Step 4: Enter measurement, verify calculation
- [ ] Step 4: Save calibration
- [ ] Verify mL/pulse saved correctly

---

## 📊 Files Modified

1. ✅ **PulseCalibrationDialog.kt** - Complete rewrite with 4 corrected steps
2. ✅ **SettingsViewModel.kt** - Updated functions for corrected workflow
3. ✅ **PulseCalibrationDialogUsageExample.kt** - Updated examples
4. ✅ **This document** - Explains corrected implementation

---

## 🎉 You're Ready!

**The corrected implementation:**
- Tracks offset visually without moving motor
- Has clear prime button to actually move pump
- Auto-calculates steps per pulse (~267)
- Only calibrates mL per pulse
- Uses larger test volume for better accuracy
- Remembers offset for consistent dispensing

**Next step:** Build and test!
