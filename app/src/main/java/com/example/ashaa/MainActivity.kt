package com.example.ashaa

import android.Manifest
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private val isPlayingFile = mutableStateOf<String?>(null)
    private val isSosDialogVisibleState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("AshaaPrefs", MODE_PRIVATE)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        if (intent.getBooleanExtra("trigger_sos", false)) { isSosDialogVisibleState.value = true }

        setContent {
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

            var name by remember { mutableStateOf(prefs.getString("userName", "") ?: "") }
            var c1Name by remember { mutableStateOf(prefs.getString("c1Name", "") ?: "") }
            var c1Num by remember { mutableStateOf(prefs.getString("c1Num", "") ?: "") }
            var c2Name by remember { mutableStateOf(prefs.getString("c2Name", "") ?: "") }
            var c2Num by remember { mutableStateOf(prefs.getString("c2Num", "") ?: "") }

            var isLoggedIn by remember { mutableStateOf(name.isNotBlank() && c1Num.isNotBlank()) }
            var showWelcome by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                launcher.launch(arrayOf(
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CALL_PHONE,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.VIBRATE
                ))
                if (isLoggedIn) startSafetyService(context, c1Num, c2Num)
            }

            if (!isLoggedIn) {
                LoginScreen { u, cn1, cp1, cn2, cp2 ->
                    prefs.edit().apply {
                        putString("userName", u); putString("c1Name", cn1); putString("c1Num", cp1)
                        putString("c2Name", cn2); putString("c2Num", cp2); apply()
                    }
                    name = u; c1Name = cn1; c1Num = cp1; c2Name = cn2; c2Num = cp2
                    isLoggedIn = true; showWelcome = true
                    startSafetyService(context, cp1, cp2)
                }
            } else if (showWelcome) {
                WelcomePopup(name) { showWelcome = false }
            } else {
                Dashboard(name, c1Name, c1Num, c2Name, c2Num, { playPauseAudio(it) }, isPlayingFile.value,
                    onLogout = {
                        context.stopService(Intent(context, SafetyService::class.java))
                        prefs.edit().clear().apply()
                        val restartIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(restartIntent)
                    }
                )
            }

            if (isSosDialogVisibleState.value) {
                SafeCheckDialog(onSafeClick = {
                    isSosDialogVisibleState.value = false
                    val cancelIntent = Intent(context, SafetyService::class.java).apply { action = "STOP_SOS" }
                    context.startService(cancelIntent)
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("trigger_sos", false)) { isSosDialogVisibleState.value = true }
    }

    private fun startSafetyService(context: Context, n1: String, n2: String) {
        val intent = Intent(context, SafetyService::class.java).apply { putExtra("c1", n1); putExtra("c2", n2) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }

    private fun playPauseAudio(file: File) {
        try {
            if (isPlayingFile.value == file.name) {
                mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null; isPlayingFile.value = null
            } else {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath); prepare(); start()
                    isPlayingFile.value = file.name
                    setOnCompletionListener { isPlayingFile.value = null }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

@Composable
fun Dashboard(name: String, c1n: String, c1p: String, c2n: String, c2p: String, onPlay: (File) -> Unit, playingFileName: String?, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Home", fontSize = 10.sp) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Contacts, "Contacts") }, label = { Text("Contacts", fontSize = 10.sp) })
               // NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.LocationOn, "Track") }, label = { Text("Trac", fontSize = 10.sp) })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Security, "Files") }, label = { Text("Files", fontSize = 10.sp) })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 3}, icon = { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile", fontSize = 10.sp) })
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFFFF0F5))) {
            when(tab) {
                0 -> HomeView(name)
                1 -> ContactsScreen(c1n, c1p, c2n, c2p)
                2 -> AIShieldView(onPlay, playingFileName)
                3 -> ProfileView(name, c1p, c2p, onLogout)
            }
        }
    }
}

@Composable
fun ContactsScreen(c1n: String, c1p: String, c2n: String, c2p: String) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section
        Text("EMERGENCY CIRCLE", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4081))
        Text("Your 24/7 Support System", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(30.dp))

        // SOS Main Button with Shadow
        Surface(
            Modifier
                .size(200.dp)
                .clickable {
                    val intent = Intent(context, SafetyService::class.java).apply { action = "TRIGGER_SOS_NOW" }
                    context.startService(intent)
                },
            shape = CircleShape,
            color = Color.Red,
            shadowElevation = 20.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Text("HELP", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PRESS IN DANGER", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // Section Title
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(Icons.Default.Group, null, tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("PRIMARY GUARDIANS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(Modifier.height(12.dp))

        // Updated Contact Cards
        ContactCardNew(if(c1n.isBlank()) "Guardian 1" else c1n, c1p)
        ContactCardNew(if(c2n.isBlank()) "Guardian 2" else c2n, c2p)

        Spacer(Modifier.height(24.dp))

        // Security Badge Note
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(10.dp))
                Text("Your contacts are end-to-end encrypted.", fontSize = 11.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun ContactCardNew(name: String, phone: String) {
    val context = LocalContext.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture Circle (Avatar)
            Box(
                Modifier
                    .size(50.dp)
                    .background(Color(0xFFFFE1E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = Color(0xFFFF4081), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Column(Modifier.padding(start = 15.dp).weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Text(phone, fontSize = 13.sp, color = Color.Gray)
            }

            // Quick Call Button
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(intent)
                },
                modifier = Modifier.background(Color(0xFFF5F5F5), CircleShape)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF4CAF50))
            }
        }
    }
}


// Ye annotation zaroori hai ModalBottomSheet ke liye
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(name: String) {
    // State to track which feature is being explained
    var selectedFeature by remember { mutableStateOf<String?>(null) }

    // ERROR FIX: Is line ko pura kiya gaya hai
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Comforting Line (No emojis, Professional)
        Text("Your safety is our priority $name", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF4081))
        Text("Always active and ready to protect you", fontSize = 14.sp, color = Color.Gray)

        Spacer(Modifier.height(25.dp))

        // Protection Status Card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("SHIELD ACTIVE", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), letterSpacing = 2.sp)
            }
        }

        Spacer(Modifier.height(30.dp))

        Text("EXPLORE SAFETY FEATURES", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
        Text("Click on any card to learn more", fontSize = 11.sp, color = Color.LightGray)
        Spacer(Modifier.height(12.dp))

        // Interactive Features List
        FeatureInfoCard("Voice Guard", "Detects high-pitched sounds & screams.", Icons.Default.Mic) {
            selectedFeature = "Voice Guard"; showSheet = true
        }
        FeatureInfoCard("Smart Panic", "Instant alert via physical buttons.", Icons.Default.FlashOn) {
            selectedFeature = "Smart Panic"; showSheet = true
        }
        FeatureInfoCard("Motion SOS", "Triggers help through rapid movement.", Icons.Default.EdgesensorHigh) {
            selectedFeature = "Motion SOS"; showSheet = true
        }
        FeatureInfoCard("Silent Sentinel", "Works even if the device shuts down.", Icons.Default.PhonelinkOff) {
            selectedFeature = "Silent Sentinel"; showSheet = true
        }

        Spacer(Modifier.height(30.dp))
    }

    // Step-by-Step Explanation Sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            FeatureDetailContent(selectedFeature) { showSheet = false }
        }
    }
}

@Composable
fun FeatureInfoCard(title: String, desc: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(Color(0xFFFFE1E9), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFFFF4081), modifier = Modifier.size(24.dp))
        }
        Column(Modifier.padding(start = 15.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
            Text(desc, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}

@Composable
fun FeatureDetailContent(featureName: String?, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 30.dp)) {
        Text(text = featureName ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        Spacer(Modifier.height(20.dp))

        val steps = when(featureName) {
            "Voice Guard" -> listOf(
                "Step 1: The app uses AI to listen for your screams or shouting when you are in DANGER.",
                "Step 2: If danger is detected, a 15-second timer starts.",
                "Step 3: After the timer, a call is placed and SMS is sent automatically with real time location."
            )
            "Smart Panic" -> listOf(
                "Step 1: Press the Power Button three times quickly.",
                "Step 2: The phone vibrates to confirm the command.",
                "Step 3: Instant SOS is triggered without opening the app.",
                "Step 4:A call is placed and SMS is sent automatically with real time location."
            )
            "Motion SOS" -> listOf(
                "Step 1: Shake your phone forcefully 4-5 times.",
                "Step 2: App detects the rapid movement as a distress signal.",
                "Step 3: Audio recording begins and alerts are sent to guardians."
            )
            "Silent Sentinel" -> listOf(
                "Step 1: If someone tries to switch off your phone in danger... OR someone BROKE your phone",
                "Step 2: The app detects the shutdown command.",
                "Step 3: It instantly sends your last known location to your primary contact."
            )
            else -> listOf()
        }

        steps.forEach { step ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9FA))
            ) {
                Text(step, Modifier.padding(16.dp), fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("I Understand")
        }
    }
}

@Composable
fun FeatureInfoCard(title: String, desc: String, icon: ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(45.dp).background(Color(0xFFFFE1E9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFFFF4081), modifier = Modifier.size(24.dp))
        }
        Column(Modifier.padding(start = 15.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Text(desc, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
        }
    }
}
@Composable
fun QuickActionIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(60.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
            Icon(icon, null, Modifier.padding(15.dp), tint = Color(0xFFFF4081))
        }
        Text(label, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}



@Composable
fun AIShieldView(onPlay: (File) -> Unit, playingFileName: String?) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(context.getExternalFilesDir(null)?.listFiles()?.filter { it.name.endsWith(".mp3") } ?: listOf()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("FILES", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(files) { file ->
                val isThisPlaying = playingFileName == file.name
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPlay(file) }) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (isThisPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFFFF4081))
                        Text(file.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 11.sp)
                        IconButton(onClick = { file.delete(); files = context.getExternalFilesDir(null)?.listFiles()?.filter { it.name.endsWith(".mp3") } ?: listOf() }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileView(name: String, cp1: String, cp2: String, onLogout: () -> Unit) {
    // State to show/hide logout confirmation dialog
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            Modifier.size(100.dp),
            shape = CircleShape,
            color = Color(0xFFFFE1E9)
        ) {
            Icon(Icons.Default.AccountCircle, null, Modifier.padding(10.dp), tint = Color(0xFFFF4081))
        }

        Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Verified User", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(30.dp))

        // Profile Info Items
        ProfileMenuItem(Icons.Default.Phone, "Primary SOS", cp1)
        ProfileMenuItem(Icons.Default.Shield, "AI Detection", "Active")

        Spacer(Modifier.height(40.dp))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true }, // Popup show karega
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Logout from Ashaa", fontWeight = FontWeight.Bold)
        }
    }

    // --- Logout Confirmation Dialog ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false }, // Bahar click karne par band
            containerColor = Color.White,
            title = {
                Text("Confirm Logout", fontWeight = FontWeight.Bold, color = Color.Black)
            },
            text = {
                Text("Are you sure you want to exit? Your active protection will be stopped.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout() // Agar Yes kiya toh logout function call hoga
                    }
                ) {
                    Text("YES", color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false } // No par sirf popup band hoga
                ) {
                    Text("NO", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray)
        Column(Modifier.padding(start = 15.dp)) {
            Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cn1 by remember { mutableStateOf("") }
    var cp1 by remember { mutableStateOf("") }
    var cn2 by remember { mutableStateOf("") }
    var cp2 by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(30.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Ashaa", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4081))
        Text("Your Girly Guardian", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Enter Your Name ") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(15.dp))
        Text("Emergency Circle", color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = cn1, onValueChange = { cn1 = it }, label = { Text("Contact 1 Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = cp1, onValueChange = { cp1 = it }, label = { Text("Contact 1 Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = cn2, onValueChange = { cn2 = it }, label = { Text("Contact 2 Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = cp2, onValueChange = { cp2 = it }, label = { Text("Contact 2 Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(30.dp))
        Button(onClick = { if(name.isNotBlank() && cp1.isNotBlank()) onLogin(name, cn1, cp1, cn2, cp2) },
            modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)), shape = RoundedCornerShape(16.dp)) {
            Text("Start Protection", fontSize = 18.sp)
        }
    }
}

@Composable
fun WelcomePopup(name: String, onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onDone() }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome $name ", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        }
    }
}

@Composable
fun SafeCheckDialog(onSafeClick: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(15) }
    LaunchedEffect(timeLeft) { if (timeLeft > 0) { delay(1000); timeLeft-- } }
    AlertDialog(
        onDismissRequest = { },
        containerColor = Color.White,
        title = { Text("Emergency Triggered! ⚠️", color = Color.Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Alerting contacts in: $timeLeft", fontSize = 20.sp)
                Text("Recording is active.", color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onSafeClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth()) {
                Text("I'M SAFE ✅")
            }
        }
    )
}