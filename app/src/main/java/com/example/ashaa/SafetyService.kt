package com.example.ashaa

import android.Manifest
import android.app.*
import android.content.*
import android.hardware.*
import android.location.LocationManager
import android.media.*
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File
import kotlin.math.sqrt

class SafetyService : Service(), SensorEventListener {
    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private var num1: String? = ""
    private var num2: String? = ""
    private var secretCode: String? = ""
    private var mediaRecorder: MediaRecorder? = null

    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var classificationHandler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var isSOSCountingDown = false
    private var countDownTimer: CountDownTimer? = null

    private var screenPressCount = 0
    private var lastScreenPressTime: Long = 0

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SHUTDOWN -> sendShutdownAlert()
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON -> {
                    val now = System.currentTimeMillis()
                    if (now - lastScreenPressTime > 1500) { screenPressCount = 0 }
                    screenPressCount++
                    lastScreenPressTime = now
                    if (screenPressCount >= 3) {
                        screenPressCount = 0
                        triggerInstantSOS()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("AshaaPrefs", MODE_PRIVATE)
        num1 = intent?.getStringExtra("c1") ?: prefs.getString("c1Num", "")
        num2 = intent?.getStringExtra("c2") ?: prefs.getString("c2Num", "")

        when (intent?.action) {
            "STOP_SOS" -> stopSOSCountdown()
            "TRIGGER_SOS_NOW" -> initiateSOSProcess()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("AshaaPrefs", MODE_PRIVATE)
        secretCode = prefs.getString("secretCode", "")?.lowercase()
        num1 = prefs.getString("c1Num", "")
        num2 = prefs.getString("c2Num", "")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(eventReceiver, filter)

        setupNotification()

        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        Handler(Looper.getMainLooper()).postDelayed({ setupSmartAI() }, 2000)
    }

    private fun setupSmartAI() {
        try {
            // Sensitivity increased to 0.10f for weaker voices
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setScoreThreshold(0.10f)
                .build()
            audioClassifier = AudioClassifier.createFromFileAndOptions(this, "yamnet.tflite", options)
            audioRecord = audioClassifier?.createAudioRecord()
            startSmartScreamDetection()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startSmartScreamDetection() {
        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        try {
            audioRecord?.startRecording()
            handlerThread = HandlerThread("AudioAIThread").apply { start() }
            classificationHandler = Handler(handlerThread!!.looper)

            val runClassifier = object : Runnable {
                override fun run() {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    // Sirf tab detect karega jab phone khud koi awaaz (recording) nahi baja raha
                    if (audioClassifier != null && !isSOSCountingDown && !am.isMusicActive) {
                        try {
                            val tensorAudio = audioClassifier!!.createInputTensorAudio()
                            tensorAudio.load(audioRecord)
                            val results = audioClassifier!!.classify(tensorAudio)

                            val dangerLabels = listOf("Screaming", "Shouting", "Yell", "Screech", "Crying, sobbing")
                            val detected = results.flatMap { it.categories }.any {
                                dangerLabels.contains(it.label) && it.score > 0.10f
                            }

                            if (detected) {
                                Handler(Looper.getMainLooper()).post { initiateSOSProcess() }
                            }
                        } catch (e: Exception) {}
                    }
                    classificationHandler?.postDelayed(this, 500) // Fast 0.5s check
                }
            }
            classificationHandler?.post(runClassifier)
        } catch (e: Exception) {}
    }

    private fun initiateSOSProcess() {
        if (isSOSCountingDown) return
        isSOSCountingDown = true

        startEvidenceRecording() // Phele recording start karo

        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else { v.vibrate(longArrayOf(0, 500, 500), 0) }

        // 15 Seconds Countdown as requested
        countDownTimer = object : CountDownTimer(15000, 1000) {
            override fun onTick(m: Long) {}
            override fun onFinish() {
                v.cancel()
                finalTriggerSOS()
                isSOSCountingDown = false
            }
        }.start()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("trigger_sos", true)
        }
        startActivity(intent)
    }

    private fun triggerInstantSOS() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(1000)
        startEvidenceRecording()
        finalTriggerSOS()
    }

    private fun finalTriggerSOS() {
        val locLink = getRealLocation()
        val message = "EMERGENCY ALERT! I need help. My Location: $locLink"

        // Send SMS
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else { SmsManager.getDefault() }

            if (!num1.isNullOrBlank()) smsManager.sendTextMessage(num1, null, message, null, null)
            if (!num2.isNullOrBlank()) smsManager.sendTextMessage(num2, null, message, null, null)
        } catch (e: Exception) { Log.e("SOS", "SMS Failed") }

        // Make Call after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!num1.isNullOrBlank()) {
                    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num1")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(callIntent)
                }
            } catch (e: Exception) { Log.e("SOS", "Call Failed") }
        }, 2000)
    }

    private fun getRealLocation(): String {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}" else "Location Unavailable"
        } catch (e: Exception) { "Location Error" }
    }

    private fun startEvidenceRecording() {
        if (mediaRecorder != null) return
        try {
            val file = getNextRecordingFile()
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) { mediaRecorder = null }
    }

    private fun getNextRecordingFile(): File {
        val dir = getExternalFilesDir(null)
        var count = 1
        var file = File(dir, "recording$count.mp3")
        while (file.exists()) {
            count++
            file = File(dir, "recording$count.mp3")
        }
        return file
    }

    private fun stopSOSCountdown() {
        countDownTimer?.cancel()
        (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel()
        isSOSCountingDown = false
        mediaRecorder?.apply { try { stop() } catch(e: Exception) {}; release() }
        mediaRecorder = null
    }

    private fun stopAIDetection() {
        try {
            audioRecord?.stop()
            handlerThread?.quitSafely()
            classificationHandler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {}
    }

    private fun setupNotification() {
        val channelId = "ashaa_sec"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Ashaa Security", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ashaa Shield Active ✨")
            .setContentText("Monitoring for safety...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true).build()
        startForeground(1, notification)
    }

    private fun sendShutdownAlert() {
        val loc = getRealLocation()
        val msg = "CRITICAL: Phone is shutting down! Last Location: $loc"
        if (!num1.isNullOrBlank()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                smsManager.sendTextMessage(num1, null, msg, null, null)
            } catch (e: Exception) {}
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (isSOSCountingDown || e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val accel = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        if (accel > 55) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 500) {
                shakeCount++; lastShakeTime = now
                if (shakeCount >= 4) { initiateSOSProcess(); shakeCount = 0 }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(eventReceiver) } catch (e: Exception) {}
        stopSOSCountdown(); stopAIDetection()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}////-