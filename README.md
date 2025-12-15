# 在线歌词

Salt Player for Windows（SPW）创意工坊插件：当 SPW 默认逻辑无法加载歌词时，从在线服务（网易云音乐接口）检索并返回 LRC 歌词。

## 功能

- 在线检索：使用网易云 `cloudsearch` + `lyric` 接口获取 LRC
- 副歌词行：可选择 `不显示 / 罗马字 / 翻译`（网易云返回 `romalrc` / `tlyric`）

## 配置

在 SPW 的 创意工坊 → 模组配置 中设置（配置项由 `preference_config.json` 定义）。

- `enabled`: 是否启用在线兜底
- `netease.sub_lyrics`: 副歌词行显示（`none` / `romaji` / `translation`）

## 许可证

本插件基于 GPL-3.0 许可证开源，详见 [LICENSE](./LICENSE)。

## 致谢

在线歌词获取与匹配逻辑改写自 [HisAtri/LrcApi](https://github.com/HisAtri/LrcApi) 的网易云实现（GPL-3.0）。

## 仓库

https://github.com/XueKirby/spw-online-lyrics
