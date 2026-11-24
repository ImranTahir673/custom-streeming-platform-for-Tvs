package com.faisal.tv.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.faisal.tv.R
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.UrlSanitizer

class MiniChannelAdapter(
    private val channels: List<ChannelDto>,
    private val onChannelSelected: (ChannelDto, Int) -> Unit
) : RecyclerView.Adapter<MiniChannelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.mini_poster)
        val number: TextView = view.findViewById(R.id.mini_number)
        val title: TextView = view.findViewById(R.id.mini_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_channel, parent, false)
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(120)
                .start()

            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_glass_card_focused)
                v.elevation = 8f
            } else {
                v.setBackgroundResource(R.drawable.bg_glass_card_normal)
                v.elevation = 2f
            }
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.title.text = channel.title
        if (channel.channelNumber > 0) {
            holder.number.text = "${channel.channelNumber}"
            holder.number.visibility = View.VISIBLE
        } else {
            holder.number.visibility = View.GONE
        }

        val posterUrl = UrlSanitizer.sanitizeUrl(channel.poster)
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(140, 90)
            .centerCrop()

        Glide.with(holder.itemView.context)
            .load(posterUrl)
            .apply(options)
            .into(holder.poster)

        holder.itemView.setOnClickListener {
            onChannelSelected(channel, position)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.poster)
    }

    override fun getItemCount(): Int = channels.size
}
