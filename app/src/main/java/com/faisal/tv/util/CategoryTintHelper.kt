package com.faisal.tv.util

import com.faisal.tv.R

object CategoryTintHelper {

    fun getBackgroundRes(categoryName: String?): Int {
        val lower = categoryName?.lowercase() ?: ""
        return when {
            lower.contains("sport") -> R.drawable.bg_tint_sports
            lower.contains("news") -> R.drawable.bg_tint_news
            lower.contains("entertainment") -> R.drawable.bg_tint_entertainment
            lower.contains("drama") -> R.drawable.bg_tint_drama
            lower.contains("music") -> R.drawable.bg_tint_music
            lower.contains("kid") || lower.contains("cartoon") -> R.drawable.bg_tint_kids
            lower.contains("movie") || lower.contains("film") -> R.drawable.bg_tint_movies
            else -> R.drawable.bg_tint_default
        }
    }
}
