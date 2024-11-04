package com.vdproductions.singsharp

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.RECORDSTATE_RECORDING
import android.media.MediaRecorder
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import kotlin.math.ln
import kotlin.math.roundToInt
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchDetectionResult
import java.io.PipedInputStream
import java.io.PipedOutputStream


class SingService : Service() {

    private lateinit var singViewModel: SingViewModel
    private lateinit var windowManager: WindowManager
    private lateinit var singView: SingView
    private lateinit var dispatcher: AudioDispatcher
    private lateinit var audioRecord: AudioRecord

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        singViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application).create(
            SingViewModel::class.java)

        val pendingIntent = createStopServiceIntent()

        val notification = NotificationCompat.Builder(this, "sing_sharp_service_channel")
            .setContentTitle("Sing Sharp running")
            .setContentText("Tap notification to stop")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent) // Set the pending intent to stop the service
            .build()

        startForeground(1, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the overlay layout
        singView = SingView(this, null)
        singView.setViewModel(singViewModel)

        // Set the layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100 // Change this value to set the initial position

        // Add the view to the window
        windowManager.addView(singView, params)

        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

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

        val buffer = ByteArray(bufferSize)
        val pipedInputStream = PipedInputStream()
        val pipedOutputStream = PipedOutputStream(pipedInputStream)

        dispatcher = AudioDispatcher(UniversalAudioInputStream(pipedInputStream,
            TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)), bufferSize, bufferSize / 2)

        dispatcher.addAudioProcessor(PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, sampleRate.toFloat(), bufferSize, object : PitchDetectionHandler {
            override fun handlePitch(
                pitchDetectionResult: PitchDetectionResult?,
                audioEvent: AudioEvent?
            ) {
                if (pitchDetectionResult == null) {
                    return
                }
                if (pitchDetectionResult.probability > 0.8) { // Check if the pitch detection is reliable
                    val note = frequencyToNote(pitchDetectionResult.pitch)
                    singViewModel.note.postValue(note)
                }
            }
        }))

        Thread(dispatcher, "Audio dispatching").start()

        audioRecord.startRecording()

        Thread {
            while (audioRecord.recordingState == RECORDSTATE_RECORDING) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    pipedOutputStream.write(buffer, 0, read)
                }
            }
            audioRecord.release()
            pipedOutputStream.close()
        }.start()
    }

    // Function to convert frequency to musical note
    fun frequencyToNote(frequency: Float): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val a4 = 440.0 // Frequency of A4
        val noteNumber = (12 * ln(frequency / a4) / ln(2.0)).roundToInt().toInt() + 69
        return noteNames[noteNumber % 12] + (noteNumber / 12 - 1) // Octave
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
        audioRecord.stop()
        dispatcher.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}