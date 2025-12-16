@file:Suppress("unused")

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

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import com.xuncorp.spw.workshop.api.WorkshopApi

/**
 * 模组入口。
 *
 * 注意：在线歌词逻辑在 [OnlineLyricsExtension] 内实现（PlaybackExtensionPoint）。
 */
class OnlineLyricsPlugin(
    pluginContext: PluginContext
) : SpwPlugin(pluginContext) {
    override fun start() {
        WorkshopApi.ui.toast("在线歌词 已启动", WorkshopApi.Ui.ToastType.Success)
    }

    override fun stop() {
        WorkshopApi.ui.toast("在线歌词 已停止", WorkshopApi.Ui.ToastType.Warning)
    }
}
