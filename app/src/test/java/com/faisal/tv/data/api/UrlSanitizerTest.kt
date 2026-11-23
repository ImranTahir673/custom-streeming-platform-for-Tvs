package com.faisal.tv.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun testSanitizeUrl_encodesSpaces() {
        val raw = "http://10.15.237.10:1935/live/GEO SUPER.stream/playlist.m3u8"
        val expected = "http://10.15.237.10:1935/live/GEO%20SUPER.stream/playlist.m3u8"
        assertEquals(expected, UrlSanitizer.sanitizeUrl(raw))
    }

    @Test
    fun testSanitizeUrl_trimsWhitespace() {
        val raw = "   http://10.15.237.10:1935/live/HUM TV.stream/playlist.m3u8   "
        val expected = "http://10.15.237.10:1935/live/HUM%20TV.stream/playlist.m3u8"
        assertEquals(expected, UrlSanitizer.sanitizeUrl(raw))
    }

    @Test
    fun testSanitizeUrl_handlesNullAndEmpty() {
        assertNull(UrlSanitizer.sanitizeUrl(null))
        assertNull(UrlSanitizer.sanitizeUrl(""))
        assertNull(UrlSanitizer.sanitizeUrl("    "))
    }

    @Test
    fun testGetStreamUrl_priorityAndroid() {
        val links = LinksDto(
            android = "http://server/live/GEO SUPER.stream/playlist.m3u8",
            web = "http://server/live/web.m3u8",
            ios = "http://server/live/ios.m3u8"
        )
        assertEquals("http://server/live/GEO%20SUPER.stream/playlist.m3u8", UrlSanitizer.getStreamUrl(links))
    }

    @Test
    fun testGetStreamUrl_fallbackWeb() {
        val links = LinksDto(
            android = null,
            web = "http://server/live/WEB STREAM.m3u8",
            ios = "http://server/live/ios.m3u8"
        )
        assertEquals("http://server/live/WEB%20STREAM.m3u8", UrlSanitizer.getStreamUrl(links))
    }

    @Test
    fun testGetStreamUrl_fallbackIos() {
        val links = LinksDto(
            android = "",
            web = "   ",
            ios = "http://server/live/IOS STREAM.m3u8"
        )
        assertEquals("http://server/live/IOS%20STREAM.m3u8", UrlSanitizer.getStreamUrl(links))
    }

    @Test
    fun testChannelDtoParsing_fromRealApiResponse() {
        val sampleJson = """
        {
            "data": [
                {
                    "id": 60,
                    "title": "Geo Super",
                    "poster": "http://islamabad.streamhome.co/uploads/webtv/613789839815f36d9dbd620734417e2b.jpg",
                    "added_date": "Apr 09, 2023",
                    "views": 199061,
                    "status": 0,
                    "links": {
                        "web": "http://10.15.237.10:1935/live/GEO SUPER.stream/playlist.m3u8",
                        "android": "http://10.15.237.10:1935/live/GEO SUPER.stream/playlist.m3u8",
                        "ios": "http://10.15.237.10:1935/live/GEO SUPER.stream/playlist.m3u8"
                    },
                    "categories": [
                        {"id": 2, "name": "Sports"}
                    ]
                }
            ],
            "meta": {
                "current_page": 1,
                "from": 1,
                "last_page": 1,
                "per_page": 250,
                "to": 56,
                "total": 56
            }
        }
        """.trimIndent()

        val gson = Gson()
        val type = object : TypeToken<PaginatedResponse<ChannelDto>>() {}.type
        val response: PaginatedResponse<ChannelDto> = gson.fromJson(sampleJson, type)

        assertNotNull(response)
        assertEquals(1, response.data.size)
        val channel = response.data[0]
        assertEquals(60, channel.id)
        assertEquals("Geo Super", channel.title)
        assertEquals(199061L, channel.views)
        assertEquals("Sports", channel.categories[0].name)
        val streamUrl = UrlSanitizer.getStreamUrl(channel.links)
        assertEquals("http://10.15.237.10:1935/live/GEO%20SUPER.stream/playlist.m3u8", streamUrl)
    }
}
