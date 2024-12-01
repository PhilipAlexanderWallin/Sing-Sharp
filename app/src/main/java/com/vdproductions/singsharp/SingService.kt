package com.vdproductions.singsharp

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.RECORDSTATE_RECORDING
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.ViewModelProvider
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchDetectionResult
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

class SingService : Service() {

    private lateinit var singViewModel: SingViewModel
    private lateinit var windowManager: WindowManager
    private lateinit var singView: SingView
    private lateinit var dispatcher: AudioDispatcher
    private lateinit var screenReceiver: ScreenReceiver
    private lateinit var audioRecord: AudioRecord

    val sampleRate = 44100
    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val buffer = ByteArray(bufferSize)
    val pipedInputStream = PipedInputStream()
    val pipedOutputStream = PipedOutputStream(pipedInputStream)

    inner class ScreenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    audioRecord.stop()
                }
                Intent.ACTION_USER_PRESENT  -> {
                    audioRecord.startRecording()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val pendingIntent = createStopServiceIntent()

        val notification = NotificationCompat.Builder(this, "sing_sharp_service_channel")
            .setContentTitle("Sing Sharp running")
            .setContentText("Tap notification to stop")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent) // Set the pending intent to stop the service
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, notification)
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        singViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application).create(
            SingViewModel::class.java)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the overlay layout
        singView = SingView(this, null)
        singView.setViewModel(singViewModel)

        // Set the layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        // Add the view to the window
        windowManager.addView(singView, params)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        )
        {
            stopSelf()
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord.startRecording()

        dispatcher = AudioDispatcher(UniversalAudioInputStream(pipedInputStream,
            TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)), bufferSize, bufferSize / 2)

        dispatcher.addAudioProcessor(PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, sampleRate.toFloat(), bufferSize, object : PitchDetectionHandler {
            override fun handlePitch(
                pitchDetectionResult: PitchDetectionResult?,
                audioEvent: AudioEvent?
            ) {
                if (pitchDetectionResult == null) {
                    singViewModel.note.postValue(null)
                    return
                }
                if (pitchDetectionResult.probability > 0.8) { // Check if the pitch detection is reliable
                    singViewModel.note.postValue(frequencyToNote(pitchDetectionResult.pitch))
                }
            }
        }))

        Thread(dispatcher, "Audio dispatching").start()

        screenReceiver = ScreenReceiver()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        Thread {
            while (MainActivity.isServiceRunning) {
                if (audioRecord.recordingState == RECORDSTATE_RECORDING) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pipedOutputStream.write(buffer, 0, read)
                    }
                }
                else {
                    Thread.sleep(100)
                }
            }
        }.start()
    }

    private fun createStopServiceIntent(): PendingIntent {
        val stopIntent = Intent(this, SingService::class.java).apply {
            action = "STOP_SERVICE"
        }
        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::singView.isInitialized) {
            windowManager.removeView(singView)
        }
        unregisterReceiver(screenReceiver);
        audioRecord.release()
        dispatcher.stop()
        pipedOutputStream.close()
        MainActivity.isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

// Twelfth root of 2 (constant for equal temperament)
val SEMITONE_RATIO = 2.0.pow(1.0 / 12.0)
val pitchHistory = mutableListOf<Float>()
const val pitchWindowSize = 5

// Function to convert frequency to musical note
fun frequencyToNote(pitch: Float): Triple<Int, Int, Float> {
    // Add the new pitch value to the history
    pitchHistory.add(pitch)

    // Remove the oldest value if the history exceeds the window size
    if (pitchHistory.size > pitchWindowSize) {
        pitchHistory.removeAt(0)
    }

    // Calculate the average of the recent pitch values
    val averagePitch = pitchHistory.average().toFloat()

    val a4 = 440.0 // Frequency of A4
    val noteNumber = (12 * ln(averagePitch / a4) / ln(2.0)).roundToInt().toInt() + 69
    val noteIndex = noteNumber % 12
    val octave = (noteNumber / 12 - 1)

    // Calculate the number of semitones from A4
    val semitonesFromA4 = 12 * log2(averagePitch / a4)

    // Round to the nearest whole semitone
    val nearestSemitone = round(semitonesFromA4)

    // Frequency of the nearest note
    val targetFrequency = a4 * SEMITONE_RATIO.pow(nearestSemitone)

    // Calculate the offset in cents
    val centsOffset = 1200 * log2(averagePitch / targetFrequency).toFloat()

    // Return the note index, the octave, and the cents offset
    return Triple(noteIndex, octave, centsOffset)
}