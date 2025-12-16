# 在线歌词（SPW 创意工坊模组）

Salt Player for Windows（SPW）创意工坊模组：当 SPW 默认逻辑无法加载歌词时，从在线服务（网易云音乐接口）检索并返回 LRC 歌词。

## 功能

- 在线检索：使用网易云 `cloudsearch` + `lyric` 接口获取 LRC
- 副歌词行：可选择 `不显示 / 罗马字 / 翻译`（网易云返回 `romalrc` / `tlyric`）

## 安装与启用

1. 将构建好的模组包（`.zip`）复制到：
   `%APPDATA%/Salt Player for Windows/workshop/plugins/`
2. 重启 SPW
3. 打开 `设置 → 创意工坊 → 模组管理`，找到“在线歌词”并启用

说明：SPW 默认禁用新安装的模组，需要手动启用一次。

## 配置

在 SPW 的 `设置 → 创意工坊 → 模组配置` 中设置（配置项由 `preference_config.json` 定义）。

- `enabled`: 是否启用在线歌词功能
- `netease.sub_lyrics`: 副歌词行显示（`none` / `romaji` / `translation`）

提示：副歌词行是否显示由 SPW 的“翻译显示/歌词翻译”开关控制；未开启时仅显示主歌词行。

## 构建

- 在本仓库根目录：`./gradlew :spw-online-lyrics:plugin`
- 在独立仓库根目录：`./gradlew plugin`

## 许可证

本模组基于 GPL-3.0 许可证开源，详见 [LICENSE](./LICENSE)。

## 致谢

在线歌词获取与匹配逻辑改写自 [HisAtri/LrcApi](https://github.com/HisAtri/LrcApi) 的网易云实现（GPL-3.0）。

## 仓库

https://github.com/XueKirby/spw-online-lyrics
