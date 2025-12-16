@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

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

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责：
 * - 读取模组配置
 * - 生成缓存键、进行简单缓存
 * - 调用在线歌词客户端并返回结果
 *
 * 注意：这里不做复杂的歌词解析/转换，仅以“返回 LRC 文本”为目标。
 *
 * 二次开发建议：
 * - 接入新歌词源：实现 [OnlineLyricsProvider] 并调用 [registerProvider] 注册。
 * - 尽量保证返回的是标准 LRC 文本（含时间戳），以适配 SPW 的解析器（主/副歌词由 SPW 解析）。
 */
object OnlineLyricsService {
    private val providers = CopyOnWriteArrayList<OnlineLyricsProvider>()
        .apply { add(NeteaseLyricsProvider(NeteaseLyricsClient())) }

    private val cache = ConcurrentHashMap<String, CachedLyrics>()
    private const val cacheTtlMs: Long = 10 * 60 * 1000L

    @Volatile
    private var configHelper: ConfigHelper? = null

    /**
     * 获取歌词。
     *
     * @return LRC 文本或 `null`（表示无法提供）
     */
    fun getLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        val request = OnlineLyricsRequest.fromMediaItem(mediaItem) ?: return null
        val config = loadConfig()
        if (!config.enabled) return null

        val cacheKey = buildString {
            append(request.title.lowercase())
            append('|')
            append(request.artist.lowercase())
            append('|')
            append(request.album.lowercase())
            append('|')
            append(config.subLyricsMode.name)
        }

        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (now - cached.timestampMs <= cacheTtlMs) {
                return cached.lyrics
            }
            cache.remove(cacheKey)
        }

        for (provider in providers) {
            val result = runCatching { provider.fetch(request, config) }.getOrNull() ?: continue
            val lyrics = buildLyricsText(result, config)
            if (lyrics != null) {
                cache[cacheKey] = CachedLyrics(lyrics = lyrics, timestampMs = now)
                return lyrics
            }
        }

        return null
    }

    /**
     * 注册新的在线歌词提供方。
     *
     * - 同一 provider 实例不会重复加入
     * - 查找顺序：按注册顺序依次尝试，直到返回非空歌词
     */
    fun registerProvider(provider: OnlineLyricsProvider) {
        if (!providers.contains(provider)) {
            providers.add(provider)
        }
    }

    /**
     * 移除一个在线歌词提供方。
     */
    fun unregisterProvider(provider: OnlineLyricsProvider) {
        providers.remove(provider)
    }

    /**
     * 从 SPW 的 ConfigManager 获取模组配置。
     *
     * - 读取失败时回退到默认配置
     * - 会尝试兼容旧键名（`netease.enable_translation` / `netease.enable_romaji`）
     */
    private fun loadConfig(): OnlineLyricsConfig {
        val helper = configHelper ?: runCatching {
            WorkshopApi.manager.createConfigManager().getConfig()
        }.getOrNull().also { configHelper = it }

        if (helper == null) return OnlineLyricsConfig()

        runCatching { helper.reload() }

        val subLyricsMode = runCatching {
            val raw = helper.get("netease.sub_lyrics", "none")
            SubLyricsMode.fromConfigValue(raw)
        }.getOrNull() ?: run {
            val enableTranslation = runCatching {
                helper.get("netease.enable_translation", false)
            }.getOrDefault(false)
            val enableRomaji = runCatching {
                helper.get("netease.enable_romaji", false)
            }.getOrDefault(false)

            when {
                enableTranslation -> SubLyricsMode.Translation
                enableRomaji -> SubLyricsMode.Romaji
                else -> SubLyricsMode.None
            }
        }

        return OnlineLyricsConfig(
            enabled = helper.get("enabled", true),
            subLyricsMode = subLyricsMode,
            minSimilarity = helper.get("netease.min_similarity", 0.2),
            maxCandidates = helper.get("netease.max_candidates", 3),
            timeoutMs = helper.get("netease.timeout_ms", 8000),
        )
    }

    private fun buildLyricsText(result: OnlineLyricsResult, config: OnlineLyricsConfig): String? {
        val base = result.lrc.takeIf { it.isNotBlank() } ?: return null
        val companion = when (config.subLyricsMode) {
            SubLyricsMode.None -> null
            SubLyricsMode.Romaji -> result.romajiLrc
            SubLyricsMode.Translation -> result.translationLrc
        }?.takeIf { it.isNotBlank() }

        return if (companion == null) {
            base
        } else {
            LrcTimelineMerge.merge(baseLrc = base, companionLrcs = listOf(companion))
        }
    }

    private data class CachedLyrics(
        val lyrics: String?,
        val timestampMs: Long
    )
}

data class OnlineLyricsConfig(
    /**
     * 总开关：
     * - `true`：在 SPW 默认歌词加载失败后尝试联网获取
     * - `false`：完全禁用模组的歌词返回（始终返回 null）
     */
    val enabled: Boolean = true,

    /**
     * 副歌词行内容来源（同时间戳输出顺序见 [SubLyricsMode] 说明）。
     */
    val subLyricsMode: SubLyricsMode = SubLyricsMode.None,

    /**
     * 网易云候选筛选的最小相似度阈值，范围约 0~1。
     *
     * 值越大越严格，误匹配更少，但更可能找不到歌词。
     */
    val minSimilarity: Double = 0.2,

    /**
     * 拉取歌词前最多尝试的候选数量（按相似度排序后的 Top-N）。
     */
    val maxCandidates: Int = 3,

    /**
     * 网络请求超时（毫秒）。
     *
     * 注意：此处仅为客户端超时；SPW 对模组 IO 线程的调度/取消策略由其自身控制。
     */
    val timeoutMs: Int = 8000,
)

/**
 * 副歌词显示模式。
 *
 * 注意：SPW 的“歌词翻译/翻译显示”通常对应副歌词行（同时间戳的第二行），因此本模组默认输出顺序为：
 * - 同一时间戳先输出原文（主歌词行）
 * - 再输出翻译/罗马字（副歌词行）
 */
enum class SubLyricsMode {
    None,
    Romaji,
    Translation;

    companion object {
        fun fromConfigValue(value: String): SubLyricsMode {
            return when (value.trim().lowercase()) {
                "none", "off", "false", "0", "不显示" -> None
                "romaji", "roma", "rm", "罗马字" -> Romaji
                "translation", "trans", "tl", "翻译" -> Translation
                else -> None
            }
        }
    }
}

data class OnlineLyricsRequest(
    val title: String,
    val artist: String,
    val album: String
) {
    companion object {
        fun fromMediaItem(mediaItem: PlaybackExtensionPoint.MediaItem): OnlineLyricsRequest? {
            val title = mediaItem.title.trim()
            if (title.isEmpty()) return null
            return OnlineLyricsRequest(
                title = title,
                artist = mediaItem.artist.trim(),
                album = mediaItem.album.trim(),
            )
        }
    }
}

data class OnlineLyricsResult(
    val lrc: String,
    val translationLrc: String? = null,
    val romajiLrc: String? = null
)

interface OnlineLyricsProvider {
    /**
     * 返回 `null` 表示该 provider 无法提供歌词（继续尝试下一个 provider）。
     */
    fun fetch(request: OnlineLyricsRequest, config: OnlineLyricsConfig): OnlineLyricsResult?
}

private class NeteaseLyricsProvider(
    private val client: NeteaseLyricsClient
) : OnlineLyricsProvider {
    override fun fetch(request: OnlineLyricsRequest, config: OnlineLyricsConfig): OnlineLyricsResult? {
        return client.searchBestLyrics(
            title = request.title,
            artist = request.artist,
            album = request.album,
            config = config
        )
    }
}
