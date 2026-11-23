package com.faisal.tv.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface FaisalApiService {
    @GET("channels")
    suspend fun getChannels(
        @Query("includes") includes: String = "categories",
        @Query("limit") limit: Int = 250
    ): PaginatedResponse<ChannelDto>
}
