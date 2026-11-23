package com.faisal.tv.data.repository

import com.faisal.tv.data.api.CategoryDto
import com.faisal.tv.data.api.ChannelDto
import com.faisal.tv.data.api.FaisalApiService
import com.faisal.tv.data.api.LinksDto
import com.faisal.tv.data.api.PaginatedResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ContentRepositoryTest {

    private fun createChannel(id: Int, title: String, views: Long, category: String): ChannelDto {
        return ChannelDto(
            id = id,
            title = title,
            views = views,
            links = LinksDto(android = "http://stream/$title.m3u8", web = null, ios = null),
            categories = listOf(CategoryDto(id = 1, name = category))
        )
    }

    @Test
    fun testGetChannels_sortingAndGrouping() = runBlocking {
        val ch1 = createChannel(1, "Sports 1", 100L, "Sports")
        val ch2 = createChannel(2, "News 1", 500L, "News")
        val ch3 = createChannel(3, "Sports 2", 300L, "Sports")

        val fakeApi = object : FaisalApiService {
            override suspend fun getChannels(includes: String, limit: Int): PaginatedResponse<ChannelDto> {
                return PaginatedResponse(data = listOf(ch1, ch2, ch3))
            }
        }

        val repository = ContentRepository(fakeApi)
        val result = repository.getChannels()

        assertTrue(result is ChannelsResult.Success)
        val success = result as ChannelsResult.Success

        // Verify sorted by views descending
        assertEquals(3, success.allChannels.size)
        assertEquals("News 1", success.allChannels[0].title) // 500 views
        assertEquals("Sports 2", success.allChannels[1].title) // 300 views
        assertEquals("Sports 1", success.allChannels[2].title) // 100 views

        // Verify categories grouping
        assertEquals(2, success.categoriesMap.size)
        assertTrue(success.categoriesMap.containsKey("Sports"))
        assertTrue(success.categoriesMap.containsKey("News"))
        assertEquals(2, success.categoriesMap["Sports"]?.size)
        assertEquals(1, success.categoriesMap["News"]?.size)
    }

    @Test
    fun testGetChannels_emptyList() = runBlocking {
        val fakeApi = object : FaisalApiService {
            override suspend fun getChannels(includes: String, limit: Int): PaginatedResponse<ChannelDto> {
                return PaginatedResponse(data = emptyList())
            }
        }

        val repository = ContentRepository(fakeApi)
        val result = repository.getChannels()

        assertTrue(result is ChannelsResult.Empty)
    }

    @Test
    fun testGetChannels_forbidden403() = runBlocking {
        val fakeApi = object : FaisalApiService {
            override suspend fun getChannels(includes: String, limit: Int): PaginatedResponse<ChannelDto> {
                val errorBody = "Forbidden".toResponseBody("text/plain".toMediaTypeOrNull())
                throw HttpException(Response.error<PaginatedResponse<ChannelDto>>(403, errorBody))
            }
        }

        val repository = ContentRepository(fakeApi)
        val result = repository.getChannels()

        assertTrue(result is ChannelsResult.Forbidden403)
    }

    @Test
    fun testGetChannels_networkError() = runBlocking {
        val fakeApi = object : FaisalApiService {
            override suspend fun getChannels(includes: String, limit: Int): PaginatedResponse<ChannelDto> {
                throw IOException("Connection timed out")
            }
        }

        val repository = ContentRepository(fakeApi)
        val result = repository.getChannels()

        assertTrue(result is ChannelsResult.NetworkError)
        val error = result as ChannelsResult.NetworkError
        assertEquals("Connection timed out", error.message)
    }
}
