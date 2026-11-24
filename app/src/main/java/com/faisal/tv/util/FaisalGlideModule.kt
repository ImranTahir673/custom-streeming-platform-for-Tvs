package com.faisal.tv.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.GlideModule
import com.bumptech.glide.request.RequestOptions

/**
 * Glide configuration enforcing strict memory caps for 512MB RAM TVs:
 * - 16 MB memory cache cap
 * - 50 MB internal disk cache cap
 * - RGB_565 decode format (cuts bitmap memory in half vs ARGB_8888)
 */
@Suppress("DEPRECATION")
class FaisalGlideModule : GlideModule {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val memoryCacheSizeBytes = 16 * 1024 * 1024L // 16 MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))

        val diskCacheSizeBytes = 50 * 1024 * 1024L // 50 MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))

        builder.setDefaultRequestOptions(
            RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // No custom component registration needed
    }
}
