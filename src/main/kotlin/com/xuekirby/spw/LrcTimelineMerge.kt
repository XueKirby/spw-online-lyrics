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
 * This file includes logic adapted from HisAtri/LrcApi (GPL-3.0),
 * originally located at `core/api/netease.py`.
 */

package com.xuekirby.spw

import java.util.SortedSet
import java.util.TreeSet

/**
 * 将多个 LRC 文本按时间轴合并。
 *
 * 约定：
 * - 输出时同时间戳先输出原文（base），再输出副歌词（companion）
 * - 仅合并带时间戳的行；元数据行（如 `[ar:]`）从 base 中保留
 */
internal object LrcTimelineMerge {
    private val timestampRegex = Regex("^(\\[\\d+:\\d+\\.\\d+])")

    fun merge(baseLrc: String, companionLrcs: List<String>): String {
        val normalizedCompanions = companionLrcs.filter { it.isNotBlank() }
        if (normalizedCompanions.isEmpty()) return baseLrc

        val baseMap = parseLrc(baseLrc)
        val companionMaps = normalizedCompanions.map { parseLrc(it) }
        if (companionMaps.all { it.isEmpty() }) return baseLrc

        val resultLines = ArrayList<String>()

        for (line in baseLrc.lineSequence()) {
            if (!timestampRegex.containsMatchIn(line)) {
                resultLines.add(line)
            }
        }

        val allTimestamps: SortedSet<String> = TreeSet<String>().apply {
            addAll(baseMap.keys)
            for (m in companionMaps) addAll(m.keys)
        }

        for (ts in allTimestamps) {
            val base = baseMap[ts]
            if (!base.isNullOrBlank()) {
                resultLines.add(ts + base)
            }

            for (companion in companionMaps) {
                val extra = companion[ts]
                if (!extra.isNullOrBlank() && extra != base) {
                    resultLines.add(ts + extra)
                }
            }
        }

        return resultLines.joinToString("\n")
    }

    private fun parseLrc(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()

        val result = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val match = timestampRegex.find(line) ?: continue
            val ts = match.groupValues[1]
            val content = line.removePrefix(ts).trim()
            if (content.isNotEmpty()) {
                result[ts] = content
            }
        }
        return result
    }
}
