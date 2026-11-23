package com.faisal.tv.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer

class ChannelCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH_PX, CARD_HEIGHT_PX)
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg_card))

            setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1.0f
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start()

                if (hasFocus) {
                    val border = GradientDrawable().apply {
                        setColor(ContextCompat.getColor(context, R.color.bg_card))
                        setStroke(BORDER_WIDTH_PX, ContextCompat.getColor(context, R.color.accent_focus))
                        cornerRadius = CORNER_RADIUS_PX
                    }
                    view.background = border
                } else {
                    val normal = GradientDrawable().apply {
                        setColor(ContextCompat.getColor(context, R.color.bg_card))
                        cornerRadius = CORNER_RADIUS_PX
                    }
                    view.background = normal
                }
            }
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val channel = item as? ChannelDto ?: return
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = channel.title
        cardView.contentText = channel.categories.firstOrNull()?.name ?: ""

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
        private const val CARD_WIDTH_PX = 330
        private const val CARD_HEIGHT_PX = 186
        private const val BORDER_WIDTH_PX = 4
        private const val CORNER_RADIUS_PX = 12f
    }
}
