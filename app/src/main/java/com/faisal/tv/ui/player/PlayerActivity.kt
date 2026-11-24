package com.faisal.tv.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.widget.HorizontalGridView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.faisal.tv.FaisalTvApp
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer
import com.faisal.tv.data.local.PrefsStore
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

    // T2.1 OSD Bar Views
    private lateinit var osdBar: LinearLayout
    private lateinit var osdPoster: ImageView
    private lateinit var osdNumber: TextView
    private lateinit var osdTitle: TextView
    private lateinit var osdCategory: TextView
    private lateinit var osdFavStar: TextView
    private lateinit var osdLive: TextView
    private lateinit var osdHint: TextView

    // T2.2 Error Overlay Views
    private lateinit var errorLayout: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var btnRetry: Button

    // IC #6 Mini Channel List Views
    private lateinit var miniListLayout: LinearLayout
    private lateinit var miniGrid: HorizontalGridView

    private lateinit var prefsStore: PrefsStore

    // Channel List & Index
    private var channels: List<ChannelDto> = emptyList()
    private var currentIndex: Int = 0

    // Retry recovery state
    private var retryCount = 0
    private val retryHandler = Handler(Looper.getMainLooper())
    private val autoRetryRunnable = Runnable {
        performRetry()
    }

    // Auto-hide OSD handler
    private val osdHandler = Handler(Looper.getMainLooper())
    private val hideOsdRunnable = Runnable {
        osdBar.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val app = application as FaisalTvApp
        prefsStore = app.container.prefsStore

        playerView = findViewById(R.id.player_view)
        bufferingSpinner = findViewById(R.id.player_buffering_spinner)

        // Bind OSD
        osdBar = findViewById(R.id.player_osd_bar)
        osdPoster = findViewById(R.id.player_osd_poster)
        osdNumber = findViewById(R.id.player_osd_number)
        osdTitle = findViewById(R.id.player_osd_title)
        osdCategory = findViewById(R.id.player_osd_category)
        osdFavStar = findViewById(R.id.player_osd_fav_star)
        osdLive = findViewById(R.id.player_osd_live)
        osdHint = findViewById(R.id.player_osd_hint)

        // Bind Error
        errorLayout = findViewById(R.id.player_error_layout)
        errorText = findViewById(R.id.player_error_text)
        btnRetry = findViewById(R.id.player_btn_retry)

        // Bind Mini Channel List
        miniListLayout = findViewById(R.id.player_mini_list_layout)
        miniGrid = findViewById(R.id.player_mini_grid)

        btnRetry.setOnClickListener {
            retryCount = 0
            performRetry()
        }

        resolveChannelsFromIntent()
        setupMiniChannelList()
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun resolveChannelsFromIntent() {
        val staticList = activeChannels
        val intentList = intent.getSerializableExtra(EXTRA_CHANNEL_LIST) as? ArrayList<ChannelDto>

        channels = when {
            !staticList.isNullOrEmpty() -> staticList
            !intentList.isNullOrEmpty() -> intentList
            else -> emptyList()
        }

        currentIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, 0)

        // Fallback for direct single channel launch
        if (channels.isEmpty()) {
            val fallbackTitle = intent.getStringExtra(EXTRA_CHANNEL_TITLE) ?: ""
            val fallbackUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
            val fallbackId = intent.getIntExtra(EXTRA_CHANNEL_ID, 0)
            if (fallbackUrl.isNotEmpty()) {
                val dummyChannel = ChannelDto(
                    id = fallbackId,
                    title = fallbackTitle,
                    poster = null,
                    links = com.faisal.tv.data.api.LinksDto(android = fallbackUrl)
                )
                channels = listOf(dummyChannel)
                currentIndex = 0
            }
        }

        if (currentIndex < 0 || currentIndex >= channels.size) {
            currentIndex = 0
        }
    }

    private fun setupMiniChannelList() {
        if (channels.isEmpty()) return
        val adapter = MiniChannelAdapter(channels) { _, position ->
            hideMiniList()
            playChannelAtIndex(position)
        }
        miniGrid.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        if (channels.isNotEmpty()) {
            playChannelAtIndex(currentIndex)
        } else {
            showPermanentError("No channels available.")
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        retryHandler.removeCallbacks(autoRetryRunnable)
        osdHandler.removeCallbacks(hideOsdRunnable)
    }

    private fun playChannelAtIndex(index: Int) {
        if (channels.isEmpty()) return
        currentIndex = index
        val channel = channels[currentIndex]

        // Record to Recents (T2.3)
        prefsStore.addRecent(channel.id)

        // Reset error recovery
        retryCount = 0
        retryHandler.removeCallbacks(autoRetryRunnable)
        errorLayout.visibility = View.GONE

        // Update OSD info (T2.1)
        updateOsdViews(channel)
        showOsd()

        // Extract stream URL
        val streamUrl = UrlSanitizer.getStreamUrl(channel.links)
        if (streamUrl.isNullOrEmpty()) {
            handlePlaybackError()
            return
        }

        startPlayback(streamUrl)
    }

    private fun updateOsdViews(channel: ChannelDto) {
        osdTitle.text = channel.title
        osdCategory.text = channel.categories.firstOrNull()?.name ?: "Live"

        if (channel.channelNumber > 0) {
            osdNumber.text = "CH ${channel.channelNumber}"
            osdNumber.visibility = View.VISIBLE
        } else {
            osdNumber.visibility = View.GONE
        }

        // Favorite star indicator
        val isFav = prefsStore.isFavorite(channel.id)
        osdFavStar.visibility = if (isFav) View.VISIBLE else View.GONE

        // Load thumbnail poster into OSD
        val posterUrl = UrlSanitizer.sanitizeUrl(channel.poster)
        Glide.with(this)
            .load(posterUrl)
            .apply(
                RequestOptions()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .override(120, 68)
                    .centerCrop()
            )
            .into(osdPoster)
    }

    private fun startPlayback(streamUrl: String) {
        bufferingSpinner.visibility = View.VISIBLE

        // Conservative DefaultLoadControl: min 15s / max 30s / playback 2.5s / rebuffer 5s / max 15MB buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 30000, 2500, 5000)
            .setTargetBufferBytes(15 * 1024 * 1024)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        if (player == null) {
            player = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> bufferingSpinner.visibility = View.VISIBLE
                                Player.STATE_READY -> {
                                    bufferingSpinner.visibility = View.GONE
                                    scheduleHideOsd()
                                }
                                Player.STATE_ENDED -> bufferingSpinner.visibility = View.GONE
                                Player.STATE_IDLE -> bufferingSpinner.visibility = View.GONE
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            handlePlaybackError()
                        }
                    })
                }
            playerView.player = player
        }

        player?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    /**
     * T2.2: Stream error recovery overlay with exponential backoff (1s, 2s, 4s).
     */
    private fun handlePlaybackError() {
        bufferingSpinner.visibility = View.GONE
        hideOsd()

        if (retryCount < MAX_AUTO_RETRIES) {
            retryCount++
            val delayMs = (1L shl (retryCount - 1)) * 1000L // 1s, 2s, 4s
            errorText.text = getString(R.string.retrying_stream, retryCount)
            btnRetry.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE

            retryHandler.removeCallbacks(autoRetryRunnable)
            retryHandler.postDelayed(autoRetryRunnable, delayMs)
        } else {
            showPermanentError(getString(R.string.channel_offline_hint))
        }
    }

    private fun showPermanentError(message: String) {
        retryHandler.removeCallbacks(autoRetryRunnable)
        bufferingSpinner.visibility = View.GONE
        hideOsd()
        errorText.text = message
        btnRetry.visibility = View.VISIBLE
        errorLayout.visibility = View.VISIBLE
        btnRetry.requestFocus()
    }

    private fun performRetry() {
        errorLayout.visibility = View.GONE
        if (channels.isNotEmpty() && currentIndex in channels.indices) {
            val channel = channels[currentIndex]
            val streamUrl = UrlSanitizer.getStreamUrl(channel.links)
            if (!streamUrl.isNullOrEmpty()) {
                startPlayback(streamUrl)
            } else {
                showPermanentError(getString(R.string.channel_offline_hint))
            }
        }
    }

    private fun zapNext() {
        if (channels.isEmpty()) return
        val next = (currentIndex + 1) % channels.size
        playChannelAtIndex(next)
    }

    private fun zapPrev() {
        if (channels.isEmpty()) return
        val prev = if (currentIndex - 1 < 0) channels.size - 1 else currentIndex - 1
        playChannelAtIndex(prev)
    }

    private fun toggleFavoriteCurrentChannel() {
        if (channels.isEmpty() || currentIndex !in channels.indices) return
        val channel = channels[currentIndex]
        val isNowFav = prefsStore.toggleFavorite(channel.id)

        osdFavStar.visibility = if (isNowFav) View.VISIBLE else View.GONE
        showOsd()

        val toastMsg = if (isNowFav) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites)
        Toast.makeText(this, "${channel.title}: $toastMsg", Toast.LENGTH_SHORT).show()
    }

    private fun showOsd() {
        osdHandler.removeCallbacks(hideOsdRunnable)
        osdBar.visibility = View.VISIBLE
        scheduleHideOsd()
    }

    private fun hideOsd() {
        osdHandler.removeCallbacks(hideOsdRunnable)
        osdBar.visibility = View.GONE
    }

    private fun scheduleHideOsd() {
        osdHandler.removeCallbacks(hideOsdRunnable)
        osdHandler.postDelayed(hideOsdRunnable, OSD_DURATION_MS)
    }

    private fun showMiniList() {
        hideOsd()
        miniListLayout.visibility = View.VISIBLE
        miniGrid.scrollToPosition(currentIndex)
        miniGrid.requestFocus()
    }

    private fun hideMiniList() {
        miniListLayout.visibility = View.GONE
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // If mini channel list is open
        if (miniListLayout.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                hideMiniList()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                hideMiniList()
                showOsd()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        // If error overlay is open and offline (buttons active)
        if (errorLayout.visibility == View.VISIBLE && btnRetry.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    zapNext()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    zapPrev()
                    return true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    finish()
                    return true
                }
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                zapNext()
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                zapPrev()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showMiniList()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (osdBar.visibility == View.VISIBLE) {
                    hideOsd()
                } else {
                    showOsd()
                }
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_F -> {
                toggleFavoriteCurrentChannel()
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (osdBar.visibility == View.VISIBLE) {
                    hideOsd()
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
        const val EXTRA_CHANNEL_LIST = "extra_channel_list"
        const val EXTRA_CHANNEL_INDEX = "extra_channel_index"

        private const val OSD_DURATION_MS = 4000L
        private const val MAX_AUTO_RETRIES = 3

        /**
         * In-memory cache of channels to bypass Intent serialization overhead on weak dual-core CPUs.
         */
        var activeChannels: List<ChannelDto>? = null
    }
}
