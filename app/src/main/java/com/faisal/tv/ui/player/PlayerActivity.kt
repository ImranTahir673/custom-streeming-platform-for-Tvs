package com.faisal.tv.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faisal.tv.R
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var bufferingSpinner: ProgressBar
    private lateinit var infoBar: LinearLayout
    private lateinit var channelTitleView: TextView
    private lateinit var errorLayout: LinearLayout
    private lateinit var btnRetry: Button

    private var streamUrl: String? = null
    private var channelTitle: String? = null

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideInfoBarRunnable = Runnable {
        infoBar.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        bufferingSpinner = findViewById(R.id.player_buffering_spinner)
        infoBar = findViewById(R.id.player_info_bar)
        channelTitleView = findViewById(R.id.player_channel_title)
        errorLayout = findViewById(R.id.player_error_layout)
        btnRetry = findViewById(R.id.player_btn_retry)

        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        channelTitle = intent.getStringExtra(EXTRA_CHANNEL_TITLE)

        channelTitleView.text = channelTitle ?: ""

        btnRetry.setOnClickListener {
            retryPlayback()
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        if (streamUrl.isNullOrEmpty()) {
            showError()
            return
        }

        errorLayout.visibility = View.GONE
        bufferingSpinner.visibility = View.VISIBLE

        // Conservative DefaultLoadControl: min 15s / max 30s / playback 2.5s / rebuffer 5s / max 15MB buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs
                30000, // maxBufferMs
                2500,  // bufferForPlaybackMs
                5000   // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(15 * 1024 * 1024)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl!!))

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                bufferingSpinner.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                bufferingSpinner.visibility = View.GONE
                                scheduleHideInfoBar()
                            }
                            Player.STATE_ENDED -> {
                                bufferingSpinner.visibility = View.GONE
                            }
                            Player.STATE_IDLE -> {
                                bufferingSpinner.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showError()
                    }
                })
            }

        playerView.player = player
        showInfoBar()
    }

    private fun showError() {
        bufferingSpinner.visibility = View.GONE
        infoBar.visibility = View.GONE
        hideHandler.removeCallbacks(hideInfoBarRunnable)
        errorLayout.visibility = View.VISIBLE
        btnRetry.requestFocus()
    }

    private fun retryPlayback() {
        releasePlayer()
        initializePlayer()
    }

    private fun releasePlayer() {
        hideHandler.removeCallbacks(hideInfoBarRunnable)
        playerView.player = null
        player?.release()
        player = null
    }

    private fun showInfoBar() {
        hideHandler.removeCallbacks(hideInfoBarRunnable)
        infoBar.visibility = View.VISIBLE
    }

    private fun scheduleHideInfoBar() {
        hideHandler.removeCallbacks(hideInfoBarRunnable)
        hideHandler.postDelayed(hideInfoBarRunnable, 4000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (errorLayout.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                retryPlayback()
                return true
            } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                finish()
                return true
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (infoBar.visibility == View.VISIBLE) {
                    infoBar.visibility = View.GONE
                    hideHandler.removeCallbacks(hideInfoBarRunnable)
                } else {
                    showInfoBar()
                    scheduleHideInfoBar()
                }
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (infoBar.visibility == View.VISIBLE) {
                    infoBar.visibility = View.GONE
                    hideHandler.removeCallbacks(hideInfoBarRunnable)
                    true
                } else {
                    finish()
                    true
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_CHANNEL_TITLE = "extra_channel_title"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }
}
