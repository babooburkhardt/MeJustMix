# MeJustMix

**MeJustMix** is the Android app controller for a DIY paint mixing machine. It handles the brains of the operation so you can mix paints (or whatever else you're pumping) without the headache.

> **Note:** This repo contains the Android source code. The printed files are hosted separately.

---

## 🛠️ The Hardware
For the STLs, full assembly instructions, and motor mounts, head over to the project page:
👉 **[MeJustMix on Printables](https://www.printables.com/model/1563458-mejustmix)**

### Bill of Materials (BOM)     ..if i forgot something i apoligize.. still a WIP
To build the full 5-pump mixer, you will need:

**Electronics & Motors**
* **1x** MKS TinyBee (Motherboard)
* **1x** 24v 5a power supply
* **5x** Nema 17 Stepper Motors (Standard size, **do not** use pancake motors)
* **5x** Compact Planetary Gear for Nema17 (4:1 ratio) - *See Credits below or description of MeJustMix on printables* 

**Printed Parts**
* **5x** 3D Printed Pumps
* **5x** 3D Printed hose adapters
* **1x** 3D Printed Housing

**Hardware**
* **15x** Skateboard Bearings (Standard 608)
* **15x** M3 Hex Nuts
* **20x** M3 Heatset Inserts (5mm diameter x 4mm length)

**Screws (Flat/Countersunk M3)**
* **20x** 25mm M3 screws
* **15x** 16mm M3 screws
* **20x** 6mm M3 screws

**Plumbing**
* **Silicone Tubing:** 3mm ID x 5mm OD (8ft+ recommended) (larger and smaller sizes can fit, up to 8mm outer diameter. but i found 3mm inner diamter to be the sweet spot between flow and accuracy)
* **5x** Mio "Water Enhancer" caps (or generic store brand equivalents. I used the Kroger/Smiths store brand in my original model for cost reasons). these bottle caps use squeeze valves that we will
  "harvest" by cutting away the extra plastic.

---

## ⚙️ Firmware Installation (MKS TinyBee)
We use **FluidNC** for motion control, allowing the Android app to talk to the board over Wi-Fi.

### 1. Flash the Board
1.  Connect your MKS TinyBee to your PC via USB (Make sure to set the jumper near the USB port to USB power during setup. This lets you configure without a dedicated power supply).
2.  Open **[installer.fluidnc.com](https://installer.fluidnc.com)** in Chrome or Edge.
3.  Click **Connect** and select your board's serial port.
4.  Select the latest firmware version.
5.  **Important:** Choose the **"Wifi"** variant.
6.  Click **Install**.

### 2. Configure the Machine
1.  Download the [`config.yaml`](config.yaml) file.
2.  In the Web Installer (or FluidNC Web UI), go to the **Config** tab.
3.  Upload the `.yaml` file.
4.  locate (and write down) the IP address (should be on the wifi page)
5.  Restart the board.

*Note: If your pumps run backwards, flip the motor plug or change the direction pin in the config.*

---

### 3.📱 The App
This repository contains the Android application source code.
1.  Build the APK from source, or download from releases.
2.  Install on your Android device.
3.  Connect to the machine's IP over Wi-Fi.
4.  Run a few calibrations. (tip, start with water to test. then move to paint after you confirmed it pumps. calibrate everythingg with paint, ***not water***, they flow differently)
5.  Start mixing.

---

## 🙌 Credits & Acknowledgements
This project wouldn't be possible without the work of others:

* **[ZeVeX](https://www.printables.com/@ZeVeX)** – For the **Compact Planetary Gear for Nema17 (4:1)** design used to drive the pumps.
* **The [FluidNC Team](http://wiki.fluidnc.com/)** – For the incredible firmware that powers the motion control.

---

## 📄 License
**Copyright (c) 2026 babooburkhardt**

This work is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International License**.

* **Attribution:** Give credit where it's due.
* **Non-Commercial:** You may not use this work for commercial purposes without my explicit permission.
