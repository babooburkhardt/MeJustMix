# ✅ Pigment Strength Calibration Restored!

## What Changed

Added **smart calibration routing** based on Kubelka-Munk setting:

### KM Theory Disabled → Simple Pigment Strength
- Visual matching interface
- Single strength number per pigment (e.g., 1.2)
- "Is my paint lighter or darker than expected?"
- Quick and easy for everyone

### KM Theory Enabled → Advanced K/S Calibration
- Camera-based scanning
- Full spectral profile (31 wavelengths)
- More accurate but more complex
- For advanced users

---

## Implementation Details

### Files Modified
`SettingsModal.kt`

### Changes Made

**1. Added Pigment Tuner State Variables**
```kotlin
var showPigmentTunerForColor by rememberSaveable { mutableStateOf<String?>(null) }
var showPigmentTuner by rememberSaveable { mutableStateOf(false) }
```

**2. Added Pigment Tuner Dialog Invocations**
```kotlin
if (showPigmentTunerForColor != null) {
    PigmentTunerDialog(
        onDismissRequest = { showPigmentTunerForColor = null },
        mixViewModel = mixViewModel,
        lockedColor = showPigmentTunerForColor,
        settingsViewModel = settingsViewModel
    )
}
```

**3. Updated Calibration Chooser Logic**
```kotlin
if (isPigment) {
    if (uiState.useKubelkaMunk) {
        // Show K/S Calibration (Advanced)
        Button(...) {
            Text("K/S Calibration (Advanced)")
            Text("Scan pigment to set spectral values")
        }
    } else {
        // Show Pigment Strength (Simple)
        Button(...) {
            Text("Pigment Strength (Simple)")
            Text("Visual matching calibration")
        }
    }
}
```

---

## User Experience

### Calibration Dialog Options

**When KM is DISABLED:**
```
┌─────────────────────────────────┐
│ Calibrate Cyan                  │
├─────────────────────────────────┤
│ What would you like to          │
│ calibrate?                      │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Flow Rate                   │ │
│ │ Adjust Steps/mL accuracy    │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Pigment Strength (Simple)   │ │
│ │ Visual matching calibration │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

**When KM is ENABLED:**
```
┌─────────────────────────────────┐
│ Calibrate Cyan                  │
├─────────────────────────────────┤
│ What would you like to          │
│ calibrate?                      │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ Flow Rate                   │ │
│ │ Adjust Steps/mL accuracy    │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ K/S Calibration (Advanced)  │ │
│ │ Scan pigment set spectral   │ │
│ │ values                      │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

---

## Pigment Strength Dialog Features

### What It Does
1. **Dispense Test Dot** - Creates 5mL sample
2. **Visual Comparison** - Shows expected vs actual color
3. **Slider Adjustment** - "Lighter" ← → "Darker"
4. **Auto-Calculate** - Determines new strength value
5. **Save** - Updates pigment strength

### How It Works
```
Current Strength: 1.00
↓
User slides to "Lighter" (0.8)
↓
New Strength: 1.25
(Need to dispense more to get desired color)
```

### For Each Pigment
- **Cyan, Magenta, Yellow, Black**: Match process colors
- **White**: Uses gray test (easier to judge)

### Visual Interface
```
Expected    VS    Actual
   ●               ●
(Target)      (Adjustable)

        Slider
Lighter ←──●──→ Darker
```

---

## Benefits

### For Beginners (KM Disabled)
✅ Simple visual matching
✅ No camera required
✅ No spectral knowledge needed
✅ Quick calibration
✅ Still improves accuracy

### For Advanced Users (KM Enabled)
✅ Full spectral calibration
✅ Maximum accuracy
✅ Professional results
✅ Scientific approach

### For Everyone
✅ Always appropriate for skill level
✅ No confusing options
✅ Clear calibration path
✅ Better color matching

---

## Comparison: Pigment Strength vs K/S

| Feature | Pigment Strength | K/S Calibration |
|---------|-----------------|-----------------|
| **Complexity** | Simple | Advanced |
| **Equipment** | Visual only | Camera + scan |
| **Time** | 2-3 minutes | 5-10 minutes |
| **Accuracy** | Good | Excellent |
| **Knowledge Required** | None | Some technical |
| **Works With** | RGB & KM | KM only |
| **Best For** | Quick fixes | Professionals |

---

## Testing Checklist

- [x] Main settings calibration chooser
- [x] Single pump settings calibration chooser
- [x] KM disabled → Shows Pigment Strength
- [x] KM enabled → Shows K/S Calibration
- [x] Both dialogs properly invoked
- [x] Pigment detection works (CMYKW)
- [x] Flow rate always available

---

## What Users Will See

### Scenario 1: Casual User (KM Disabled)
1. Mix doesn't match screen
2. Click "Calibrate" on Cyan pump
3. See "Pigment Strength (Simple)" option
4. Dispense test dot
5. Adjust slider until colors match
6. Save - done!

### Scenario 2: Pro User (KM Enabled)
1. Want maximum accuracy
2. Click "Calibrate" on Cyan pump
3. See "K/S Calibration (Advanced)" option
4. Scan pigment with camera
5. Get full spectral profile
6. Save - done!

### Scenario 3: Switching Modes
1. Started with KM disabled
2. Used Pigment Strength calibration
3. Enable KM for better accuracy
4. Calibration option switches to K/S
5. Can now do advanced calibration
6. Both values saved independently

---

## Result

✅ **Two-tier calibration system**
- Simple for beginners
- Advanced for pros
- Automatic based on KM setting

✅ **No confusion**
- Only shows relevant options
- Clear button labels
- Appropriate for mode

✅ **Best of both worlds**
- Quick visual matching available
- Advanced spectral available
- User chooses complexity level

**Perfect solution!** 🎨✨

---

## Note on Data Storage

Both calibration methods store their data separately:
- **Pigment Strength**: Stored in `pigmentStrengths` (cyan, magenta, yellow, black, white)
- **K/S Values**: Stored in pigment K/S maps (spectral data)

Switching between KM modes doesn't lose either set of calibrations!
