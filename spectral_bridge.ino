/**
 * ESP32 Spectral Sensor Bridge Firmware
 * 
 * Target: ESP32-WROOM
 * Sensor: AS7265x Triad (I2C)
 * Function: BLE Server to transmit spectral data to Android App
 * 
 * Dependencies:
 * 1. Adafruit AS7265x Library (Install via Library Manager)
 * 2. ESP32 BLE Arduino (Built-in to ESP32 Board Package)
 */
#include <Wire.h>
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
// Generated for this project. See Integration Manual.
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_DATA_UUID         "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHAR_CONTROL_UUID      "824c965e-269c-4869-9f79-6a3f124c6536"
// --- Global Objects ---
Adafruit_AS7265x sensor;
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
  Serial.println("Initializing AS7265x Sensor...");
  Wire.begin(I2C_SDA, I2C_SCL);
  
  if(!sensor.begin()) {
    Serial.println("AS7265x not found! Check wiring.");
    // Blink fast forever to indicate error
    while(1) {
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
      delay(100);
    }
  }
  Serial.println("Sensor Found!");
  
  // Turn off sensor bulb initially if on
  sensor.disableIndicator();
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
  
  // Take measurements (one-shot)
  sensor.takeMeasurements(); 
  // Note: takeMeasurements() is blocking in the standard library.
  // If non-blocking is required later, we would use state machine with .dataReady()
  
  // Collect 18 channels
  // Order: 
  // AS72651: R, S, T, U, V, W 
  // AS72652: G, H, I, J, K, L 
  // AS72653: A, B, C, D, E, F
  
  float channels[18];
  
  channels[0] = sensor.getCalibratedR();
  channels[1] = sensor.getCalibratedS();
  channels[2] = sensor.getCalibratedT();
  channels[3] = sensor.getCalibratedU();
  channels[4] = sensor.getCalibratedV();
  channels[5] = sensor.getCalibratedW();
  channels[6] = sensor.getCalibratedG();
  channels[7] = sensor.getCalibratedH();
  channels[8] = sensor.getCalibratedI();
  channels[9] = sensor.getCalibratedJ();
  channels[10] = sensor.getCalibratedK();
  channels[11] = sensor.getCalibratedL();
  channels[12] = sensor.getCalibratedA();
  channels[13] = sensor.getCalibratedB();
  channels[14] = sensor.getCalibratedC();
  channels[15] = sensor.getCalibratedD();
  channels[16] = sensor.getCalibratedE();
  channels[17] = sensor.getCalibratedF();
  // Create CSV String
  // Using String class for ease of concatenation, though std::string or char buf is faster.
  // For 18 floats, typically 6 chars per float + comma ~= 126 chars.
  // We reserve enough space.
  String dataString = "";
  for (int i = 0; i < 18; i++) {
    dataString += String(channels[i], 2); // 2 decimal places
    if (i < 17) dataString += ",";
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
