# ✅ Tube Diameter Feature Added!

## 🎯 What Changed

The calibrator now **intelligently skips Step 1 (Visual Homing)** for pumps with **small tubes (≤3mm inner diameter)**.

---

## 🔬 **Why This Matters**

### Small Tubes (≤3mm ID)
- Tubing is **stiff and tight-fitting**
- Roller position is **consistent** without visual homing
- Less tube compression = less variability
- **Skip straight to priming!**

### Large Tubes (>3mm ID)  
- More flexible, can shift position
- Need visual homing to align rollers
- Full 4-step calibration process
- Ensures accuracy

---

## 📐 **How It Works**

### Conditional Logic
```kotlin
// Checks tube diameter from pump config
val needsVisualHoming = (pump.tubeInnerDiameterMm ?: 4f) > 3f

// Starts at correct step
val startingStep = if (needsVisualHoming) 1 else 2
```

### What User Sees

#### For Large Tubes (>3mm)
```
┌────────────────────────────────┐
│ Motor Configuration:            │
│ 1.8° motor, 1:4 gear, 3 rollers │
│ = 267 steps/pulse              │
│ ────────────────────────────── │
│ Tube: 4.5 mm ID                │
└────────────────────────────────┘

●───○───○───○
Home Prime Dispense Measure

Step 1: Visual Homing
[scroll wheel shown]
```

#### For Small Tubes (≤3mm)
```
┌────────────────────────────────┐
│ Motor Configuration:            │
│ 1.8° motor, 1:4 gear, 3 rollers │
│ = 267 steps/pulse              │
│ ────────────────────────────── │
│ Tube: 2.5 mm ID                │
│ ℹ️ Small tubes (≤3mm) don't    │
│    need visual homing           │
└────────────────────────────────┘

●───○───○
Prime Dispense Measure

Step 1: Prime to Home
ℹ️ Small tubes (≤3mm ID) don't need 
   visual homing - roller position is 
   consistent.

[No back button shown]
```

---

## 🎨 **Visual Changes**

### Stepper UI
- **Large tubes**: 4 steps (Home → Prime → Dispense → Measure)
- **Small tubes**: 3 steps (Prime → Dispense → Measure)
- Step numbers adjust automatically

### Info Cards
- Shows tube diameter at top
- Explains why Step 1 is skipped (for small tubes)
- Clear messaging in Step 2 for small tubes

### Navigation
- Back button hidden in Step 2 for small tubes (since there's no Step 1 to go back to)
- Restart button resets to correct starting step

---

## 📝 **Required: Add Field to PumpConfig**

```kotlin
data class PumpConfig(
    // ... existing fields ...
    
    // ADD THIS FIELD:
    val tubeInnerDiameterMm: Float? = null,  // Inner diameter in millimeters
    
    // OPTIONAL (from previous polish):
    val motorStepAngle: Float? = null,       // Default 1.8°
    val gearReduction: Float? = null,        // Default 4.0
    val numRollers: Int? = null              // Default 3
)
```

### Default Behavior
- If `tubeInnerDiameterMm` is **not set** (null), defaults to **4mm**
- **4mm > 3mm** → Shows visual homing (safe default)
- User can set actual tube diameter in pump settings

---

## 🔧 **Examples**

### Common Tube Sizes

| Tube ID | Needs Visual Homing? | Steps Shown |
|---------|---------------------|-------------|
| 1.6mm   | ❌ No               | 3 steps     |
| 2.4mm   | ❌ No               | 3 steps     |
| 3.0mm   | ❌ No (threshold)   | 3 steps     |
| 3.2mm   | ✅ Yes              | 4 steps     |
| 4.8mm   | ✅ Yes              | 4 steps     |
| 6.4mm   | ✅ Yes              | 4 steps     |

### In Practice

```kotlin
// Small tube pump - skips visual homing
val cyanPump = PumpConfig(
    name = "Cyan",
    // ... other fields ...
    tubeInnerDiameterMm = 2.4f  // Small tube
)
// Calibration starts at Step 2 (Prime)

// Large tube pump - shows visual homing  
val yellowPump = PumpConfig(
    name = "Yellow",
    // ... other fields ...
    tubeInnerDiameterMm = 4.8f  // Large tube
)
// Calibration starts at Step 1 (Home)
```

---

## 🧪 **Testing**

### Test Case 1: Small Tube
1. Set `tubeInnerDiameterMm = 2.0f`
2. Open calibration dialog
3. ✅ Should show "Tube: 2.0 mm ID"
4. ✅ Should show info message about skipping
5. ✅ Should start at Prime step (Step 1)
6. ✅ Stepper shows: ●──○──○ (3 steps)
7. ✅ No back button in first step

### Test Case 2: Large Tube
1. Set `tubeInnerDiameterMm = 4.5f`
2. Open calibration dialog
3. ✅ Should show "Tube: 4.5 mm ID"
4. ✅ Should NOT show skip message
5. ✅ Should start at Home step (Step 1)
6. ✅ Stepper shows: ●──○──○──○ (4 steps)
7. ✅ Back button appears in Step 2

### Test Case 3: Default (no tube specified)
1. Don't set `tubeInnerDiameterMm` (null)
2. Open calibration dialog
3. ✅ Should default to 4.0mm
4. ✅ Should show full 4-step process (safe default)

---

## 💡 **User Benefits**

### Faster Calibration
- Small tube users save time (skip Step 1)
- No confusing scroll wheel for simple pumps
- Straight to priming

### Better UX
- System is smart about what's needed
- Clear explanation of why steps are skipped
- No manual "skip this step" confusion

### Accurate Guidance
- Only shows visual homing when actually needed
- Prevents unnecessary complexity
- Reduces user errors

---

## 🎯 **Technical Details**

### Step Renumbering
- Internal step numbers stay the same (1, 2, 3, 4)
- Visual display adjusts:
  - Large tubes: "Step 2" shows as "Step 2"
  - Small tubes: "Step 2" shows as "Step 1"
- Stepper UI handles offset automatically

### State Management
```kotlin
// Starting step
val startingStep = if (needsVisualHoming) 1 else 2

// Restart button
currentStep = startingStep  // Resets to correct step

// Back button in Step 2
if (needsVisualHoming) currentStep = 1  // Only if Step 1 exists
```

---

## 📚 **Documentation**

### Help Section Update
The help section could mention:

> 💡 **Tube Size Matters:**
> - Small tubes (≤3mm): Consistent roller position, no homing needed
> - Large tubes (>3mm): Flexible, needs visual alignment

---

## ✅ **Summary**

| Feature | Status |
|---------|--------|
| Auto-detects tube size | ✅ Done |
| Skips Step 1 for small tubes | ✅ Done |
| Shows 3-step vs 4-step UI | ✅ Done |
| Displays tube diameter | ✅ Done |
| Explains skip reason | ✅ Done |
| Adjusts step numbers | ✅ Done |
| Hides back button when needed | ✅ Done |
| Restart to correct step | ✅ Done |

---

## 🚀 **Ship It!**

Your calibrator now:
1. ✅ Intelligently adapts to tube size
2. ✅ Shows 3 or 4 steps as appropriate
3. ✅ Explains why steps are skipped
4. ✅ Saves time for small tube users
5. ✅ Maintains accuracy for large tube users

**Add the `tubeInnerDiameterMm` field to PumpConfig and you're done!** 🎉

---

## 📐 **Recommended Tube Diameter Input**

Consider adding to pump settings UI:

```kotlin
OutlinedTextField(
    value = tubeInnerDiameterMm.toString(),
    onValueChange = { /* update */ },
    label = { Text("Tube Inner Diameter") },
    suffix = { Text("mm") },
    supportingText = { 
        Text("≤3mm: Skip visual homing, >3mm: Full calibration") 
    }
)
```

This helps users set it correctly!
