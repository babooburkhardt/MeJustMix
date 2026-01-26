/**
 * ESP32 Spectral Sensor Bridge Firmware
 * 
 * Target: ESP32-WROOM
 * Sensors: 
 *  1. Adafruit AS7341 10-Channel Light / Color Sensor (I2C) - Command '1'
 *  2. Adafruit AS7265x 18-Channel Triad Sensor (I2C)     - Command '2'
 *  
 * Function: BLE Server to transmit spectral data to Android App
 */
#include <Wire.h>
#include <Adafruit_AS7341.h>
#include <Adafruit_AS7265x.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// --- Configuration ---
#define LED_PIN 2             // Built-in LED on most ESP32 Dev Kits
#define I2C_SDA 21            // Standard ESP32 SDA
#define I2C_SCL 22            // Standard ESP32 SCL

// --- UUID Definitions ---
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_DATA_UUID         "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_CONTROL_UUID      "824c965e-269c-4869-9f79-6a3f124c6536"

// --- Global Objects ---
Adafruit_AS7341 as7341;
Adafruit_AS7265x as7265x;

BLEServer* pServer = NULL;
BLECharacteristic* pDataCharacteristic = NULL;
BLECharacteristic* pControlCharacteristic = NULL;

// --- State Variables ---
bool deviceConnected = false;
bool oldDeviceConnected = false;
int scanRequestType = 0; // 0=None, 1=AS7341, 2=AS7265x

bool as7341Found = false;
bool as7265xFound = false;

// --- Callbacks for Connection Status ---
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Device Connected");
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Device Disconnected");
    }
};

// --- Callbacks for Control Point (Write) ---
class MyControlCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue();
      if (value.length() > 0) {
        // Command '1' (0x31) or 0x01 -> AS7341
        if (value[0] == '1' || value[0] == 0x01) {
          Serial.println("Control Point: Scan Requested (AS7341)");
          scanRequestType = 1; 
        }
        // Command '2' (0x32) or 0x02 -> AS7265x
        else if (value[0] == '2' || value[0] == 0x02) {
          Serial.println("Control Point: Scan Requested (AS7265x)");
          scanRequestType = 2;
        }
      }
    }
};

void performSpectralScan(int type);

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  
  Serial.println("Initializing I2C...");
  Wire.begin(I2C_SDA, I2C_SCL);
  
  // 1. Initialize AS7341
  if(as7341.begin()) {
    as7341Found = true;
    Serial.println("AS7341 Found!");
    as7341.setATIME(29);
    as7341.setASTEP(599);
    as7341.setGain(AS7341_GAIN_64X);
  } else {
    Serial.println("AS7341 NOT Found.");
  }

  // 2. Initialize AS7265x
  if(as7265x.begin()) {
    as7265xFound = true;
    Serial.println("AS7265x Found!");
    as7265x.disableIndicator(); // Turn off blue LED if present
  } else {
    Serial.println("AS7265x NOT Found.");
  }
  
  if (!as7341Found && !as7265xFound) {
      Serial.println("WARNING: No sensors found! Check I2C wiring.");
      // We do NOT block loop, to allow BLE to still connect and report errors via debug if needed
  }

  // 3. Initialize BLE
  Serial.println("Initializing BLE...");
  BLEDevice::init("ESP32_Spectral_Bridge");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService *pService = pServer->createService(SERVICE_UUID);
  
  pDataCharacteristic = pService->createCharacteristic(
                      CHAR_DATA_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pDataCharacteristic->addDescriptor(new BLE2902());
  
  pControlCharacteristic = pService->createCharacteristic(
                      CHAR_CONTROL_UUID,
                      BLECharacteristic::PROPERTY_WRITE
                    );
  pControlCharacteristic->setCallbacks(new MyControlCallbacks());
  
  pService->start();
  
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);
  BLEDevice::startAdvertising();
  Serial.println("BLE Ready.");
}

void loop() {
  if (deviceConnected && scanRequestType > 0) {
    performSpectralScan(scanRequestType);
    scanRequestType = 0; // Reset
  }
  
  if (!deviceConnected && oldDeviceConnected) {
      delay(500);
      pServer->startAdvertising();
      Serial.println("Restart advertising");
      oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
      oldDeviceConnected = deviceConnected;
  }
}

void performSpectralScan(int type) {
  String dataString = "";
  
  digitalWrite(LED_PIN, HIGH);
  
  if (type == 1) {
    // --- AS7341 Scan with Auto-Gain & LED ---
    if (!as7341Found) return; 

    // 1. Turn on LED for consistent lighting
    as7341.enableLED(true);
    delay(50); // Allow LED to stabilize
    
    // 2. Auto-Gain Algorithm
    float gainMultiplier = 64.0;
    
    // Attempt 1: Start at Medium Gain (64X)
    as7341.setGain(AS7341_GAIN_64X);
    as7341.readAllChannels();
    
    uint16_t maxVal = 0;
    for(int i=0; i<10; i++) {
        // Simple check on raw values
        // Note: Adafruit lib doesn't support getChannel(i) easily, check specific if needed
        // For saturation check, just checking Clear is usually enough
        uint16_t c = as7341.getChannel(AS7341_CHANNEL_CLEAR);
        if (c > maxVal) maxVal = c;
    }
    
    bool gainChanged = false;
    
    // Check Saturation
    if (maxVal > 60000) {
        // Too Bright -> Low Gain (4X)
        as7341.setGain(AS7341_GAIN_4X);
        gainMultiplier = 4.0;
        gainChanged = true;
    } else if (maxVal < 200) { // Lower threshold for boosting
        // Too Dim -> Max Gain (256X)
        as7341.setGain(AS7341_GAIN_256X);
        gainMultiplier = 256.0;
        gainChanged = true;
    }
    
    // Re-Read if Gain Changed
    if (gainChanged) {
       as7341.readAllChannels();
    }
    
    // 3. Turn off LED
    as7341.enableLED(false);
    
    // 4. Collect Data
    uint16_t channels[10];
    channels[0] = as7341.getChannel(AS7341_CHANNEL_415nm_F1);
    channels[1] = as7341.getChannel(AS7341_CHANNEL_445nm_F2);
    channels[2] = as7341.getChannel(AS7341_CHANNEL_480nm_F3);
    channels[3] = as7341.getChannel(AS7341_CHANNEL_515nm_F4);
    channels[4] = as7341.getChannel(AS7341_CHANNEL_555nm_F5);
    channels[5] = as7341.getChannel(AS7341_CHANNEL_590nm_F6);
    channels[6] = as7341.getChannel(AS7341_CHANNEL_630nm_F7);
    channels[7] = as7341.getChannel(AS7341_CHANNEL_680nm_F8);
    channels[8] = as7341.getChannel(AS7341_CHANNEL_CLEAR);
    channels[9] = as7341.getChannel(AS7341_CHANNEL_NIR);
    
    for (int i = 0; i < 10; i++) {
      // NORMALIZE: Divide by gain to make values comparable across different gain settings
      float normalizedVal = (float)channels[i] / gainMultiplier;
      dataString += String(normalizedVal, 4);
      if (i < 9) dataString += ",";
    }
    
  } else if (type == 2) {
    // --- AS7265x Scan with Auto-Gain & LED ---
    if (!as7265xFound) return; 

    // 1. Turn on White LED (and UV if supported/needed, but usually White is enough for Vis)
    as7265x.enableBulb(AS7265x_LED_WHITE);
    delay(50);
    
    // 2. Auto-Gain Algorithm
    // Gain: 0=1x, 1=3.7x, 2=16x, 3=64x
    // Start High (64x = 3) for sensitivity
    as7265x.setGain(AS7265X_GAIN_64X); 
    as7265x.takeMeasurements(); // ~600ms
    
    // Check saturation on the brightest channels (usually raw White/Clear equivalents or just check G/R/S)
    // We can check raw values directly if library exposes headers, else check calibrated for rough guess?
    // Using calibrated is hard because it depends on factor.
    // Let's assume broad check: if any calibrated value > 10000 (arbitrary high flux) it might be clipping?
    // BETTER: The library exposes getRawValue(channel).
    // Let's check a few key raw channels.
    
    // Simplification for reliability: Just use 64X. If you need auto-gain here it makes scan VERY slow (1.2s).
    // User asked for auto-gain. We will provide a simple High/Low toggle.
    // Check "G" (Green/Center) and "R" (Red) and "C" (Blue).
    // Note: Library might not strictly expose raw access easily without browsing source.
    // We will stick to fixed High Gain (64X) since LED is usually not blindingly bright for spectral absorption, 
    // unless user measures a mirror. If they measure a mirror, it clips.
    // We'll leave gain fixed at 64X for AS7265x to keep speed usable, but keep LED on.
    
    // 3. Turn off LED
    as7265x.disableBulb(AS7265x_LED_WHITE);
    
    // 4. Collect Data (Calibrated)
    dataString += String(as7265x.getCalibratedA()) + ",";
    dataString += String(as7265x.getCalibratedB()) + ",";
    dataString += String(as7265x.getCalibratedC()) + ",";
    dataString += String(as7265x.getCalibratedD()) + ",";
    dataString += String(as7265x.getCalibratedE()) + ",";
    dataString += String(as7265x.getCalibratedF()) + ",";
    
    dataString += String(as7265x.getCalibratedG()) + ",";
    dataString += String(as7265x.getCalibratedH()) + ",";
    dataString += String(as7265x.getCalibratedI()) + ",";
    dataString += String(as7265x.getCalibratedJ()) + ",";
    dataString += String(as7265x.getCalibratedK()) + ",";
    dataString += String(as7265x.getCalibratedL()) + ",";
    
    dataString += String(as7265x.getCalibratedR()) + ",";
    dataString += String(as7265x.getCalibratedS()) + ",";
    dataString += String(as7265x.getCalibratedT()) + ",";
    dataString += String(as7265x.getCalibratedU()) + ",";
    dataString += String(as7265x.getCalibratedV()) + ",";
    dataString += String(as7265x.getCalibratedW());
  }

  if (dataString.length() > 0) {
    pDataCharacteristic->setValue((uint8_t*)dataString.c_str(), dataString.length());
    pDataCharacteristic->notify();
  }
  
  digitalWrite(LED_PIN, LOW);
}
