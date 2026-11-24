package com.faisal.tv.data.repository

import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.FaisalApiService
import retrofit2.HttpException
import java.io.IOException

sealed class ChannelsResult {
    data class Success(
        val allChannels: List<ChannelDto>,
        val categoriesMap: Map<String, List<ChannelDto>>
    ) : ChannelsResult()

    object Forbidden403 : ChannelsResult()
    data class NetworkError(val message: String?) : ChannelsResult()
    object Empty : ChannelsResult()
}

class ContentRepository(
    private val apiService: FaisalApiService
) {
    // In-memory cache for instant back-navigation
    private var cachedResult: ChannelsResult? = null

    suspend fun getChannels(forceRefresh: Boolean = false): ChannelsResult {
        if (!forceRefresh && cachedResult is ChannelsResult.Success) {
            return cachedResult!!
        }

        return try {
            val response = apiService.getChannels()
            val channels = response.data
            if (channels.isEmpty()) {
                val emptyResult = ChannelsResult.Empty
                cachedResult = emptyResult
                emptyResult
            } else {
                // "All Channels" sorted by views descending
                val sortedAll = channels.sortedByDescending { it.views }
                sortedAll.forEachIndexed { index, channel ->
                    channel.channelNumber = index + 1
                }

                // Group by categories[0].name
                val grouped = LinkedHashMap<String, MutableList<ChannelDto>>()
                for (channel in channels) {
                    val categoryName = channel.categories.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "General"
                    grouped.getOrPut(categoryName) { mutableListOf() }.add(channel)
                }

                val successResult = ChannelsResult.Success(
                    allChannels = sortedAll,
                    categoriesMap = grouped
                )
                cachedResult = successResult
                successResult
            }
        } catch (e: HttpException) {
            if (e.code() == 403) {
                ChannelsResult.Forbidden403
            } else {
                ChannelsResult.NetworkError("HTTP ${e.code()}: ${e.message()}")
            }
        } catch (e: IOException) {
            ChannelsResult.NetworkError(e.message ?: "Network unreachable")
        } catch (e: Exception) {
            ChannelsResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    fun clearCache() {
        cachedResult = null
    }
}
