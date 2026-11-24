package com.faisal.tv.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer
import com.faisal.tv.data.local.PrefsStore

class ChannelCardPresenter(
    private val prefsStore: PrefsStore? = null,
    private val onFavoriteToggle: ((ChannelDto) -> Unit)? = null
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH_PX, CARD_HEIGHT_PX)
            setBackgroundResource(R.drawable.bg_glass_card_normal)

            // Number badge (Interface Change #5)
            val numberBadge = TextView(context).apply {
                id = R.id.tag_channel_number
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_badge_pill)
                setPadding(14, 4, 14, 4)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = 12
                    leftMargin = 12
                }
            }
            addView(numberBadge)

            // Favorite star badge
            val favBadge = TextView(context).apply {
                id = R.id.tag_channel_fav
                text = "★"
                setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_badge_pill)
                setPadding(12, 2, 12, 2)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = 12
                    rightMargin = 12
                }
                visibility = View.GONE
            }
            addView(favBadge)

            // Glass focus effect (Interface Change #3)
            setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1.0f
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start()

                if (hasFocus) {
                    view.setBackgroundResource(R.drawable.bg_glass_card_focused)
                    view.elevation = 10f
                } else {
                    view.setBackgroundResource(R.drawable.bg_glass_card_normal)
                    view.elevation = 2f
                }
            }

            setOnLongClickListener { v ->
                val channel = v.getTag(R.id.tag_channel_data) as? ChannelDto
                if (channel != null && onFavoriteToggle != null) {
                    onFavoriteToggle.invoke(channel)
                    val badge = v.findViewById<TextView>(R.id.tag_channel_fav)
                    badge?.visibility = if (prefsStore?.isFavorite(channel.id) == true) View.VISIBLE else View.GONE
                    true
                } else {
                    false
                }
            }
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val channel = item as? ChannelDto ?: return
        val cardView = viewHolder.view as ImageCardView
        cardView.setTag(R.id.tag_channel_data, channel)

        cardView.titleText = channel.title
        cardView.contentText = channel.categories.firstOrNull()?.name ?: ""

        // Update channel number badge
        val numberBadge = cardView.findViewById<TextView>(R.id.tag_channel_number)
        if (channel.channelNumber > 0) {
            numberBadge?.text = "${channel.channelNumber}"
            numberBadge?.visibility = View.VISIBLE
        } else {
            numberBadge?.visibility = View.GONE
        }

        // Update favorite star badge
        val favBadge = cardView.findViewById<TextView>(R.id.tag_channel_fav)
        favBadge?.visibility = if (prefsStore?.isFavorite(channel.id) == true) View.VISIBLE else View.GONE

        val posterUrl = UrlSanitizer.sanitizeUrl(channel.poster)

        val requestOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(320, 180)
            .centerCrop()

        Glide.with(cardView.context)
            .load(posterUrl)
            .apply(requestOptions)
            .into(cardView.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
        Glide.with(cardView.context).clear(cardView.mainImageView)
    }

    companion object {
        private const val CARD_WIDTH_PX = 320
        private const val CARD_HEIGHT_PX = 180
    }
}
