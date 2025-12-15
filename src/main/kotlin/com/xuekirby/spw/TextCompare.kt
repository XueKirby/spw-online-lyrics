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
 * originally located at `utils/textcompare.py`.
 */

package com.xuekirby.spw

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 文本相似度与艺术家匹配工具。
 *
 * 用途：网易云搜索结果中筛选出更可能匹配当前播放曲目的候选项。
 * 实现目标是“足够好用”，不是严格的 NLP/模糊匹配最佳实践。
 */
internal object TextCompare {
    private val artistSplitRegex = Regex("[,\\\\& +|、，/]+")

    fun association(text1: String, text2: String): Double {
        if (text1.isEmpty()) return 0.5
        if (text2.isEmpty()) return 0.0

        val a = text1.lowercase()
        val b = text2.lowercase()

        val commonRatio = longestCommonSubstringLength(a, b).toDouble() / a.length.toDouble()
        val duplicateRate = strDuplicateRate(a, b)
        return commonRatio * (sqrt(duplicateRate).pow(1.0 / 1.5))
    }

    fun assocArtists(text1: String, text2: String): Double {
        if (text1.isEmpty()) return 0.5

        val list1 = artistSplitRegex.split(text1).filter { it.isNotBlank() }
        val list2 = artistSplitRegex.split(text2).filter { it.isNotBlank() }

        return calculateDuplicateRate(list1, list2)
    }

    private fun calculateDuplicateRate(list1: List<String>, list2: List<String>): Double {
        if (list1.isEmpty()) return 0.0
        if (list2.isEmpty()) return 0.0

        var sum = 0.0
        for (token in list1) {
            var maxSim = 0.0
            for (candidate in list2) {
                val sim = association(token, candidate)
                if (sim > maxSim) maxSim = sim
            }
            sum += maxSim
        }
        return sum / list1.size.toDouble()
    }

    private fun strDuplicateRate(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val setA = a.toSet()
        val setB = b.toSet()
        val common = setA intersect setB
        val total = setA union setB
        if (total.isEmpty()) return 0.0
        return common.size.toDouble() / total.size.toDouble()
    }

    private fun longestCommonSubstringLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0

        val n = b.length
        var previous = IntArray(n + 1)
        var current = IntArray(n + 1)
        var maxLength = 0

        for (i in 1..a.length) {
            val ca = a[i - 1]
            for (j in 1..n) {
                if (ca == b[j - 1]) {
                    current[j] = previous[j - 1] + 1
                    if (current[j] > maxLength) maxLength = current[j]
                } else {
                    current[j] = 0
                }
            }
            val tmp = previous
            previous = current
            current = tmp
            current.fill(0)
        }

        return maxLength
    }
}
