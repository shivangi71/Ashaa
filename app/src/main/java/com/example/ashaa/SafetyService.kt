package com.example.ashaa

import android.app.*
import android.content.*
import android.hardware.*
import android.location.LocationManager
import android.media.*
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File

class SafetyService : Service(), SensorEventListener {
    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private var num1: String? = ""
    private var num2: String? = ""
    private var mediaRecorder: MediaRecorder? = null

    // AI Variables
    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var classificationHandler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var isSOSCountingDown = false
    private var countDownTimer: CountDownTimer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SOS") {
            stopSOSCountdown()
            return START_STICKY
        }
        num1 = intent?.getStringExtra("c1")
        num2 = intent?.getStringExtra("c2")
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        setupNotification()

        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)

        // AI Model Setup
        setupSmartAI()
    }

    private fun setupSmartAI() {
        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setScoreThreshold(0.70f) // 70% confidence
                .build()

            audioClassifier = AudioClassifier.createFromFileAndOptions(this, "yamnet.tflite", options)
            audioRecord = audioClassifier?.createAudioRecord()
            startSmartScreamDetection()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startSmartScreamDetection() {
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord?.startRecording()

        handlerThread = HandlerThread("AudioAIThread")
        handlerThread?.start()
        classificationHandler = Handler(handlerThread!!.looper)

        val runClassifier = object : Runnable {
            override fun run() {
                if (audioClassifier != null && !isSOSCountingDown) {
                    val tensorAudio = audioClassifier!!.createInputTensorAudio()
                    tensorAudio.load(audioRecord)
                    val results = audioClassifier!!.classify(tensorAudio)

                    // LOGS: Ise Android Studio ke Logcat mein check karein
                    results.forEach { result ->
                        result.categories.forEach { category ->
                            if (category.score > 0.30f) { // Sirf 30% se upar wale dikhayega
                                android.util.Log.d("AshaaAI", "Detected: ${category.label} - Score: ${category.score}")
                            }
                        }
                    }

                    val dangerLabels = listOf("Screaming", "Shouting", "Yell", "Crying, sobbing", "Screech")
                    val detectedDanger = results.flatMap { it.categories }.any { category ->
                        dangerLabels.contains(category.label) && category.score > 0.40f // Threshold kam kiya
                    }

                    if (detectedDanger) {
                        android.util.Log.d("AshaaAI", "DANGER DETECTED! Triggering SOS...")
                        Handler(Looper.getMainLooper()).post { initiateSOSProcess() }
                    }
                }
                classificationHandler?.postDelayed(this, 1000)
            }
        }
        classificationHandler?.post(runClassifier)
    }
    private fun initiateSOSProcess() {
        if (isSOSCountingDown) return
        isSOSCountingDown = true

        // 1. AI ko stop karna padega taaki mic khali ho jaye recording ke liye
        stopAIDetection()

        // 2. Recording Shuru
        startEvidenceRecording()

        // 3. Looping Vibration
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else {
            v.vibrate(longArrayOf(0, 500, 500), 0)
        }

        // 4. 30 Sec Countdown
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(m: Long) {}
            override fun onFinish() {
                v.cancel()
                finalTriggerSOS()
                isSOSCountingDown = false
            }
        }.start()

        // 5. Popup Launch
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("trigger_sos", true)
        }
        startActivity(intent)
    }

    private fun stopAIDetection() {
        try {
            audioRecord?.stop()
            handlerThread?.quitSafely()
        } catch (e: Exception) { }
    }

    private fun stopSOSCountdown() {
        countDownTimer?.cancel()
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.cancel()
        isSOSCountingDown = false

        // SOS cancel hone par AI ko fir se start karna
        setupSmartAI()
    }

    private fun finalTriggerSOS() {
        val locLink = getRealLocation()
        val message = "HELP! I am in danger. Real-time Location: $locLink"
        try {
            val sms = SmsManager.getDefault()
            if (!num1.isNullOrBlank()) sms.sendTextMessage(num1, null, message, null, null)
            if (!num2.isNullOrBlank()) sms.sendTextMessage(num2, null, message, null, null)

            if (!num1.isNullOrBlank()) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$num1")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(callIntent)
            }
        } catch (e: Exception) { }
    }

    private fun startEvidenceRecording() {
        if (mediaRecorder != null) return
        try {
            val file = File(getExternalFilesDir(null), "SOS_Evidence_${System.currentTimeMillis()}.mp4")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupNotification() {
        val chan = NotificationChannel("ashaa_sec", "Ashaa Security", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        val notification = NotificationCompat.Builder(this, "ashaa_sec")
            .setContentTitle("Ashaa Shield Active ✨")
            .setContentText("Aap surakshit hain...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (isSOSCountingDown) return
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val accel = Math.sqrt((x * x + y * y + z * z).toDouble())
        if (accel > 55) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 500) {
                shakeCount++
                lastShakeTime = now
                if (shakeCount >= 4) {
                    initiateSOSProcess()
                    shakeCount = 0
                }
            }
        }
    }

    private fun getRealLocation(): String {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) "https://www.google.com/maps/search/?api=1&query=${loc.latitude},${loc.longitude}"
            else "Location Unavailable"
        } catch (e: Exception) { "Location Error" }
    }

    override fun onDestroy() {
        stopSOSCountdown()
        stopAIDetection()
        audioClassifier?.close()
        mediaRecorder?.apply { stop(); release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}