package com.faisal.tv.ui.category

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.VerticalGridView
import com.faisal.tv.FaisalTvApp
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer
import com.faisal.tv.ui.home.ChannelCardPresenter
import com.faisal.tv.ui.player.PlayerActivity
import com.faisal.tv.util.CategoryTintHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CategoryActivity : AppCompatActivity() {

    private lateinit var categoryTitleView: TextView
    private lateinit var channelCountView: TextView
    private lateinit var clockView: TextView
    private lateinit var gridView: VerticalGridView
    private lateinit var rootLayout: View

    private var categoryName: String = ""
    private var channels: List<ChannelDto> = emptyList()

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("EEE, MMM d  •  h:mm a", Locale.getDefault())
            clockView.text = sdf.format(Date())
            clockHandler.postDelayed(this, 60000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        categoryTitleView = findViewById(R.id.category_title)
        channelCountView = findViewById(R.id.category_channel_count)
        clockView = findViewById(R.id.category_clock)
        gridView = findViewById(R.id.category_grid)
        rootLayout = findViewById(R.id.category_root)

        categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: "Category"
        resolveChannels()

        // Interface Change #8: Static per-category tinted background
        rootLayout.setBackgroundResource(CategoryTintHelper.getBackgroundRes(categoryName))

        categoryTitleView.text = categoryName
        channelCountView.text = "${channels.size} Channels"

        setupGrid()
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun resolveChannels() {
        val staticList = activeCategoryChannels
        val intentList = intent.getSerializableExtra(EXTRA_CHANNEL_LIST) as? ArrayList<ChannelDto>

        channels = when {
            !staticList.isNullOrEmpty() -> staticList
            !intentList.isNullOrEmpty() -> intentList
            else -> emptyList()
        }
    }

    private fun setupGrid() {
        val app = application as FaisalTvApp
        val prefsStore = app.container.prefsStore

        val cardPresenter = ChannelCardPresenter(prefsStore) { channel ->
            prefsStore.toggleFavorite(channel.id)
        }

        val objectAdapter = ArrayObjectAdapter(cardPresenter)
        channels.forEach { objectAdapter.add(it) }

        val bridgeAdapter = ItemBridgeAdapter(objectAdapter)
        bridgeAdapter.setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
            override fun onBind(viewHolder: ItemBridgeAdapter.ViewHolder) {
                viewHolder.itemView.setOnClickListener {
                    val position = viewHolder.layoutPosition
                    if (position in channels.indices) {
                        val channel = channels[position]
                        val streamUrl = UrlSanitizer.getStreamUrl(channel.links)
                        if (!streamUrl.isNullOrEmpty()) {
                            PlayerActivity.activeChannels = channels
                            val intent = Intent(this@CategoryActivity, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
                                putExtra(PlayerActivity.EXTRA_CHANNEL_TITLE, channel.title)
                                putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id)
                                putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, position)
                            }
                            startActivity(intent)
                        }
                    }
                }
            }
        })

        gridView.adapter = bridgeAdapter
        gridView.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)
    }

    override fun onStop() {
        super.onStop()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_CATEGORY_NAME = "extra_category_name"
        const val EXTRA_CHANNEL_LIST = "extra_channel_list"

        var activeCategoryChannels: List<ChannelDto>? = null
    }
}
