
ASHAA: AI-Powered Zero-Touch Women Safety System

ASHAA ek proactive digital guardian hai jo emergency situations mein bina kisi manual interaction ke madad pahunchata hai. Yeh system khaas karke un halaton ke liye design kiya gaya hai jahan victim panic ya physical restraint ki wajah se phone unlock nahi kar sakti.
+3

 Key Features

AI-Based Scream Detection: YAMNet (TensorFlow Lite) model ka use karke yeh system cheekh (screams) aur distress signals ko pehchanta hai.
+2


Zero-Touch Activation: Bina phone chhue emergency alerts trigger hote hain.
+1


Shake-to-SOS: Agar user chillane ki halat mein nahi hai, toh phone ko multiple times shake karne se system activate ho jata hai.
+1


Evidence Recording: Emergency trigger hote hi background audio record hona shuru ho jata hai (Airplane Black Box system se inspired).
+1


Automatic Alerts: 15-second ke countdown ke baad trusted contacts ko SMS, live location aur automatic calls chale jaate hain.
+3


Last Location Safety: Phone switch off ya damage hone se pehle last known location bhej di jaati hai.
+2


Triple Sensor Trigger: Side ya sensor button ko 3 baar dabane se silently protocol start ho jata hai.
+1

🛠️ Tech Stack

Language: Kotlin & Java 


UI Framework: Jetpack Compose 


Architecture: MVVM (Model-View-ViewModel) 


Machine Learning: TensorFlow Lite (YAMNet Model) 


Database & Services: Firebase Realtime Database, Google Play Services 
+1


Hardware Integration: Accelerometer (Sensors), Broadcast Receivers, Foreground Services 
+1

🏗️ Architecture & Logic

Foreground Service: 24/7 protection ke liye system background mein hamesha active rehta hai.
+1


Smart Switch Logic: AI detection aur evidence recording ke beech microphone resource conflict ko handle karne ke liye "Stop-and-Switch" logic use kiya gaya hai.


Battery Optimization Bypass: Android Doze Mode ke restrictions ko Wakelocks ke zariye handle kiya gaya hai.
+1

🚀 Future Vision

Police Station Integration: Nearest police station se auto-connect hona.


Safe Haven Ecosystem: Verified safe places ka interactive map.


Wearable Support: Smartwatches par porting aur bio-sensors (heartbeat/stress) ka integration.




"Ashaa is more than just code; it’s a promise of security." 
+1
