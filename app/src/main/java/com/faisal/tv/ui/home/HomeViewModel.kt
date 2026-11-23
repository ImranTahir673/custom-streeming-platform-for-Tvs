package com.faisal.tv.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faisal.tv.data.repository.ChannelsResult
import com.faisal.tv.data.repository.ContentRepository
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ContentRepository
) : ViewModel() {

    private val _channelsState = MutableLiveData<ChannelsResult>()
    val channelsState: LiveData<ChannelsResult> = _channelsState

    fun loadChannels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val result = repository.getChannels(forceRefresh)
            _channelsState.value = result
        }
    }
}
