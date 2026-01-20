# 🎨 Settings Reorganization Proposal

## Current Issues

1. **No clear grouping** - settings are mixed together
2. **Long scrolling** - everything in one flat list
3. **Hard to find things** - no visual hierarchy
4. **Repetitive headers** - too many HorizontalDividers
5. **Calibration buried** - important features hard to access

---

## ✨ Proposed New Structure

### Use Expandable Sections (Accordion Style)

```
┌─────────────────────────────────────────┐
│ Machine Settings                    [✓] │
├─────────────────────────────────────────┤
│                                          │
│ > 🔧 Connection Settings        [▼]    │
│   ├─ FluidNC IP Address                │
│   ├─ [Open Web Control]                │
│   └─ ☑ Bypass Connection Check         │
│                                          │
│ > 🎯 Dispensing Settings        [▶]    │
│                                          │
│ > 💧 Pump Configuration         [▶]    │
│                                          │
│ > 🎨 Color Mixing Algorithm     [▶]    │
│                                          │
│ > 🔄 Pulse Mode                 [▶]    │
│                                          │
│ > 📊 Display Settings           [▶]    │
│                                          │
│ > 💾 Data Management            [▶]    │
│                                          │
│ > 🐛 Debug Options              [▶]    │
│                                          │
│                    [Done]                │
└─────────────────────────────────────────┘
```

---

## 📋 Detailed Organization

### 1. 🔧 Connection Settings (Default: Expanded)
```
FluidNC IP Address: [192.168.x.x    ]
[🌐 Open FluidNC Web Control]
☑ Bypass Connection Check
```

### 2. 🎯 Dispensing Settings (Default: Collapsed)
```
Flow Rate (mL/sec): [50     ]
Retraction Steps: [25     ] [Tune]
```

### 3. 💧 Pump Configuration (Default: Collapsed)
```
┌──────────────────────────────────┐
│ ● Cyan                  Axis: X  │
│ [Calibrate]  [Prime]             │
│ 45ml ▓▓▓▓▓░░░░░░░ [Refill]      │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│ ● Magenta              Axis: Y   │
│ [Calibrate]  [Prime]             │
│ 67ml ▓▓▓▓▓▓▓▓░░░░ [Refill]      │
└──────────────────────────────────┘

... (all pumps)
```

### 4. 🎨 Color Mixing Algorithm (Default: Collapsed)
```
Currently using: Spectral absorption/scattering (K-M)

☑ Use Kubelka-Munk Theory
  ⚠️ More accurate but may be slower
  [📊 Edit K/S Values]
```

### 5. 🔄 Pulse Mode (Default: Collapsed)
```
☐ Enable Pulse Mode
⚠️ Only needed for tubes
   >3mm inner diameter.
   If unsure, leave as is.

Minimum pulses: [●1] [2] [3] [5]

Pump Calibrations:
  Cyan: 0.52 mL/pulse [Calibrate]
  Magenta: 0.48 mL/pulse [Calibrate]
  ...
```

### 6. 📊 Display Settings (Default: Collapsed)
```
☑ Show 'Reality Check' Warnings
☐ Show 'Real Paint' Preview (⚠️ Inaccurate)
```

### 7. 💾 Data Management (Default: Collapsed)
```
[Export Backup] [Import Backup]
Exports colors, photos, and settings to a single file
```

### 8. 🐛 Debug Options (Default: Collapsed)
```
☐ Enable Camera Color Picker (Beta)
☐ Show Debug Terminal
```

---

## 💡 Key Benefits

### Better UX
- ✅ **Find things faster** - clear categories
- ✅ **Less scrolling** - collapse unused sections
- ✅ **Visual hierarchy** - icons + bold headers
- ✅ **Mobile friendly** - one section visible at a time

### Better Organization
- ✅ **Logical grouping** - related settings together
- ✅ **Priority ordering** - important stuff first
- ✅ **Cleaner look** - no walls of text
- ✅ **Expandable** - easy to add new settings

---

Would you like me to implement this reorganization?
