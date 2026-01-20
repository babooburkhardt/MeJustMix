# ✅ TODO FIXED: K/S Calibration Only Shows When KM Enabled

## Issue
When Kubelka-Munk theory was disabled, the calibration dialog still showed the "Color Calibration" option to set K/S values, which doesn't make sense since K/S values are only used by the Kubelka-Munk algorithm.

## Solution
Added a check for `uiState.useKubelkaMunk` before showing the color calibration button.

## Changes Made

### SettingsModal.kt

**Location 1: Main Calibration Chooser Dialog (line ~219)**
```kotlin
// Before:
val isPigment = listOf("Cyan", "Magenta", "Yellow", "Black", "White").any { pump.name.contains(it, ignoreCase = true) }
if (isPigment) {
    Button(...) { Text("Color Calibration") }
}

// After:
val isPigment = listOf("Cyan", "Magenta", "Yellow", "Black", "White").any { pump.name.contains(it, ignoreCase = true) }
// Only show color calibration if KM is enabled AND it's a pigment
if (isPigment && uiState.useKubelkaMunk) {
    Button(...) { Text("Color Calibration") }
}
```

**Location 2: Single Pump Settings Dialog (line ~860)**
```kotlin
// Same fix applied to SinglePumpSettingsDialog
```

## How It Works Now

### When KM is ENABLED:
- Pigment pumps (Cyan, Magenta, Yellow, Black, White) show:
  - ✅ Flow Rate calibration
  - ✅ Color Calibration (K/S values)

### When KM is DISABLED:
- All pumps show:
  - ✅ Flow Rate calibration only
  - ❌ No Color Calibration option

### Non-Pigment Pumps:
- Always show:
  - ✅ Flow Rate calibration only
  - ❌ Never show Color Calibration

## User Experience

**Before Fix:**
1. User disables Kubelka-Munk
2. User clicks "Calibrate" on Cyan pump
3. Dialog shows "Color Calibration" button ❌
4. User clicks it and calibrates K/S values
5. Those values are never used (confusing!)

**After Fix:**
1. User disables Kubelka-Munk
2. User clicks "Calibrate" on Cyan pump
3. Dialog shows only "Flow Rate" button ✅
4. User calibrates flow rate
5. Clear and logical!

## Testing Checklist

- [x] Fixed main settings calibration chooser
- [x] Fixed single pump settings calibration chooser
- [x] Only shows when `useKubelkaMunk == true`
- [x] Only shows for pigment pumps
- [x] Flow rate option always available

## Result

✅ K/S calibration option now properly respects the KM toggle setting
✅ No confusing options when KM is disabled
✅ Better user experience - only show relevant options

**TODO Complete!** 🎉
