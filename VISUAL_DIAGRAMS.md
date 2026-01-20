# Visual System Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         USER INTERFACE                       │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │        PulseHomeScrollWheel Component                  │  │
│  │                                                         │  │
│  │    ┌────────────────────────────────────────┐         │  │
│  │    │  Visual 3-Roller Pump Representation   │         │  │
│  │    │         [Drag to Rotate]                │         │  │
│  │    │                                          │         │  │
│  │    │          ╔═══════╗                      │         │  │
│  │    │          ║  ◉ ◉ ◉║  ← Rollers            │         │  │
│  │    │          ║   ▼   ║  ← Home Indicator    │         │  │
│  │    │          ╚═══════╝                      │         │  │
│  │    └────────────────────────────────────────┘         │  │
│  │                                                         │  │
│  │    Steps: 800        From Home: 0 steps               │  │
│  │    [Snap to Home]  [Mark as Home Position]            │  │
│  └───────────────────────────────────────────────────────┘  │
│                             ▲                                │
│                             │ User Drag Gesture              │
│                             │                                │
└─────────────────────────────┼────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    CALCULATION LAYER                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  PulseModeCalculator.kt                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Drag Position  →  Angle  →  Steps  →  Commands    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  Motor Specs:                                                │
│  • Step Angle: 1.8°      →  200 steps/motor rev             │
│  • Gear Ratio: 1:4       →  800 steps/pump rev              │
│  • Rollers: 3            →  266.67 steps/pulse              │
│                                                               │
│  Calculations:                                               │
│  • Current step count from drag                              │
│  • Distance from pulse boundary                              │
│  • Snap to nearest home position                            │
│  • Convert volumes to steps                                  │
│                                                               │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      VIEW MODEL LAYER                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  SettingsViewModel                                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  jogPump(pumpIndex, steps)                          │    │
│  │    → Update local state                             │    │
│  │    → Send to motor controller                       │    │
│  │                                                       │    │
│  │  markPumpAsHomed(pumpIndex)                         │    │
│  │    → Snap to pulse boundary                         │    │
│  │    → Save home position                             │    │
│  │                                                       │    │
│  │  updatePumpCalibration(index, stepsPerPulse, mL)    │    │
│  │    → Save calibration values                        │    │
│  │                                                       │    │
│  │  dispensePulses(index, pulseCount, stepsPerPulse)   │    │
│  │    → Calculate total steps                          │    │
│  │    → Send dispense command                          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    HARDWARE INTERFACE                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  G-Code Commands (via HTTP/WebSocket/Serial)                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Example Commands:                                   │    │
│  │  • "G0 X50"     → Jog X axis forward 50 steps       │    │
│  │  • "G0 Y-100"   → Jog Y axis backward 100 steps     │    │
│  │  • "G0 Z2667"   → Dispense Z axis 2667 steps        │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PHYSICAL HARDWARE                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Stepper Motor (1.8° step angle)                            │
│         ↓ (1:4 gear reduction)                              │
│  Peristaltic Pump (3 rollers)                               │
│         ↓                                                    │
│  Fluid Dispensing                                            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow: User Drags Wheel

```
1. User Touch
   ┌──────────────────────────┐
   │ User drags finger on      │
   │ scroll wheel              │
   └──────────┬───────────────┘
              │
              ▼
2. Gesture Detection
   ┌──────────────────────────┐
   │ pointerInput detects      │
   │ drag gesture              │
   │ • Calculate touch angle   │
   │ • Compute delta angle     │
   └──────────┬───────────────┘
              │
              ▼
3. Angle to Steps
   ┌──────────────────────────┐
   │ Convert angle to steps    │
   │ deltaSteps = (deltaAngle  │
   │   / 360°) × stepsPerPulse │
   └──────────┬───────────────┘
              │
              ▼
4. Update State
   ┌──────────────────────────┐
   │ accumulatedSteps +=       │
   │   deltaSteps              │
   │ onOffsetChange(steps)     │
   └──────────┬───────────────┘
              │
              ▼
5. Callback to ViewModel
   ┌──────────────────────────┐
   │ viewModel.jogPump(        │
   │   pumpIndex, deltaSteps   │
   │ )                         │
   └──────────┬───────────────┘
              │
              ▼
6. Send to Hardware
   ┌──────────────────────────┐
   │ sendGCode(                │
   │   "G0 ${axis}${steps}"    │
   │ )                         │
   └──────────┬───────────────┘
              │
              ▼
7. Motor Moves
   ┌──────────────────────────┐
   │ Physical pump rotates     │
   │ User sees liquid shift    │
   │ in tubing                 │
   └──────────────────────────┘
```

## Home Position Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    HOMING WORKFLOW                           │
└─────────────────────────────────────────────────────────────┘

Step 1: Initial Position (Unknown)
┌────────────────────────────────────┐
│  Pump at arbitrary position         │
│  Steps: ???                         │
│  Status: Not Homed                  │
└────────────────────────────────────┘
                 │
                 │ User drags wheel
                 ▼
Step 2: Positioning
┌────────────────────────────────────┐
│  User rotates to align roller       │
│  Steps: 750 (example)              │
│  From Home: 50 steps               │
│  Status: Near Home                  │
└────────────────────────────────────┘
                 │
                 │ User clicks "Snap to Home"
                 ▼
Step 3: Snap to Boundary
┌────────────────────────────────────┐
│  System calculates nearest          │
│  pulse boundary:                    │
│  750 → round(750/267) × 267 = 800  │
│  Jogs +50 steps                     │
└────────────────────────────────────┘
                 │
                 │ Auto-aligned
                 ▼
Step 4: At Home Position
┌────────────────────────────────────┐
│  Pump perfectly aligned             │
│  Steps: 800                         │
│  From Home: 0 steps                 │
│  Status: ✓ At Pulse Boundary        │
└────────────────────────────────────┘
                 │
                 │ User clicks "Mark as Home"
                 ▼
Step 5: Home Saved
┌────────────────────────────────────┐
│  Home position recorded             │
│  pulseHomeOffset = 800              │
│  Status: ✓ Homed                    │
│  Ready for calibration              │
└────────────────────────────────────┘
```

## Pulse Calculation Diagram

```
┌─────────────────────────────────────────────────────────────┐
│              VOLUME TO MOTOR STEPS                           │
└─────────────────────────────────────────────────────────────┘

User Wants: 5 mL
     │
     │ ÷ mL per pulse (0.5)
     ▼
Pulses Needed: 10 pulses
     │
     │ × steps per pulse (266.67)
     ▼
Motor Steps: 2667 steps
     │
     │ G-code: "G0 X2667"
     ▼
Physical Rotation: 3.33 pump revolutions
     │
     │ Fluid dispensed
     ▼
Result: 5.0 mL (exact!)

┌─────────────────────────────────────────────────────────────┐
│           ROUNDING EXAMPLE (NON-EXACT)                       │
└─────────────────────────────────────────────────────────────┘

User Wants: 5.3 mL
     │
     │ ÷ mL per pulse (0.5)
     ▼
Pulses Calculated: 10.6 pulses
     │
     │ round()
     ▼
Actual Pulses: 11 pulses
     │
     │ × steps per pulse (266.67)
     ▼
Motor Steps: 2933 steps
     │
     │ × mL per pulse
     ▼
Actual Volume: 5.5 mL
     │
     │ Error: +0.2 mL (+3.8%)
     ▼
Result: Slight overage, but within tolerance
```

## Motor Geometry

```
┌─────────────────────────────────────────────────────────────┐
│                    MOTOR TO PUMP GEOMETRY                    │
└─────────────────────────────────────────────────────────────┘

                     STEPPER MOTOR
                    ┌──────────────┐
                    │              │
    1.8° steps  ──→ │   ╔═══╗      │
    200 steps/rev   │   ║   ║      │
                    │   ╚═══╝      │
                    └──────┬───────┘
                           │
                           │ Gear 1:4 reduction
                           │ (4 motor turns = 1 pump turn)
                           ▼
                    ┌──────────────┐
                    │  PUMP SHAFT  │
                    │   800 steps  │
                    │   per rev    │
                    └──────┬───────┘
                           │
                           ▼
                    ╔══════════════╗
                    ║   3 ROLLERS  ║
                    ║              ║
                    ║   ◉  ◉  ◉   ║  ← 120° apart
                    ║              ║
                    ║   TUBE ===   ║  ← Compressed here
                    ╚══════════════╝

Each roller rotation (1 pulse):
• Pump rotates 120°
• 800 steps ÷ 3 = 266.67 steps
• Dispenses fixed volume (calibrated)

Home Position:
• One roller JUST PAST compression
• Ensures consistent start/stop
• Next pulse begins at same point
```

## Pulse Boundaries Visualization

```
┌─────────────────────────────────────────────────────────────┐
│                  PULSE BOUNDARIES ON AXIS                    │
└─────────────────────────────────────────────────────────────┘

Steps:  0      267     534     801     1068    1335
        │       │       │       │       │       │
        ▼       ▼       ▼       ▼       ▼       ▼
    ────┼───────┼───────┼───────┼───────┼───────┼────
        │       │       │       │       │       │
      Pulse 0  Pulse 1 Pulse 2 Pulse 3 Pulse 4 Pulse 5
      (Home)

Example Positions:
• 0 steps    →  At Home ✓
• 100 steps  →  100 from home ✗
• 267 steps  →  At boundary ✓
• 750 steps  →  50 from next boundary ✗
• 801 steps  →  At boundary ✓

Snap to Home Logic:
Position 750:
  750 ÷ 267 = 2.81 pulses
  round(2.81) = 3 pulses
  3 × 267 = 801 steps  ← Snap here!
  Jog: 801 - 750 = +51 steps
```

## Component Relationships

```
┌─────────────────────────────────────────────────────────────┐
│                    FILE DEPENDENCIES                         │
└─────────────────────────────────────────────────────────────┘

PulseCalibrationDialog.kt
         │
         ├─→ Uses: CompactPulseHomeWheel
         │         (embedded in Step 2)
         │
         └─→ Callbacks to ViewModel:
             • onJogPump(steps, stepsPerPulse)
             • onMarkHome()

PulseHomeScrollWheel.kt
         │
         ├─→ Exports: PulseHomeScrollWheel (full)
         ├─→ Exports: CompactPulseHomeWheel (compact)
         └─→ Exports: calculateStepsPerPulse()

PulseHomeTestScreen.kt
         │
         ├─→ Uses: PulseHomeScrollWheel (full version)
         └─→ Uses: calculateStepsPerPulse()

PulseModeCalculator.kt
         │
         └─→ Utility functions for:
             • Step/pulse/mL conversions
             • Home position calculations
             • Error calculations

SettingsViewModel.kt
         │
         ├─→ Imports: PulseModeCalculator
         │
         └─→ Implements:
             • jogPump(pumpIndex, steps)
             • markPumpAsHomed(pumpIndex)
             • dispensePulses(...)
             • updatePumpCalibration(...)
```

## Integration Points

```
┌─────────────────────────────────────────────────────────────┐
│              YOUR EXISTING CODE ←→ NEW COMPONENTS            │
└─────────────────────────────────────────────────────────────┘

                  YOUR MOTOR CONTROLLER
                         ↑
                         │ G-code commands
                         │
              ┌──────────┴──────────┐
              │                      │
        jogPump()            dispensePulses()
              │                      │
              └──────────┬───────────┘
                         │
                  SettingsViewModel
                         ↑
                         │ Callbacks
                         │
              ┌──────────┴──────────┐
              │                      │
     PulseHomeScrollWheel    PulseCalibrationDialog
              │                      │
              └──────────┬───────────┘
                         │
                    User Touch
                         │
                   Android View
```

---

**Key Insight**: You only need to implement TWO functions in your ViewModel:
1. `jogPump()` - Send steps to motor controller
2. `markPumpAsHomed()` - Save home position

Everything else (UI, calculations, conversions) is handled by the components!
