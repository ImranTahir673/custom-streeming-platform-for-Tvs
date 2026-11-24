package com.faisal.tv.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer

/**
 * Interface Change #1: Hero spotlight row featured card presenter.
 * Static poster image only, NO video preview, keeping RAM well within budget.
 */
class FeaturedHeroPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_featured_hero, parent, false)
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.05f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(120)
                .start()

            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_glass_card_focused)
                v.elevation = 12f
            } else {
                v.setBackgroundResource(R.drawable.bg_glass_card_normal)
                v.elevation = 2f
            }
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val channel = item as? ChannelDto ?: return
        val root = viewHolder.view

        val poster = root.findViewById<ImageView>(R.id.hero_poster)
        val title = root.findViewById<TextView>(R.id.hero_title)
        val category = root.findViewById<TextView>(R.id.hero_category)
        val number = root.findViewById<TextView>(R.id.hero_channel_number)

        title.text = channel.title
        category.text = channel.categories.firstOrNull()?.name ?: "Live"

        if (channel.channelNumber > 0) {
            number.text = "CH ${channel.channelNumber}"
            number.visibility = View.VISIBLE
        } else {
            number.visibility = View.GONE
        }

        val posterUrl = UrlSanitizer.sanitizeUrl(channel.poster)
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(480, 220)
            .centerCrop()

        Glide.with(root.context)
            .load(posterUrl)
            .apply(options)
            .into(poster)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val root = viewHolder.view
        val poster = root.findViewById<ImageView>(R.id.hero_poster)
        Glide.with(root.context).clear(poster)
    }
}
