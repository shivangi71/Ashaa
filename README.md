**ASHAA: AI-Powered Zero-Touch Women Safety System**

ASHAA is an AI-powered proactive digital guardian designed to provide emergency assistance without any manual interaction in real-life danger situations.     
The project is specifically built for scenarios where a user may be unable to unlock their phone or call for help due to panic or physical restraint. 
         
Key Features           
AI-Based Scream Detection                     
Utilizes the YAMNet (TensorFlow Lite) audio recognition model to intelligently identify distress sounds like human screams.                           
                        
Zero-Touch Activation:                           
Automatically triggers emergency protocols without requiring any screen interaction or manual input.
             
Shake-to-SOS:    
In situations where the user cannot speak or shout, shaking the phone multiple times activates the emergency system.      

Automatic Emergency Alerts:         
Once triggered, the system initiates a 15-second safety countdown. If not cancelled, it automatically sends SMS alerts, shares live location, and places calls to trusted contacts.      
       
Evidence Recording: 
Inspired by an airplane's black box, the system automatically records background audio as evidence during the emergency.

Last Location Safety:
If the device is forcefully switched off or damaged, the system automatically sends the last known GPS coordinates to trusted contacts.
Triple Sensor Trigger: 
Pressing the phone’s side or sensor button three times instantly triggers the SOS protocol.


🛠️ Tech StackCore
Languages: Kotlin & Java.

UI Framework: Jetpack Compose.

Architecture: MVVM (Model-View-ViewModel).

Artificial Intelligence: TensorFlow Lite (YAMNet Model for audio classification).

Location Services: Geolocator API & Google Play Services.

Backend: Firebase Realtime Database & Google Firebase Services.

System Integration: Broadcast Receivers (Power-off/Screen-press) and Foreground Services.

Technical Implementation 

Foreground Service Persistence: 
The app uses high-priority Foreground Services and Wakelocks to ensure 24/7 protection and prevent the OS from killing the process during Doze Mode.

Hardware Resource Management:
Implements "Stop-and-Switch" logic to manage the microphone between the AI detection model and the MediaRecorder to prevent crashes.

Noise Reduction:
AI helps differentiate between genuine distress signals and environmental noise (music, traffic), reducing false alerts.


🚀 Future Vision

Automated Emergency Network: 
Integrating Google Places API to auto-connect with the nearest Police Stations for immediate dispatch.

Safe Haven Ecosystem: 
Providing an interactive map of verified 24/7 safe places like hospitals and pharmacies.

Wearable Synchronization: 
Porting the system to smartwatches for discreet triggers when the phone is out of reach.

Bio-Sensor Integration: 
Utilizing heartbeat and stress sensors to detect physiological changes for 100% emergency accuracy.
