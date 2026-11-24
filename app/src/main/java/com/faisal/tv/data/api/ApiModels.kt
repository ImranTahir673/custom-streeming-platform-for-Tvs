package com.faisal.tv.data.api

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class PaginatedResponse<T>(
    @SerializedName("data") val data: List<T> = emptyList(),
    @SerializedName("links") val links: PaginationLinksDto? = null,
    @SerializedName("meta") val meta: MetaDto? = null
)

@Keep
data class PaginationLinksDto(
    @SerializedName("first") val first: String? = null,
    @SerializedName("last") val last: String? = null,
    @SerializedName("prev") val prev: String? = null,
    @SerializedName("next") val next: String? = null
)

@Keep
data class MetaDto(
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("from") val from: Int = 0,
    @SerializedName("last_page") val lastPage: Int = 1,
    @SerializedName("path") val path: String? = null,
    @SerializedName("per_page") val perPage: Int = 250,
    @SerializedName("to") val to: Int = 0,
    @SerializedName("total") val total: Int = 0
)

@Keep
data class ChannelDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("added_date") val addedDate: String? = null,
    @SerializedName("views") val views: Long = 0,
    @SerializedName("status") val status: Int = 0,
    @SerializedName("links") val links: LinksDto? = null,
    @SerializedName("categories") val categories: List<CategoryDto> = emptyList(),
    var channelNumber: Int = 0
) : java.io.Serializable

@Keep
data class LinksDto(
    @SerializedName("android") val android: String? = null,
    @SerializedName("web") val web: String? = null,
    @SerializedName("ios") val ios: String? = null
) : java.io.Serializable

@Keep
data class CategoryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
) : java.io.Serializable
