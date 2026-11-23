package com.faisal.tv

import android.app.Application
import com.faisal.tv.data.api.NetworkModule
import com.faisal.tv.data.repository.ContentRepository

class FaisalTvApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}

class AppContainer {
    val apiService by lazy { NetworkModule.createApiService() }
    val repository by lazy { ContentRepository(apiService) }
}
