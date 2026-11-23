package com.faisal.tv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.faisal.tv.FaisalTvApp
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer
import com.faisal.tv.data.repository.ChannelsResult
import com.faisal.tv.ui.player.PlayerActivity

class HomeFragment : BrowseSupportFragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter

    private var loadingLayout: LinearLayout? = null
    private var errorLayout: LinearLayout? = null
    private var errorMessageView: TextView? = null
    private var btnRetry: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.bg_sidebar)

        val app = requireActivity().application as FaisalTvApp
        viewModel = HomeViewModel(app.container.repository)

        setupRowsAdapter()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind overlays from activity
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

    private fun setupRowsAdapter() {
        val listRowPresenter = ListRowPresenter().apply {
            shadowEnabled = false
            selectEffectEnabled = false
        }
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is ChannelDto) {
                val streamUrl = UrlSanitizer.getStreamUrl(item.links)
                if (!streamUrl.isNullOrEmpty()) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_TITLE, item.title)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, item.id)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.channelsState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ChannelsResult.Success -> {
                    hideOverlays()
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
        val cardPresenter = ChannelCardPresenter()

        var headerId = 0L

        // 1. "All Channels" row (sorted by views descending)
        if (success.allChannels.isNotEmpty()) {
            val allChannelsAdapter = ArrayObjectAdapter(cardPresenter)
            for (ch in success.allChannels) {
                allChannelsAdapter.add(ch)
            }
            val header = HeaderItem(headerId++, getString(R.string.all_channels))
            rowsAdapter.add(ListRow(header, allChannelsAdapter))
        }

        // 2. Dynamic Category rows from API
        for ((categoryName, channels) in success.categoriesMap) {
            val categoryAdapter = ArrayObjectAdapter(cardPresenter)
            for (ch in channels) {
                categoryAdapter.add(ch)
            }
            val header = HeaderItem(headerId++, categoryName)
            rowsAdapter.add(ListRow(header, categoryAdapter))
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
