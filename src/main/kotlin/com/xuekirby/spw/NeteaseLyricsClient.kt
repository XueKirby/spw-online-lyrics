/*
 * 在线歌词 (SPW Plugin)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * This file includes logic adapted from HisAtri/LrcApi (GPL-3.0).
 */

package com.xuekirby.spw

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import kotlin.math.sqrt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 网易云歌词客户端（直连 music.163.com 的公开 API）。
 *
 * 说明：
 * - SPW 插件环境可能缺少 `java.net.http.HttpClient`，因此使用 `HttpURLConnection` 实现请求。
 * - 仅实现“搜索歌曲 -> 拉取歌词”这条链路，返回 LRC 及可选的翻译/罗马字（按时间轴合并由上层决定）。
 *
 * 接口要点：
 * - 搜索：`/api/cloudsearch/pc`，取 `result.songs` 列表
 * - 歌词：`/api/song/lyric`，主要字段：
 *   - `lrc.lyric`：原文
 *   - `tlyric.lyric`：翻译（如果有）
 *   - `romalrc.lyric`：罗马字（如果有）
 */
internal class NeteaseLyricsClient {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 搜索并返回最匹配的歌词。
     *
     * 匹配策略参考 LrcApi 的实现：对 title/artist/album 做相似度计算，取 Top-N 候选再逐个拉取歌词。
     */
    fun searchBestLyrics(
        title: String,
        artist: String,
        album: String,
        config: OnlineLyricsConfig
    ): OnlineLyricsResult? {
        val searchStr = listOf(title, artist, album).filter { it.isNotBlank() }.joinToString(" ")
        val encoded = URLEncoder.encode(searchStr, StandardCharsets.UTF_8)
        val url =
            "https://music.163.com/api/cloudsearch/pc?s=$encoded&type=1&offset=0&limit=100"
        val body = httpGet(url, config.timeoutMs) ?: return null
        val songs = runCatching {
            json.decodeFromString(CloudSearchResponse.serializer(), body).result?.songs.orEmpty()
        }.getOrNull() ?: return null

        if (songs.isEmpty()) return null

        val candidates = songs.mapNotNull { song ->
            val songNames = (song.aliases.orEmpty() + song.name).filter { it.isNotBlank() }
            val titleRatio = songNames.maxOfOrNull { TextCompare.association(title, it) } ?: 0.0

            val singerName = song.artists.orEmpty().joinToString(" ") { it.name }
            val albumName = song.album?.name.orEmpty()

            val artistRatio = TextCompare.assocArtists(artist, singerName)
            val albumRatio = TextCompare.association(album, albumName)

            val ratio = sqrt(titleRatio * (artistRatio + albumRatio) / 2.0)
            if (ratio < config.minSimilarity) return@mapNotNull null

            Candidate(
                ratio = ratio,
                trackId = song.id,
            )
        }.sortedByDescending { it.ratio }
            .take(config.maxCandidates.coerceIn(1, 10))

        for (candidate in candidates) {
            val lyrics = fetchLyrics(candidate.trackId, config)
            if (lyrics != null) return lyrics
        }

        return null
    }

    private fun fetchLyrics(trackId: Long, config: OnlineLyricsConfig): OnlineLyricsResult? {
        val url = "https://music.163.com/api/song/lyric?id=$trackId&lv=1&tv=1&rv=1"
        val body = httpGet(url, config.timeoutMs) ?: return null
        val payload = runCatching {
            json.decodeFromString(LyricApiResponse.serializer(), body)
        }.getOrNull() ?: return null

        val lrc = payload.lrc?.lyric
        if (lrc.isNullOrBlank()) return null

        return OnlineLyricsResult(
            lrc = lrc,
            translationLrc = payload.translation?.lyric,
            romajiLrc = payload.romaji?.lyric,
        )
    }

    /**
     * 简单的 GET 请求封装。
     *
     * - 不引入额外网络库，降低对 SPW 运行时的要求
     * - 支持 gzip
     */
    private fun httpGet(url: String, timeoutMs: Int): String? {
        val connection = runCatching {
            URI.create(url).toURL().openConnection() as java.net.HttpURLConnection
        }.getOrNull() ?: return null
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("user-agent", USER_AGENT)
        connection.setRequestProperty("origin", "https://music.163.com")
        connection.setRequestProperty("referer", "https://music.163.com")
        connection.setRequestProperty("X-Real-IP", "118.88.88.88")
        connection.setRequestProperty("accept", "application/json")
        connection.setRequestProperty("accept-encoding", "gzip")

        return runCatching {
            val code = connection.responseCode
            if (code !in 200..299) return@runCatching null

            val encoding = connection.contentEncoding?.lowercase()
            val inputStream = connection.inputStream
            val decodedStream = if (encoding == "gzip") GZIPInputStream(inputStream) else inputStream
            decodedStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private data class Candidate(
        val ratio: Double,
        val trackId: Long,
    )

    @Serializable
    private data class CloudSearchResponse(
        val result: CloudSearchResult? = null
    )

    @Serializable
    private data class CloudSearchResult(
        val songs: List<Song>? = null
    )

    @Serializable
    private data class Song(
        val id: Long,
        val name: String,
        @SerialName("alia")
        val aliases: List<String>? = null,
        @SerialName("ar")
        val artists: List<Artist>? = null,
        @SerialName("al")
        val album: Album? = null
    )

    @Serializable
    private data class Artist(
        val name: String
    )

    @Serializable
    private data class Album(
        val name: String
    )

    @Serializable
    private data class LyricApiResponse(
        val lrc: LyricContent? = null,
        @SerialName("romalrc")
        val romaji: LyricContent? = null,
        @SerialName("tlyric")
        val translation: LyricContent? = null
    )

    @Serializable
    private data class LyricContent(
        val lyric: String? = null
    )

    private companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0"
    }
}
