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
import org.pf4j.Extension

@Extension
class OnlineLyricsExtension : PlaybackExtensionPoint {
    /**
     * 当 SPW 默认逻辑无法加载歌词时触发的回调。
     *
     * - 返回 `null`：表示模组也无法提供歌词，交由 SPW 继续处理
     * - 返回 LRC 文本：SPW 将使用该歌词
     *
     * 注意：此方法在 IO 线程执行，允许进行网络请求。
     */
    override fun onAfterLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        return OnlineLyricsService.getLyrics(mediaItem)
    }
}
