# Pulse Mode Mathematics - Quick Reference

## Motor & Pump Specifications

```
Stepper Motor: 1.8° step angle
Gear Reduction: 1:4 (motor:pump)
Pump Type: 3-roller peristaltic
```

## Core Calculations

### 1. Steps per Motor Revolution
```
Steps/Motor Rev = 360° ÷ Step Angle
                = 360° ÷ 1.8°
                = 200 steps
```

### 2. Steps per Pump Revolution
```
Steps/Pump Rev = Steps/Motor Rev × Gear Reduction
               = 200 steps × 4
               = 800 steps
```

### 3. Steps per Pulse (One Roller Rotation)
```
Steps/Pulse = Steps/Pump Rev ÷ Roller Count
            = 800 steps ÷ 3
            ≈ 266.67 steps
```

## Volume Calculations

### 4. Pulses Needed for Target Volume
```
Pulses = Target Volume (mL) ÷ mL per Pulse
       = Round to nearest integer
```

### 5. Steps Needed for Target Volume
```
Steps = Pulses × Steps per Pulse
```

### 6. Actual Dispensed Volume
```
Actual mL = Pulses (rounded) × mL per Pulse
```

### 7. Dispensing Error
```
Error (mL) = |Actual mL - Target mL|
Error (%) = (Error mL ÷ Target mL) × 100%
```

## Home Position Calculations

### 8. Distance from Pulse Boundary
```
Remainder = Current Steps % Steps per Pulse

If Remainder > (Steps per Pulse / 2):
    Distance = Remainder - Steps per Pulse  (negative, before boundary)
Else:
    Distance = Remainder  (positive, after boundary)
```

### 9. Nearest Home Position
```
Pulses = Round(Current Steps ÷ Steps per Pulse)
Nearest Home = Pulses × Steps per Pulse
```

### 10. Steps to Reach Home
```
Steps to Home = Nearest Home - Current Steps
```

### 11. Is At Home?
```
At Home = |Distance from Boundary| < Tolerance
        (typically Tolerance = 1 step)
```

## Calibration Math

### 12. Calculate mL per Pulse
```
Test: Dispense N pulses
Measure: M milliliters collected

mL per Pulse = M ÷ N
```

### 13. Minimum Dispensable Volume
```
Min Volume = mL per Pulse × Minimum Pulse Count
           (e.g., if min = 1 pulse, min vol = 0.5 mL)
```

## Example Scenarios

### Example 1: Dispense 5 mL
```
Given:
- mL per Pulse = 0.5 mL
- Steps per Pulse = 266.67

Calculate:
1. Pulses = 5 mL ÷ 0.5 mL/pulse = 10 pulses
2. Steps = 10 × 266.67 = 2667 steps
3. Actual = 10 × 0.5 = 5.0 mL (exact match!)
4. Error = 0%
```

### Example 2: Dispense 5.2 mL
```
Given:
- mL per Pulse = 0.5 mL
- Steps per Pulse = 266.67

Calculate:
1. Pulses = 5.2 mL ÷ 0.5 mL/pulse = 10.4 → 10 pulses (rounded)
2. Steps = 10 × 266.67 = 2667 steps
3. Actual = 10 × 0.5 = 5.0 mL
4. Error = |5.0 - 5.2| = 0.2 mL = 3.8% error
```

### Example 3: Home Position Check
```
Given:
- Current Position = 750 steps
- Steps per Pulse = 266.67

Calculate:
1. Remainder = 750 % 266.67 = 216.66 steps
2. Half Pulse = 266.67 / 2 = 133.34
3. Since 216.66 > 133.34:
   Distance = 216.66 - 266.67 = -50.01 steps
4. Nearest Home = Round(750 / 266.67) × 266.67
                = 3 × 266.67 = 800 steps
5. Steps to Home = 800 - 750 = 50 steps forward
6. At Home? |50| < 1? NO - need to move 50 steps
```

### Example 4: After Homing
```
Given:
- Current Position = 800 steps (after homing)
- Steps per Pulse = 266.67

Calculate:
1. Remainder = 800 % 266.67 = 0 steps
2. Distance = 0 steps
3. At Home? |0| < 1? YES - perfectly aligned!
```

## Rotation Angle Relationships

### Pump Angle from Steps
```
Pump Angle = (Steps % Steps per Pump Rev) × (360° / Steps per Pump Rev)
           = (Steps % 800) × (360° / 800)
           = (Steps % 800) × 0.45°
```

### Visual Wheel Angle (for UI)
```
Wheel Angle = (Total Steps / Steps per Pulse) × 360°
            = (Total Steps / 266.67) × 360°

This represents the rotation of the 3-roller assembly.
```

### Motor Angle from Steps
```
Motor Angle = Steps × Step Angle
            = Steps × 1.8°
```

## Accuracy Considerations

### Maximum Error per Component
```
Max Error = ± (mL per Pulse / 2)
          = ± 0.25 mL (if mL per pulse = 0.5)
```

### Total Mix Error (N components)
```
Worst Case = N × Max Error per Component
Best Case  = 0 (all errors cancel)
Typical    = √N × Max Error per Component
```

### Example: 3-Color Mix (C=2mL, M=3mL, Y=1mL)
```
Each at 0.5 mL/pulse:
- Cyan: 2.0 ÷ 0.5 = 4 pulses → 2.0 mL (exact)
- Magenta: 3.0 ÷ 0.5 = 6 pulses → 3.0 mL (exact)
- Yellow: 1.0 ÷ 0.5 = 2 pulses → 1.0 mL (exact)
Total: 6.0 mL (exact!) - Error: 0%

But if Yellow was 1.2 mL requested:
- Yellow: 1.2 ÷ 0.5 = 2.4 → 2 pulses → 1.0 mL
- Error: -0.2 mL or -16.7% for yellow component
```

## Code Implementation

### Step Calculation Function
```kotlin
fun stepsForVolume(targetMl: Float, mlPerPulse: Float, stepsPerPulse: Float): Int {
    val pulses = (targetMl / mlPerPulse).roundToInt()
    return (pulses * stepsPerPulse).roundToInt()
}
```

### Home Check Function
```kotlin
fun isAtHome(currentSteps: Float, stepsPerPulse: Float, tolerance: Float = 1f): Boolean {
    val remainder = currentSteps % stepsPerPulse
    val distance = if (remainder > stepsPerPulse / 2) {
        remainder - stepsPerPulse
    } else {
        remainder
    }
    return abs(distance) < tolerance
}
```

### Snap to Home Function
```kotlin
fun snapToHome(currentSteps: Float, stepsPerPulse: Float): Int {
    val pulses = (currentSteps / stepsPerPulse).roundToInt()
    return (pulses * stepsPerPulse).roundToInt()
}
```

## Physical Considerations

### Mechanical Accuracy
- Stepper accuracy: ±0.05° (typical)
- Gear backlash: ~0.5-2° (depends on gear quality)
- Pump elasticity: Can cause ~1-5% volume variation

### Recommended Practices
1. **Always home before dispensing** - Ensures consistent starting position
2. **Use minimum pulse counts** - Reduces cumulative error (e.g., min 2-3 pulses)
3. **Calibrate regularly** - Tube wear affects mL/pulse
4. **Account for temperature** - Viscosity changes affect flow

### Pulse Minimum Guidelines
```
For 5% accuracy:  Minimum 2 pulses
For 2% accuracy:  Minimum 5 pulses
For 1% accuracy:  Minimum 10 pulses

Example with 0.5 mL/pulse:
- 5% accuracy: Minimum 1 mL (2 pulses)
- 2% accuracy: Minimum 2.5 mL (5 pulses)
- 1% accuracy: Minimum 5 mL (10 pulses)
```

## Summary Formula Sheet

| What You Need | Formula |
|---------------|---------|
| Steps per pulse | `(360 / step_angle) × gear_reduction / roller_count` |
| Pulses for volume | `round(target_mL / mL_per_pulse)` |
| Steps for volume | `pulses × steps_per_pulse` |
| Actual volume | `pulses × mL_per_pulse` |
| Is at home? | `abs(steps % steps_per_pulse) < tolerance` |
| Steps to home | `round(steps / steps_per_pulse) × steps_per_pulse - steps` |
| Max error | `mL_per_pulse / 2` |
| mL per pulse | `measured_mL / test_pulses` |
