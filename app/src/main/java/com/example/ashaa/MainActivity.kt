package com.example.ashaa

import android.Manifest
import android.content.*
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingFile = mutableStateOf<String?>(null)

    // Zaroori Fix: Dialog state ko class level par rakha hai taaki onNewIntent ise update kar sake
    private var isSosDialogVisibleState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("AshaaPrefs", MODE_PRIVATE)

        // --- POPUP FIX: Background Overlay ---
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Check if opened via SOS trigger
        if (intent.getBooleanExtra("trigger_sos", false)) {
            isSosDialogVisibleState.value = true
        }

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
                    Manifest.permission.RECORD_AUDIO, // AI Model ke liye mic permission
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CALL_PHONE
                ))
                if (isLoggedIn) startSafetyService(context, c1Num, c2Num)
            }

            if (!isLoggedIn) {
                LoginScreen { u, cn1, cp1, cn2, cp2 ->
                    prefs.edit().apply {
                        putString("userName", u); putString("c1Name", cn1)
                        putString("c1Num", cp1); putString("c2Name", cn2)
                        putString("c2Num", cp2); apply()
                    }
                    name = u; c1Num = cp1; isLoggedIn = true; showWelcome = true
                    startSafetyService(context, cp1, cp2)
                }
            } else if (showWelcome) {
                WelcomePopup(name) { showWelcome = false }
            } else {
                Dashboard(
                    name, c1Name, c1Num, c2Name, c2Num,
                    { playPauseAudio(it) },
                    isPlayingFile.value,
                    onLogout = {
                        // Logout logic: Stop service + Clear data + Restart
                        context.stopService(Intent(context, SafetyService::class.java))
                        prefs.edit().clear().apply()
                        val restartIntent = Intent(context, MainActivity::class.java)
                        restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(restartIntent)
                    }
                )
            }

            // --- EMERGENCY POPUP (Always listening to State) ---
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
        if (intent.getBooleanExtra("trigger_sos", false)) {
            isSosDialogVisibleState.value = true
        }
    }

    private fun startSafetyService(context: Context, n1: String, n2: String) {
        val intent = Intent(context, SafetyService::class.java).apply {
            putExtra("c1", n1); putExtra("c2", n2)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }

    private fun playPauseAudio(file: File) {
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
    }
}

// --- UI COMPONENTS (SAB KUCH PEHLE JAISA HAI) ---

@Composable
fun Dashboard(name: String, c1n: String, c1p: String, c2n: String, c2p: String, onPlay: (File) -> Unit, playingFileName: String?, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Contacts, null) }, label = { Text("Circle") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Vault") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFFFF0F5))) {
            when(tab) {
                0 -> HomeView(name)
                1 -> ContactsView(c1n, c1p, c2n, c2p)
                2 -> VaultView(onPlay, playingFileName)
                3 -> ProfileView(name, c1p, c2p, onLogout) // Logout pass kiya
            }
        }
    }
}

@Composable
fun ProfileView(name: String, cp1: String, cp2: String, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Surface(Modifier.size(100.dp), shape = CircleShape, color = Color(0xFFFFE1E9)) {
            Icon(Icons.Default.AccountCircle, null, Modifier.padding(10.dp), tint = Color(0xFFFF4081))
        }
        Spacer(Modifier.height(15.dp))
        Text(name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Primary SOS: $cp1", color = Color.Gray)
        Text("Secondary SOS: $cp2", color = Color.Gray)

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Logout & Exit Protection", color = Color.White)
        }
    }
}

// Baaki saare functions (HomeView, VaultView, LoginScreen, WelcomePopup, SafeCheckDialog)
// aapke original code se bilkul same hain... bas WelcomePopup mein maine 👑 emoji add rakha hai.

@Composable
fun HomeView(name: String) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Surface(Modifier.size(220.dp), shape = CircleShape, color = Color(0xFFFF80AB), shadowElevation = 8.dp) {
            Box(contentAlignment = Alignment.Center) {
                Text("SOS ACTIVE\nTez Shake Karein", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SafeCheckDialog(onSafeClick: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(30) }
    LaunchedEffect(timeLeft) {
        if (timeLeft > 0) { delay(1000); timeLeft-- }
    }
    AlertDialog(
        onDismissRequest = { },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Emergency Triggered! ⚠️", color = Color(0xFFFF4081), fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SMS/Call will be sent in:", textAlign = TextAlign.Center)
                Text("$timeLeft", fontSize = 50.sp, fontWeight = FontWeight.Black, color = Color.Red)
                Text("Recording is ACTIVE.", color = Color.Gray, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onSafeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("I'M SAFE ✅", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun WelcomePopup(name: String, onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onDone() }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome ", fontSize = 20.sp, color = Color.Gray)
            Text("$name ", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        }
    }
}

@Composable
fun VaultView(onPlay: (File) -> Unit, playingFileName: String?) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(context.getExternalFilesDir(null)?.listFiles()?.toList() ?: listOf()) }
    Column(Modifier.padding(16.dp)) {
        Text("Evidence Vault 🔒", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(files) { file ->
                val isThisPlaying = playingFileName == file.name
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPlay(file) }) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (isThisPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFFFF4081))
                        Text(file.name, Modifier.weight(1f).padding(start = 10.dp), fontSize = 11.sp)
                        IconButton(onClick = { file.delete(); files = context.getExternalFilesDir(null)?.listFiles()?.toList() ?: listOf() }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Gray)
                        }
                    }
                }
            }
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
        Text("Ashaa ✨", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF4081))
        Text("Your Girly Guardian", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your Name 🌸") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(20.dp))
        Text("Emergency Contact 1", color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = cn1, onValueChange = { cn1 = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = cp1, onValueChange = { cp1 = it }, label = { Text("Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(20.dp))
        Text("Emergency Contact 2", color = Color(0xFFFF4081), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = cn2, onValueChange = { cn2 = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(value = cp2, onValueChange = { cp2 = it }, label = { Text("Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(30.dp))
        Button(onClick = { if(name.isNotBlank() && cp1.isNotBlank()) onLogin(name, cn1, cp1, cn2, cp2) },
            modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)), shape = RoundedCornerShape(16.dp)) {
            Text("Start Protection", fontSize = 18.sp)
        }
    }
}

@Composable
fun ContactsView(c1n: String, c1p: String, c2n: String, c2p: String) {
    Column(Modifier.padding(20.dp)) {
        Text("Close Contacts 💖", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4081))
        Spacer(Modifier.height(15.dp))
        ContactCard(c1n, c1p)
        Spacer(Modifier.height(10.dp))
        ContactCard(c2n, c2p)
    }
}

@Composable
fun ContactCard(name: String, phone: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
            Column(Modifier.padding(start = 15.dp)) {
                Text(if(name.isBlank()) "No Name" else name, fontWeight = FontWeight.Bold)
                Text(phone, color = Color.Gray)
            }
        }
    }
}