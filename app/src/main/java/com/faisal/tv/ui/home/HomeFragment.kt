package com.faisal.tv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowPresenter
import com.faisal.tv.FaisalTvApp
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer
import com.faisal.tv.data.local.PrefsStore
import com.faisal.tv.data.repository.ChannelsResult
import com.faisal.tv.ui.category.CategoryActivity
import com.faisal.tv.ui.player.PlayerActivity

class HomeFragment : BrowseSupportFragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var prefsStore: PrefsStore

    private var loadingLayout: LinearLayout? = null
    private var errorLayout: LinearLayout? = null
    private var errorMessageView: TextView? = null
    private var btnRetry: Button? = null

    private var latestChannelsResult: ChannelsResult.Success? = null

    // For focus restoration
    private var lastSelectedPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.bg_sidebar)

        val app = requireActivity().application as FaisalTvApp
        prefsStore = app.container.prefsStore
        viewModel = HomeViewModel(app.container.repository)

        setupRowsAdapter()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        loadingLayout = activity.findViewById(R.id.loading_layout)
        errorLayout = activity.findViewById(R.id.error_layout)
        errorMessageView = activity.findViewById(R.id.error_message)
        btnRetry = activity.findViewById(R.id.btn_retry)

        btnRetry?.setOnClickListener {
            showLoading()
            viewModel.loadChannels(forceRefresh = true)
        }

        observeViewModel()

        showLoading()
        viewModel.loadChannels(forceRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        // Refresh Favorites and Recents when returning from Player or Category
        latestChannelsResult?.let {
            refreshFavoritesAndRecents(it)
        }
    }

    private fun setupRowsAdapter() {
        // IC #4: Dim row containers/text only, never to bitmaps (selectEffectEnabled = false)
        val listRowPresenter = ListRowPresenter().apply {
            shadowEnabled = false
            selectEffectEnabled = false
        }
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        // IC #2: Clicking a category in the sidebar opens dedicated Category screen
        headersSupportFragment?.setOnHeaderClickedListener { _, row ->
            val headerName = row.headerItem?.name ?: return@setOnHeaderClickedListener
            val success = latestChannelsResult ?: return@setOnHeaderClickedListener

            // Favorites and Recents stay as rows at the top of Home
            if (headerName == getString(R.string.favorites) ||
                headerName == getString(R.string.recently_watched) ||
                headerName == getString(R.string.featured_title)
            ) {
                startHeadersTransition(false)
                return@setOnHeaderClickedListener
            }

            val targetChannels = when (headerName) {
                getString(R.string.all_channels) -> success.allChannels
                else -> success.categoriesMap[headerName] ?: emptyList()
            }

            if (targetChannels.isNotEmpty()) {
                CategoryActivity.activeCategoryChannels = targetChannels
                val intent = Intent(requireContext(), CategoryActivity::class.java).apply {
                    putExtra(CategoryActivity.EXTRA_CATEGORY_NAME, headerName)
                }
                startActivity(intent)
            }
        }

        // Card Click Listener -> PlayerActivity
        setOnItemViewClickedListener { _, item, _, row ->
            if (item is ChannelDto) {
                val streamUrl = UrlSanitizer.getStreamUrl(item.links)
                if (!streamUrl.isNullOrEmpty()) {
                    val rowChannels = getChannelsFromRow(row)
                    val index = if (rowChannels.contains(item)) rowChannels.indexOf(item) else 0

                    PlayerActivity.activeChannels = if (rowChannels.isNotEmpty()) rowChannels else listOf(item)
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_TITLE, item.title)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, item.id)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_INDEX, index)
                    }
                    startActivity(intent)
                }
            }
        }

        // IC #4: Dim headers of unfocused rows (text only, never bitmaps)
        setOnItemViewSelectedListener { _, _, rowViewHolder, _ ->
            if (rowViewHolder is RowPresenter.ViewHolder) {
                lastSelectedPosition = selectedPosition
                rowViewHolder.headerViewHolder?.view?.alpha = 1.0f
            }
        }
    }

    private fun getChannelsFromRow(row: Row?): List<ChannelDto> {
        val listRow = row as? ListRow ?: return emptyList()
        val adapter = listRow.adapter as? ArrayObjectAdapter ?: return emptyList()
        val list = mutableListOf<ChannelDto>()
        for (i in 0 until adapter.size()) {
            val item = adapter.get(i)
            if (item is ChannelDto) {
                list.add(item)
            }
        }
        return list
    }

    private fun observeViewModel() {
        viewModel.channelsState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ChannelsResult.Success -> {
                    hideOverlays()
                    latestChannelsResult = result
                    populateRows(result)
                }
                is ChannelsResult.Forbidden403 -> {
                    showError(getString(R.string.error_forbidden))
                }
                is ChannelsResult.NetworkError -> {
                    val msg = result.message ?: getString(R.string.error_network)
                    showError(msg)
                }
                is ChannelsResult.Empty -> {
                    showError(getString(R.string.error_empty))
                }
            }
        }
    }

    private fun populateRows(success: ChannelsResult.Success) {
        rowsAdapter.clear()

        val cardPresenter = ChannelCardPresenter(prefsStore) { channel ->
            toggleFavorite(channel)
        }
        val featuredPresenter = FeaturedHeroPresenter()

        var headerId = 0L

        // Row 1: ★ Favorites (T2.3, hidden if empty)
        val favIds = prefsStore.getFavoriteIds()
        val favChannels = success.allChannels.filter { favIds.contains(it.id.toString()) }
        if (favChannels.isNotEmpty()) {
            val favAdapter = ArrayObjectAdapter(cardPresenter)
            favChannels.forEach { favAdapter.add(it) }
            val header = HeaderItem(headerId++, getString(R.string.favorites))
            rowsAdapter.add(ListRow(header, favAdapter))
        }

        // Row 2: Recently Watched (T2.3, hidden if empty, max 10)
        val recentIds = prefsStore.getRecentIds()
        val recentChannels = recentIds.mapNotNull { idStr ->
            success.allChannels.find { it.id.toString() == idStr }
        }
        if (recentChannels.isNotEmpty()) {
            val recentAdapter = ArrayObjectAdapter(cardPresenter)
            recentChannels.forEach { recentAdapter.add(it) }
            val header = HeaderItem(headerId++, getString(R.string.recently_watched))
            rowsAdapter.add(ListRow(header, recentAdapter))
        }

        // Row 3: IC #1 Hero spotlight row (most-watched channel, static poster only)
        if (success.allChannels.isNotEmpty()) {
            val topChannel = success.allChannels.first()
            val heroAdapter = ArrayObjectAdapter(featuredPresenter)
            heroAdapter.add(topChannel)
            val header = HeaderItem(headerId++, getString(R.string.featured_title))
            rowsAdapter.add(ListRow(header, heroAdapter))
        }

        // Row 4: "All Channels" row (sorted by views descending)
        if (success.allChannels.isNotEmpty()) {
            val allChannelsAdapter = ArrayObjectAdapter(cardPresenter)
            success.allChannels.forEach { allChannelsAdapter.add(it) }
            val header = HeaderItem(headerId++, getString(R.string.all_channels))
            rowsAdapter.add(ListRow(header, allChannelsAdapter))
        }

        // Row 5+: Dynamic Category rows from API
        for ((categoryName, channels) in success.categoriesMap) {
            val categoryAdapter = ArrayObjectAdapter(cardPresenter)
            channels.forEach { categoryAdapter.add(it) }
            val header = HeaderItem(headerId++, categoryName)
            rowsAdapter.add(ListRow(header, categoryAdapter))
        }

        // Restore focus position if returning
        if (lastSelectedPosition in 0 until rowsAdapter.size()) {
            setSelectedPosition(lastSelectedPosition)
        }
    }

    private fun refreshFavoritesAndRecents(success: ChannelsResult.Success) {
        // Re-populates rows seamlessly without disrupting user position
        val currentPosition = selectedPosition
        populateRows(success)
        if (currentPosition in 0 until rowsAdapter.size()) {
            setSelectedPosition(currentPosition)
        }
    }

    private fun toggleFavorite(channel: ChannelDto) {
        val isNowFav = prefsStore.toggleFavorite(channel.id)
        val msg = if (isNowFav) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites)
        Toast.makeText(requireContext(), "${channel.title}: $msg", Toast.LENGTH_SHORT).show()

        latestChannelsResult?.let {
            refreshFavoritesAndRecents(it)
        }
    }

    private fun showLoading() {
        errorLayout?.visibility = View.GONE
        loadingLayout?.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        loadingLayout?.visibility = View.GONE
        errorMessageView?.text = message
        errorLayout?.visibility = View.VISIBLE
        btnRetry?.requestFocus()
    }

    private fun hideOverlays() {
        loadingLayout?.visibility = View.GONE
        errorLayout?.visibility = View.GONE
    }
}
