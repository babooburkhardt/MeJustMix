# 🛠️ MeJustMix Assembly Guide

This guide covers the assembly of the mechanical and electrical components of the MeJustMix paint mixing machine.

> **⚠️ Note:** This is a work in progress. Some sections require you to reference the specific 3D model files or use your best judgment ("Figure it out").

---

## 📦 Bill of Materials (BOM) Comparison
Ensure you have all items listed in the main `README.md`.

**Critical Components:**
- **Motherboard:** MKS TinyBee V1.0
- **Stepper Drivers:** 5x TMC2209 (or similar StepStick drivers) - *Note: Not explicitly listed in original BOM, but required for MKS TinyBee.*
- **Motors:** 5x Nema 17 Stepper Motors
- **Power Supply:** 24V 5A PSU
- **Pumps:** 3D printed components + ZeVeX Planetary Gear system

---

## 1️⃣ Pump Assembly (x5)
Each pump unit consists of a Nema 17 motor, a 3D printed housing, a planetary gear system, and rollers.

### Parts per Pump:
- 1x Nema 17 Motor
- 1x Compact Planetary Gear set (Sun gear, Planet gears, Ring gear/Housing)
- 3x 608 Bearings (for rollers)
- Silicone Tubing (3mm ID)
- M3 Screws & Heatset Inserts

### Assembly Steps:
1.  **Prepare the Housing:**
    - Insert M3 heatset inserts into the mounting holes on the printed pump housing.
    - *Figure it out:* Exact locations of inserts depend on the STL files.

2.  **Assemble the Planetary Gear:**
    - Mount the "Sun" gear onto the Nema 17 motor shaft.
    - Place the "Planet" gears onto the carrier.
    - *Figure it out:* Assemble the specific ZeVeX gear configuration as per the Printables model instructions. Ensure smooth rotation.

3.  **Install Rollers & Bearings:**
    - press fit the 608 bearings into the printed rollers.
    - Mount the rollers onto the triangle carrier driven by the gearbox.

4.  **Route the Tubing:**
    - Feed the silicone tubing through the intake hole.
    - Wrap it carefully around the roller path.
    - Feed it out the exit hole.
    - **Tip:** Don't stretch the tubing too tight, or the motor may stall. Too loose, and it won't pump.

5.  **Test Rotation:**
    - Manually turn the gears to ensure the rollers compress the tube against the housing wall and move freely.

---

## 2️⃣ Frame & Housing Assembly
1.  **Mount Pumps:**
    - Secure each of the 5 assembled pumps to the main housing/frame using M3 screws.
    - *Figure it out:* Orientation of pumps (typically vertical or angled for gravity feed).

2.  **Power Supply Mounting:**
    - Secure the 24V power supply to the base or back of the frame.
    - Ensure airflow to the PSU fan.

3.  **Controller Mounting:**
    - Mount the MKS TinyBee board using standoffs.
    - Ensure the USB port and SD card slot are accessible.

---

## 3️⃣ Electronics Wiring
**⚠️ WARNING:** Always double-check polarity. Incorrect wiring can destroy your board.

### A. Power Supply
- Connect **24V DC+** from PSU to **VIN (+)** on MKS TinyBee.
- Connect **GND (-)** from PSU to **GND (-)** on MKS TinyBee.

### B. Stepper Drivers
- Install 5x Stepper Drivers (e.g., TMC2209) into the slots X, Y, Z, E0, E1.
- **Crucial:** Pay attention to the orientation! The "EN" pin on the driver must match the "EN" pin on the board. Installing backwards will fry the board.

### C. Motor Wiring
Connect the 4-wire Nema 17 cables to the board. 
*Note: If a motor spins backwards later, simply flip the plug 180°.*

| Pump / Color | Axis ID | MKS TinyBee Port | Config Pin (Step/Dir) |
| :--- | :--- | :--- | :--- |
| **Pump 1 (Cyan)** | X | X_AXIS | i2so.1 / i2so.2 |
| **Pump 2 (Magenta)** | Y | Y_AXIS | i2so.4 / i2so.5 |
| **Pump 3 (Yellow)** | Z | Z_AXIS | i2so.7 / i2so.8 |
| **Pump 4 (Black)** | A | E0_AXIS | i2so.10 / i2so.11 |
| **Pump 5 (White)** | B | E1_AXIS | i2so.13 / i2so.14 |

*(Note: The MKS TinyBee usually labels axes X, Y, Z, E0, E1. Our config maps these to X, Y, Z, A, B).*

---

## 4️⃣ Final Setup
1.  **Tube Connections:**
    - Connect the **Input** side of the tubes to your paint reservoirs (Pigment bottles).
        - *Figure it out:* How you attach the Mio caps/valves to your specific bottles.
    - Connect the **Output** tubes to the mixing nozzle/manifold.

2.  **Cable Management:**
    - Use zip ties to tidy up the motor wires.
    - Ensure wires don't touch moving gears.

3.  **Firmware:**
    - Follow the **Firmware Installation** section in the `README.md` to flash FluidNC and upload `config.yaml`.

4.  **Calibration:**
    - Use the app to run each pump.
    - If a pump vibrates but doesn't turn, check wiring pairs (coil A vs coil B).
    - If a pump turns backwards (sucks instead of blows), flip the plug or change settings in the app/config.

---

## ❓ Troubleshooting & "Figure It Out" Zones
- **Gear Friction:** If pumps bind, use lithium grease on the planetary gears.
- **Tube Slip:** If the tube walks out of the pump, check the printed housing constraints or add a zip-tie constraint at the entry/exit.
- **Motor Heat:** Nema 17s can get hot. Adjust the `run_current` in `config.yaml` if they are too hot to touch (typically 0.6A - 0.8A is sufficient for pumps).

Good luck with the build! 🚀
