package com.faisal.tv.util

import com.faisal.tv.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTintHelperTest {

    @Test
    fun testCategoryTintMapping() {
        assertEquals(R.drawable.bg_tint_sports, CategoryTintHelper.getBackgroundRes("Sports"))
        assertEquals(R.drawable.bg_tint_sports, CategoryTintHelper.getBackgroundRes("SPORTS LIVE"))
        assertEquals(R.drawable.bg_tint_news, CategoryTintHelper.getBackgroundRes("News Channels"))
        assertEquals(R.drawable.bg_tint_entertainment, CategoryTintHelper.getBackgroundRes("Entertainment HD"))
        assertEquals(R.drawable.bg_tint_drama, CategoryTintHelper.getBackgroundRes("Pakistani Dramas"))
        assertEquals(R.drawable.bg_tint_music, CategoryTintHelper.getBackgroundRes("Music TV"))
        assertEquals(R.drawable.bg_tint_kids, CategoryTintHelper.getBackgroundRes("Kids Cartoons"))
        assertEquals(R.drawable.bg_tint_movies, CategoryTintHelper.getBackgroundRes("Action Movies"))
        assertEquals(R.drawable.bg_tint_default, CategoryTintHelper.getBackgroundRes("Random Category"))
        assertEquals(R.drawable.bg_tint_default, CategoryTintHelper.getBackgroundRes(null))
    }
}
