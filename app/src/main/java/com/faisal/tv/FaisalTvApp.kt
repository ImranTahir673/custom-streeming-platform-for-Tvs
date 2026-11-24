package com.faisal.tv

import android.app.Application
import com.bumptech.glide.Glide
import com.faisal.tv.data.api.NetworkModule
import com.faisal.tv.data.repository.ContentRepository

class FaisalTvApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).onLowMemory()
    }
}

class AppContainer(context: android.content.Context) {
    val apiService by lazy { NetworkModule.createApiService() }
    val repository by lazy { ContentRepository(apiService) }
    val prefsStore by lazy { com.faisal.tv.data.local.PrefsStore(context) }
}

