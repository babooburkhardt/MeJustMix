_# MeJustMix

**MeJustMix** is the Android app controller for a DIY paint mixing machine. It handles the brains of the operation so you can mix paints (or whatever else you're pumping) without the headache.

> **Note:** This repo contains the Android source code. The printed files are hosted separately.

---

## 🛠️ The Hardware
For the STLs, and motor mounts, head over to the project page:
👉 **[MeJustMix on Printables](https://www.printables.com/model/1563458-mejustmix)**

## Assembly guide is currently AI generated. and only handles all the electrical side. as it cannot understand STLs. im working on it myself, so treat the current guide as something to fill the gap while i find spare time to work on it

### Bill of Materials (BOM)     ..if i forgot something i apologize.. still a WIP
To build the full 5-pump mixer, you will need:

**Electronics & Motors**
* **1x** [MKS TinyBee](https://a.co/d/3X2XGmC) (Motherboard)
* **5x** [TMC2209 stepper driver](https://a.co/d/hhsNy26) (or any other stepper driver, its just these are silent by default with our setup, so no stepper noise everytime you make paint)
* **1x** [24v 5a power supply](https://a.co/d/igveDYC)
* **5x** [Nema 17 Stepper Motors](https://a.co/d/8bX0HPr) (any Standard nema17 size, except **do not** use pancake motors)
* **5x** [Compact Planetary Gear for Nema17](https://www.printables.com/@ZeVeX_22610) (4:1 ratio) - *See Credits below or description of MeJustMix on printables* 

**Printed Parts** - I will set up a section on PCBway for a marketplace to get resin parts or FDM parts for those who dont own a printer.
* **5x** 3D Printed Pumps (FDM reccomended, if using resin, you will need lube)
* **5x** 3D Printed hose adapters (Must use resin or it WILL leak)
* **1x** 3D Printed Housing (resin reccomende, but optional)

**Hardware**
* **15x** [Skateboard Bearings](https://a.co/d/cYjuKLy) (Standard 608)
* **[Screw Assortment](https://a.co/d/it1I78U)** (this has all but the heatset inserts and skate bearings)
* **15x** M3 Hex Nuts (in screw assortment)
* **20x** 25mm M3 screws (in screw assortment)
* **15x** 16mm M3 screws (in screw assortment)
* **20x** 6mm M3 screws (in screw assortment)
* **20x** [M3 Heatset Inserts](https://a.co/d/5PxoURc) (5mm diameter x 4mm length)

**Plumbing**
* [**Silicone Tubing:**](https://a.co/d/109G1Nu) 3mm ID x 5mm OD (8ft+ recommended) Larger and smaller sizes can fit, up to 8mm outer diameter. But I found 3mm inner diameter to be the sweet spot between flow and accuracy. Inner diameter MUST be 2mm smaller than the outer diamter, or you can custom design your own insert to adjust the spacing. I left it modular for this reason
* [**Luer Lock G18 needle**](https://a.co/d/7jotTo9)
* (OPTIONAL alternative to luer lock g18) **5x** Mio "Water Enhancer" caps (or generic store brand equivalents. I used the Kroger/Smiths store brand in my original model for cost reasons). these bottle caps use squeeze valves that we will
  "harvest" by cutting away the extra plastic.
  
**Paint - Liquitex Acrylic Basic Fluid** (if you dont want to fight the quick calibrator, stick to these exact paints, others will be supported later)
* [**Cadmium Yellow Medium Hue**](https://a.co/d/0aRB7Bl)
* [**Phthanlocyanine Blue**](https://a.co/d/6JxlL5G)
* [**Mars Black**](https://a.co/d/33VU7aW) (or carbon black, but mars black is cheaper an does the same for our use)
* [**Quinacridone Magenta**](https://a.co/d/2lmyL3s)
* [**Titanium White**](https://a.co/d/jlMkRhW)

---

## ⚙️ Firmware Installation (MKS TinyBee)
We use **FluidNC** for motion control. The app supports **two connection modes**:
- **Bluetooth (BLE)** – Direct wireless connection, auto-discovery, no network setup required
- **WiFi** – Network-based connection, access to FluidNC web interface

### 1. Flash the Board
1.  Connect your MKS TinyBee to your PC via USB (Make sure to set the jumper near the USB port to USB power during setup. This lets you configure without a dedicated power supply).
2.  Open **[installer.fluidnc.com](https://installer.fluidnc.com)** in Chrome or Edge.
3.  Click **Connect** and select your board's serial port. (if unsure, try seeing what changes if the board is unplugged vs plugged in. the one that changes likely is your board)
4.  Select the latest firmware version.
5.  **Choose your variant:**
    - **For Bluetooth:** Select the **"Bluetooth"** or **"BT"** variant
    - **For WiFi:** Select the **"Wifi"** variant
    - ⚠️ **Note:** bluetooth has some troubleshooting to do as the fluid installer is bugged. even manually flashing is unsuccessful. ive done all testing so far with wifi.
6.  Click **Install**.

### 2. Configure the Machine
1.  Download the [`config.yaml`](config.yaml) file.
2.  In the Web Installer (or FluidNC Web UI), go to the **Config** tab.
3.  Upload the `.yaml` file.
4.  **If using WiFi:** Locate and write down the IP address (should be on the wifi page).
5.  **If using Bluetooth:** The device will advertise as "FluidNC" by default after a reboot.
6.  Restart the board.

*Note: If your pumps run backwards, flip the motor plug or change the direction pin in the config.*

---

### 3.📱 The App
This repository contains the Android application source code.

#### Installation
1.  Build the APK from source, or download from releases.
2.  Install on your Android device.

#### First Launch - Connection Setup
On first launch, the app will ask you to choose your connection method:

**Option 1: Bluetooth (Recommended)**
1. Select **"Bluetooth"** when prompted
2. The app will explain why location permission is needed (Android requirement for BLE scanning)
3. Tap **"Grant Permission"** to allow location access
   - *Note: The app does NOT track your location. This permission is only used to scan for Bluetooth devices.*
4. The app will automatically scan for nearby FluidNC devices
5. When your machine is found, tap to connect
6. That's it! No IP addresses, no network configuration needed.

**Option 2: WiFi**
1. Select **"WiFi"** when prompted
2. Enter your FluidNC's IP address (from step 2.4 above)
3. The app will connect over your local network
4. You can access the FluidNC web interface by tapping "Open FluidNC Web Control" in Settings

#### Using the App
1. **Calibration:** Run a few calibrations before mixing paint
   - *Tip: Start with water to test pumps, then move to paint after confirming it works and doesnt leak*
   - ⚠️ **Important:** Calibrate everything with paint, ***not water*** – they flow differently. 
2. **Mixing:** Select colors, adjust amounts, and dispense
3. **Multi-Machine Support (BLE only):** If you have multiple machines (e.g., one for acrylics, one for airbrush), you can add and switch between them in Settings

#### Switching Connection Modes
You can change between Bluetooth and WiFi anytime:
1. Open **Settings** → **Connection Settings**
2. Toggle between **[Bluetooth]** and **[WiFi]**
3. Configure the new connection method
4. The app will remember your choice

**Why Choose Bluetooth?**
- ✅ Auto-discovery – no IP addresses to remember
- ✅ Direct connection – works without WiFi network
- ✅ Multi-machine support – easily switch between multiple mixers
- ✅ Simpler setup – just turn on and connect

**Why Choose WiFi?**
- ✅ Access to FluidNC web interface
- ✅ No location permission required
- ✅ Longer range (depends on your network)


---

## 🙌 Credits & Acknowledgements
This project wouldn't be possible without the work of others:

* **[ZeVeX](https://www.printables.com/@ZeVeX_22610)** – For the **Compact Planetary Gear for Nema17 (4:1)** design used to drive the pumps.
* **The [FluidNC Team](http://wiki.fluidnc.com/)** – For the incredible firmware that powers the motion control.

---

## 📄 License
**Copyright (c) 2026 babooburkhardt**

This work is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International License**.

* **Attribution:** Give credit where it's due.
* **Non-Commercial:** You may not use this work for commercial purposes without my explicit permission.
_
