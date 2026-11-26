package dev.lunikodevelopment.ludoform.menu

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Display

/**
 * Asks Android for permission to record the screen (D49). Android shows its own dialog every time; the player answers
 * it. The activity is see-through and closes as soon as the answer comes.
 */
class RecordConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, RecordService::class.java).putExtra(RecordService.RESULT, resultCode).putExtra(RecordService.DATA, data),
            )
        } else {
            InGameMenuService.running?.recordingRefused()
        }
        finish()
    }

    private companion object {
        const val REQUEST = 1
    }
}

/**
 * Records the top screen into Movies/Ludoform as MP4 (D49): video only, at the screen's size up to 1920 wide, 30 fps.
 * A foreground service with a notification, as Android requires while a recording runs; Stop in the in-game menu ends it.
 */
class RecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var output: Uri? = null
    private var descriptor: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == STOP) {
            finish(saved = true)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val result = intent?.getIntExtra(RESULT, 0) ?: 0

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(DATA)
        if (data == null || !begin(result, data)) {
            InGameMenuService.running?.recordingFailed()
            finish(saved = false)
        }
        return START_NOT_STICKY
    }

    private fun begin(
        result: Int,
        data: Intent,
    ): Boolean =
        runCatching {
            val mode = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
            val (width, height) = RecordSize.fit(mode.physicalWidth, mode.physicalHeight)
            val density = resources.displayMetrics.densityDpi
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "Ludoform_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Ludoform")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            val uri = checkNotNull(contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
            output = uri
            val fd = checkNotNull(contentResolver.openFileDescriptor(uri, "w"))
            descriptor = fd
            val recorder =
                MediaRecorder(this).apply {
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(fd.fileDescriptor)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(width, height)
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(12_000_000)
                    prepare()
                }
            this.recorder = recorder
            val projection =
                getSystemService(MediaProjectionManager::class.java).getMediaProjection(result, data)
                    ?: error("No projection")
            this.projection = projection
            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() = finish(saved = true)
                },
                Handler(Looper.getMainLooper()),
            )
            display =
                projection.createVirtualDisplay(
                    "Ludoform recording",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.surface,
                    null,
                    null,
                )
            recorder.start()
            startedAt = System.currentTimeMillis()
            InGameMenuService.running?.recordingStarted()
            true
        }.getOrElse { false }

    private var finished = false

    private fun finish(saved: Boolean) {
        if (finished) return
        finished = true
        val wasRecording = startedAt != null
        runCatching { recorder?.stop() }.onFailure { if (wasRecording) startedAt = null }
        runCatching { recorder?.release() }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { descriptor?.close() }
        val uri = output
        val kept = saved && startedAt != null && uri != null
        if (uri != null) {
            if (kept) {
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } else {
                runCatching { contentResolver.delete(uri, null, null) }
            }
        }
        startedAt = null
        InGameMenuService.running?.recordingStopped(kept)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        finish(saved = true)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screen recording", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Ludoform is recording the top screen")
            .setContentText("Stop it from the in-game menu.")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val RESULT = "result"
        const val DATA = "data"
        private const val STOP = "dev.lunikodevelopment.ludoform.STOP_RECORDING"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42

        /** When the running recording started; null while none runs. */
        @Volatile var startedAt: Long? = null
            private set

        fun stop(context: Context) {
            context.startService(Intent(context, RecordService::class.java).setAction(STOP))
        }
    }
}

/** The recording's size: the screen's, scaled down to at most 1920 wide, in even numbers as encoders need. Pure. */
object RecordSize {
    fun fit(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val scale = minOf(1f, 1920f / maxOf(width, 1))
        val w = (width * scale).toInt() and 1.inv()
        val h = (height * scale).toInt() and 1.inv()
        return w.coerceAtLeast(2) to h.coerceAtLeast(2)
    }
}
