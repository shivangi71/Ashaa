# ASHAA: Empowering Safety through Intelligent Automation
  
 A smart personal safety application that speaks for you when you can't. 
   
 Ashaa is designed to bridge the gap in emergency response. Instead of relying on manual SOS triggers, it uses real-time sensor data and voice recognition to detect distress automatically.                            
 
   
 Whether it’s a high-frequency scream or a specific shake pattern, Ashaa app ensures help is notified instantly.  
  
**4 Key Features:**
  
 **Voice Guard (AI):** Uses a TensorFlow Lite (YAMNet) model to classify audio in real-time. It recognizes high-pitched screams and shouts with a 0.10f sensitivity threshold.          
   
**Motion SOS:** Integrated Accelerometer monitoring detects specific "Shake-to-SOS" patterns , ideal for situations where the screen is inaccessible.      
  
**Smart Panic:** A BroadcastReceiver monitors physical button patterns. Pressing the side Power Button 3 times instantly triggers the SOS sequence. 

 **Silent Sentinel:** A unique fail-safe that triggers an alert if the device is forced to Shutdown, sending the last known location before the power cuts off.

**Emergency Network:** Automatically maps the nearest Police Stations and Safe Havens (Hospitals/Pharmacies) via Google Places API.

 **Evidence Logging:** Automatically records background audio and saves it as an encrypted .mp3 for legal evidence. 

**Tech Stack:**
   **Environment:** Andoid Studio
   
  **Language:** Kotlin / JAVA
   
  **UI Framework:** Jetpack Compose (Modern Declarative UI)
       
   **AI/ML:** TensorFlow Lite (YAMNet Audio Classification: Recognize 521+ voices) 
   
  **Backend:** Firebase Realtime Database 
   
   **Architecture:** MVVM (Model-View-ViewModel)
   
   **Android Core:** Foreground Services, Broadcast Receivers, System Overlays.

 **How It Works (Architecture):**
 
   **Monitoring:** The app runs a persistent Foreground Service that listens to the Mic, Accelerometer, and System Intents.
   
   **Detection:**  If a scream or shake is detected, the Safe-Check Dialog (System Overlay) pops up.
   
   **Verification:** A 15-second countdown begins. If the user doesn't press "I'M SAFE," the SOS initiates.
   
   **Action:** The app fetches GPS Coordinates, starts Audio Recording, sends an SOS SMS to contacts, and places an Automated Call.
