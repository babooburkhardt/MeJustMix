# Integration Checklist ✓

Use this checklist to track your progress integrating the pulse mode scroll wheel.

## Phase 1: Code Integration

### Files Created (Already Done ✓)
- [x] `PulseHomeScrollWheel.kt` - Scroll wheel components
- [x] `PulseHomeTestScreen.kt` - Test screen
- [x] `PulseModeCalculator.kt` - Calculation utilities
- [x] Modified `PulseCalibrationDialog.kt` - Integrated scroll wheel
- [x] Documentation files

### ViewModel Updates (Your Task)
- [ ] Open `SettingsViewModel.kt`
- [ ] Add import: `import com.example.mejustmix.utils.PulseModeCalculator`
- [ ] Add function: `jogPump(pumpIndex: Int, steps: Int)`
- [ ] Add function: `markPumpAsHomed(pumpIndex: Int)`
- [ ] Add function: `dispensePulses(pumpIndex: Int, pulseCount: Int, stepsPerPulse: Float)`
- [ ] Add function: `updatePumpCalibration(pumpIndex: Int, stepsPerPulse: Float, mlPerPulse: Float)`
- [ ] Implement: `sendGCodeToController(gcode: String)` (connect to your motor controller)

**Reference**: See `VIEWMODEL_ADDITIONS.kt` for complete code

### Dialog Connection (Your Task)
- [ ] Find where you call `PulseCalibrationDialog`
- [ ] Add callback: `onJogPump = { steps, _ -> viewModel.jogPump(selectedIndex, steps) }`
- [ ] Add callback: `onMarkHome = { viewModel.markPumpAsHomed(selectedIndex) }`
- [ ] Add callback: `onDispensePulses = { count, stepsPerPulse -> viewModel.dispensePulses(...) }`
- [ ] Add callback: `onSave = { stepsPerPulse, mlPerPulse -> viewModel.updatePumpCalibration(...) }`

**Reference**: See `QUICK_START.md` for example

---

## Phase 2: Testing Without Hardware

### UI Testing
- [ ] Build and run app
- [ ] Navigate to pump calibration
- [ ] Verify scroll wheel appears in Step 2
- [ ] Test dragging - rollers should rotate visually
- [ ] Verify step count updates in real-time
- [ ] Check "Distance from home" updates
- [ ] Test "Snap to Home" button
- [ ] Verify "Mark as Home" enables when aligned
- [ ] Confirm state persists when navigating away and back

### Calculation Testing
- [ ] Verify steps per pulse shows ~267 (or your calculated value)
- [ ] Check that dragging 360° equals ~267 steps
- [ ] Confirm home detection works (shows "Aligned" when at boundary)
- [ ] Test with different starting positions

**Testing Tip**: Comment out G-code sending for now:
```kotlin
// sendGCodeToController("G0 ${pump.axis}$steps")  // Comment for UI testing
```

---

## Phase 3: Hardware Integration

### Motor Controller Connection
- [ ] Uncomment `sendGCodeToController()` call
- [ ] Verify G-code format matches your controller
  - [ ] HTTP endpoint correct?
  - [ ] G-code syntax correct? (e.g., "G0 X50" vs "G0X50")
  - [ ] Authentication required?
- [ ] Test connectivity to controller

### Motor Response Testing
- [ ] Drag scroll wheel slightly
- [ ] Verify motor moves
- [ ] Check motor moves in correct direction
  - [ ] If backwards, invert step sign: `G0 ${pump.axis}${-steps}`
- [ ] Verify step count matches motor movement
- [ ] Test all pumps (X, Y, Z, A, B axes)

### Home Position Testing
- [ ] Manually position roller just past compression point
- [ ] Use scroll wheel to fine-tune position
- [ ] Click "Snap to Home"
- [ ] Verify pump aligns at pulse boundary
- [ ] Click "Mark as Home"
- [ ] Confirm home position saved (check state)

---

## Phase 4: Full Calibration Workflow

### Per-Pump Calibration
For EACH pump (Cyan, Magenta, Yellow, Black, White):

- [ ] **Pump 1 (Cyan)**
  - [ ] Step 1: Verify steps per pulse (~267)
  - [ ] Step 2: Home the pump using scroll wheel
  - [ ] Step 3: Dispense 10 test pulses
  - [ ] Step 4: Measure output (e.g., 5.0 mL)
  - [ ] Step 4: Calculate mL per pulse (e.g., 0.5 mL)
  - [ ] Save calibration

- [ ] **Pump 2 (Magenta)**
  - [ ] Complete Steps 1-4
  - [ ] Save calibration

- [ ] **Pump 3 (Yellow)**
  - [ ] Complete Steps 1-4
  - [ ] Save calibration

- [ ] **Pump 4 (Black)** (if applicable)
  - [ ] Complete Steps 1-4
  - [ ] Save calibration

- [ ] **Pump 5 (White)** (if applicable)
  - [ ] Complete Steps 1-4
  - [ ] Save calibration

### Calibration Validation
- [ ] Values seem reasonable? (typically 0.3-1.0 mL per pulse)
- [ ] Consistent across similar pumps?
- [ ] Re-test one pump to verify repeatability

---

## Phase 5: Functional Testing

### Pulse Mode Dispensing
- [ ] Enable pulse mode in settings
- [ ] Create a simple mix (e.g., 2mL + 3mL + 1mL)
- [ ] Verify volumes round to whole pulses
- [ ] Dispense the mix
- [ ] Measure actual output
- [ ] Compare to expected (should match closely)

### Accuracy Testing
- [ ] Test with larger volumes (more accurate)
  - [ ] Mix with 10mL+ per component
  - [ ] Measure accuracy
- [ ] Test with smaller volumes (less accurate)
  - [ ] Mix with 1-2mL per component
  - [ ] Note rounding effects
- [ ] Test edge cases
  - [ ] Very small amounts (< 1 pulse)
  - [ ] Verify minimum pulse enforcement

### Multi-Pump Testing
- [ ] Mix using all 5 pumps
- [ ] Verify each homes correctly
- [ ] Check interference (pumps don't affect each other)
- [ ] Test sequential dispensing

---

## Phase 6: Motor Specification Verification

### If Your Motor Specs Are Different
Only complete this section if your motors don't match the defaults:

- [ ] Measure actual step angle: _____ degrees (default: 1.8°)
- [ ] Confirm gear reduction: ___:___ (default: 1:4)
- [ ] Count pump rollers: _____ (default: 3)
- [ ] Calculate steps per pulse: _____ (default: ~267)
- [ ] Update `MotorSpecs` in `PulseModeCalculator.kt`
- [ ] Re-test scroll wheel behavior

**Calculation**:
```
Steps per motor rev = 360° / step_angle
Steps per pump rev = steps_per_motor_rev × gear_reduction  
Steps per pulse = steps_per_pump_rev / roller_count
```

---

## Phase 7: Optional Enhancements

### Test Screen (Optional)
- [ ] Add navigation to `PulseHomeTestScreen`
- [ ] Test standalone screen
- [ ] Use for development/debugging

### Persistence
- [ ] Verify home positions persist across app restarts
- [ ] Check calibration values save correctly
- [ ] Test pulse mode settings persist

### User Experience
- [ ] Add tooltips or help text
- [ ] Consider haptic feedback on alignment
- [ ] Add sound effects (optional)
- [ ] Smooth animations (already implemented)

---

## Phase 8: Production Readiness

### Code Review
- [ ] Remove debug logging
- [ ] Remove commented-out code
- [ ] Add error handling for edge cases
- [ ] Handle network failures gracefully
- [ ] Add user-friendly error messages

### Documentation
- [ ] Add inline code comments
- [ ] Document G-code format in code
- [ ] Create user guide for calibration
- [ ] Add troubleshooting section

### Testing
- [ ] Test on multiple devices
- [ ] Test different screen sizes
- [ ] Test with slow motor responses
- [ ] Test with network latency
- [ ] Handle motor disconnection gracefully

---

## Troubleshooting Checklist

If something doesn't work, check:

### Scroll Wheel Not Visible
- [ ] Imports correct?
- [ ] In correct step of calibration? (Step 2)
- [ ] Dialog showing at all?

### Wheel Not Responding to Touch
- [ ] `onOffsetChange` callback connected?
- [ ] Check for overlapping touch handlers?
- [ ] Test on real device (not just emulator)?

### Motor Not Moving
- [ ] G-code reaching controller?
- [ ] G-code format correct?
- [ ] Motor powered?
- [ ] Correct axis selected?
- [ ] Step count reasonable? (not 0 or too large)

### Motor Moves Wrong Direction
- [ ] Invert step sign: `-steps` instead of `steps`
- [ ] Or adjust in G-code generator

### Steps Don't Match Physical Movement
- [ ] Verify motor specs
- [ ] Check microstepping settings
- [ ] Confirm gear reduction
- [ ] Count rollers physically

### Can't Achieve Perfect Home
- [ ] Check for mechanical backlash
- [ ] Verify motor holding torque
- [ ] Increase tolerance in code (currently 1 step)
- [ ] Consider physical detent

### Dispensing Inaccurate
- [ ] Recalibrate mL per pulse
- [ ] Check for tube wear
- [ ] Verify consistent liquid viscosity
- [ ] Check for air bubbles in lines
- [ ] Confirm pump home before dispensing

---

## Success Criteria

Your integration is complete when:

✅ **UI Works**
- Scroll wheel appears and responds to touch
- Visual feedback is smooth
- Step counting is accurate
- Home alignment detection works

✅ **Motor Control Works**
- Motor responds to jog commands
- Direction is correct
- Step count matches movement
- All pumps can be controlled

✅ **Calibration Works**
- Can complete all 4 steps for each pump
- Values are reasonable and consistent
- Calibration persists

✅ **Dispensing Works**
- Pulse mode produces accurate volumes
- Ratios match requested values
- Repeatability is good

---

## Quick Reference

**Key Files**:
- Main component: `PulseHomeScrollWheel.kt`
- Calculations: `PulseModeCalculator.kt`
- Your code: `SettingsViewModel.kt`

**Key Functions to Implement**:
1. `jogPump(pumpIndex, steps)` - Send steps to motor
2. `markPumpAsHomed(pumpIndex)` - Save home position

**Key Values**:
- Steps per pulse: ~267 steps (auto-calculated)
- Typical mL per pulse: 0.3-1.0 mL (calibrated)
- Tolerance: ±1 step for home detection

**Documentation**:
- Quick start: `QUICK_START.md`
- Full guide: `PULSE_MODE_INTEGRATION.md`
- Math: `PULSE_MODE_MATH.md`
- Diagrams: `VISUAL_DIAGRAMS.md`

---

## Done! 🎉

When all checkboxes are marked, you have a fully functional pulse mode scroll wheel integrated into your app!

**Last Step**: Remove this checklist from your project (or keep for reference)
