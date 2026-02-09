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
import java.io.File

class SafetyService : Service(), SensorEventListener {
    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private var num1: String? = ""
    private var num2: String? = ""
    private var mediaRecorder: MediaRecorder? = null
    private var audioReader: AudioRecord? = null

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

        startScreamDetection()
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

    private fun initiateSOSProcess() {
        if (isSOSCountingDown) return
        isSOSCountingDown = true

        // 1. Recording TURANT shuru (Aapki requirement)
        startEvidenceRecording()

        // 2. Vibration (Looping)
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else {
            v.vibrate(longArrayOf(0, 500, 500), 0)
        }

        // 3. Countdown: 30 Seconds
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(m: Long) {}
            override fun onFinish() {
                v.cancel()
                finalTriggerSOS() // SMS aur Call sirf countdown khatam hone par
                isSOSCountingDown = false
            }
        }.start()

        // 4. Popup launch (MainActivity ko trigger_sos flag ke saath)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("trigger_sos", true)
        }
        startActivity(intent)
    }

    private fun stopSOSCountdown() {
        countDownTimer?.cancel()
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.cancel() // Vibration band
        isSOSCountingDown = false
        // Note: Recording band nahi kar rahe hain kyunki aapne kaha recording ho jaye
    }

    private fun finalTriggerSOS() {
        val locLink = getRealLocation()
        val message = "HELP! I am in danger. Real-time Location: $locLink"

        try {
            val sms = SmsManager.getDefault()
            if (!num1.isNullOrBlank()) sms.sendTextMessage(num1, null, message, null, null)
            if (!num2.isNullOrBlank()) sms.sendTextMessage(num2, null, message, null, null)

            // Primary contact ko turant call
            if (!num1.isNullOrBlank()) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$num1")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(callIntent)
            }
        } catch (e: Exception) { }
    }

    private fun getRealLocation(): String {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}"
            else "Location Permission needed/GPS OFF"
        } catch (e: Exception) { "Location Error" }
    }

    private fun startScreamDetection() {
        val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioReader = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        audioReader?.startRecording()

        Thread {
            val data = ShortArray(bufferSize)
            while (audioReader != null) {
                val read = audioReader?.read(data, 0, bufferSize) ?: 0
                var max = 0
                for (i in 0 until read) {
                    val absVal = Math.abs(data[i].toInt())
                    if (absVal > max) max = absVal
                }
                if (max > 32500 && !isSOSCountingDown) {
                    Handler(Looper.getMainLooper()).post { initiateSOSProcess() }
                }
            }
        }.start()
    }

    private fun startEvidenceRecording() {
        if (mediaRecorder != null) return // Already recording
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
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        stopSOSCountdown()
        audioReader?.stop(); audioReader?.release(); audioReader = null
        mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}