/**
 * ESP32 Spectral Sensor Bridge Firmware
 * 
 * Target: ESP32-WROOM
 * Sensor: Adafruit AS7341 10-Channel Light / Color Sensor (I2C)
 * Function: BLE Server to transmit spectral data to Android App
 * 
 * Dependencies:
 * 1. Adafruit AS7341 Library (Install via Library Manager)
 * 2. ESP32 BLE Arduino (Built-in to ESP32 Board Package)
 */
#include <Wire.h>
#include <Adafruit_AS7341.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
// --- Configuration ---
#define LED_PIN 2             // Built-in LED on most ESP32 Dev Kits
#define I2C_SDA 21            // Standard ESP32 SDA
#define I2C_SCL 22            // Standard ESP32 SCL
// --- UUID Definitions ---
// Generated for this project. See Integration Manual.
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_DATA_UUID         "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_CONTROL_UUID      "824c965e-269c-4869-9f79-6a3f124c6536"
// --- Global Objects ---
Adafruit_AS7341 as7341;
BLEServer* pServer = NULL;
BLECharacteristic* pDataCharacteristic = NULL;
BLECharacteristic* pControlCharacteristic = NULL;
// --- State Variables ---
bool deviceConnected = false;
bool oldDeviceConnected = false;
bool scanRequested = false;
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
        // Check if value is "1" (0x31) or byte 0x01
        if (value[0] == '1' || value[0] == 0x01) {
          Serial.println("Control Point: Scan Requested");
          scanRequested = true; // Set flag to process in main loop
        }
      }
    }
};
void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  
  // 1. Initialize I2C and Sensor
  Serial.println("Initializing AS7341 Sensor...");
  Wire.begin(I2C_SDA, I2C_SCL);
  
  if(!as7341.begin()) {
    Serial.println("AS7341 not found! Check wiring.");
    // Blink fast forever to indicate error
    while(1) {
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
      delay(100);
    }
  }
  Serial.println("Sensor Found!");
  
  // Configure Sensor
  // Optimization: Total Scan Time ~100ms (10Hz)
  // Step Time = (ATIME + 1) * (ASTEP + 1) * 2.78µs
  // (29 + 1) * (599 + 1) * 2.78 = 30 * 600 * 2.78 ≈ 50,040µs (50ms)
  // Total (x2 steps) = 100ms
  // 50ms is a multiple of both 16.6ms (60Hz) and 20ms (50Hz), canceling flicker noise.
  
  as7341.setATIME(29);
  as7341.setASTEP(599);
  as7341.setGain(AS7341_GAIN_64X); // Reduced from 256X to prevent saturation
  
  // Turn off onboard LED initially (if wired to LED pin on breakout)
  // as7341.enableLED(false); // Note: Breakout board specific

  // 2. Initialize BLE
  Serial.println("Initializing BLE...");
  BLEDevice::init("ESP32_Spectral_Bridge");
  // Create Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  // Create Service
  BLEService *pService = pServer->createService(SERVICE_UUID);
  // Create Data Characteristic (Read/Notify)
  pDataCharacteristic = pService->createCharacteristic(
                      CHAR_DATA_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  // Add CCCD for notifications
  pDataCharacteristic->addDescriptor(new BLE2902());
  // Create Control Characteristic (Write)
  pControlCharacteristic = pService->createCharacteristic(
                      CHAR_CONTROL_UUID,
                      BLECharacteristic::PROPERTY_WRITE
                    );
  pControlCharacteristic->setCallbacks(new MyControlCallbacks());
  // Start Service
  pService->start();
  // Start Advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
  BLEDevice::startAdvertising();
  Serial.println("BLE Ready over, waiting for connections...");
}
void loop() {
  // logic to handle scan request
  if (deviceConnected && scanRequested) {
    scanRequested = false; // Reset flag
    performSpectralScan();
  }
  // logic to handle disconnection (auto-restart advertising)
  if (!deviceConnected && oldDeviceConnected) {
      delay(500); // give the bluetooth stack the chance to get things ready
      pServer->startAdvertising(); // restart advertising
      Serial.println("Restart advertising");
      oldDeviceConnected = deviceConnected;
  }
  // logic to handle connection
  if (deviceConnected && !oldDeviceConnected) {
      // do stuff here on connecting
      oldDeviceConnected = deviceConnected;
  }
}
/**
 * Performs a sensor read, formats the data, and notifies the client.
 */
void performSpectralScan() {
  Serial.println("Taking Reading...");
  
  // Visual Feedback: LED ON
  digitalWrite(LED_PIN, HIGH);
  
  // Take measurements
  if (!as7341.readAllChannels()){
    Serial.println("Error reading all channels!");
    return;
  }
  
  // Collect 10 channels
  // F1..F8, Clear, NIR
  
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
  
  // Create CSV String
  String dataString = "";
  for (int i = 0; i < 10; i++) {
    dataString += String(channels[i]);
    if (i < 9) dataString += ",";
  }
  Serial.print("Data: ");
  Serial.println(dataString);
  // Send Notification
  // Note: setValue copies the data.
  pDataCharacteristic->setValue((uint8_t*)dataString.c_str(), dataString.length());
  pDataCharacteristic->notify();
  
  // Visual Feedback: LED OFF
  digitalWrite(LED_PIN, LOW);
  Serial.println("Notify Sent.");
}
