package com.kokoro.tts.reader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kokoro.tts.reader.ui.ReaderActivity

/**
 * Foreground Service hosting MediaSessionCompat and lock screen media widget.
 * Ensures uninterrupted audio playback when screen is turned off or app is in background.
 */
class BookPlaybackService : Service() {

    interface PlaybackController {
        fun play()
        fun pause()
        fun nextSentence()
        fun previousSentence()
        fun isPlaying(): Boolean
    }

    companion object {
        private const val TAG = "BookPlaybackService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "kokoro_reader_playback_channel"

        const val ACTION_PLAY_PAUSE = "com.kokoro.tts.action.PLAY_PAUSE"
        const val ACTION_PREV = "com.kokoro.tts.action.PREV"
        const val ACTION_NEXT = "com.kokoro.tts.action.NEXT"
        const val ACTION_STOP = "com.kokoro.tts.action.STOP"
        const val ACTION_UPDATE = "com.kokoro.tts.action.UPDATE"

        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_PROGRESS_TEXT = "extra_progress_text"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var playbackController: PlaybackController? = null

        fun updateState(
            context: Context,
            isPlaying: Boolean,
            bookTitle: String,
            chapterTitle: String,
            progressText: String
        ) {
            val intent = Intent(context, BookPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_PROGRESS_TEXT, progressText)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start/update BookPlaybackService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BookPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying = false
    private var bookTitle = "Audiobook"
    private var chapterTitle = "Playing"
    private var progressText = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Book Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing audiobook controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackController?.play()
                }

                override fun onPause() {
                    playbackController?.pause()
                }

                override fun onSkipToNext() {
                    playbackController?.nextSentence()
                }

                override fun onSkipToPrevious() {
                    playbackController?.previousSentence()
                }

                override fun onStop() {
                    playbackController?.pause()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val controller = playbackController
                if (controller != null) {
                    if (controller.isPlaying()) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
            }
            ACTION_PREV -> {
                playbackController?.previousSentence()
            }
            ACTION_NEXT -> {
                playbackController?.nextSentence()
            }
            ACTION_STOP -> {
                playbackController?.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: bookTitle
                chapterTitle = intent.getStringExtra(EXTRA_CHAPTER_TITLE) ?: chapterTitle
                progressText = intent.getStringExtra(EXTRA_PROGRESS_TEXT) ?: progressText
            }
        }

        updateMediaSessionState()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapterTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, bookTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, progressText)
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, ReaderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, BookPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, BookPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, BookPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(bookTitle)
            .setContentText("$chapterTitle • $progressText")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .build()
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
