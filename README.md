# 🍅 PomoSolo Android（安卓端部门）

> **部门：安卓端部门** ｜ 成立：2026-09-10 ｜ 状态：🚧 **v1 开发中**（原生落地） ｜ 仓库：[PomoSolo-Android](https://github.com/liaowenqi123/PomoSolo-Android)
>
> 上级项目：**PomoSolo**（番茄钟专注应用，主仓库 `liaowenqi123/PomoSolo`，Tauri 桌面端 + PWA + 自建服务器）。
> 团队协作、部门分工、接口权威文档见主仓库 [TEAM_GUIDE.md](D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/TEAM_GUIDE.md)。

## 这是什么

PomoSolo 的 **Android 原生应用（V1）**。代码全部在本工程内**自研**，不依赖任何 PWA 构建产物，
不复用原前端代码；仅把桌面端/PWA 的 Vue 源码当作「界面长什么样 / 交互是什么」的参考蓝本。

> **路线定调（2026-09-10）**：安卓端**不再把 PWA 构建产物当作应用内容**。v0 的 WebView+PWA
> 验证壳已从本仓库移除（保留在 git 历史），**v1 起以桌面端 Vue 源码（`src/`）为参考、原生实现
> 界面与业务**，并叠加安卓原生能力 —— 数据真正落盘到 App 私有文件，不再受
> Service Worker / Cache API / IndexedDB 在 WebView 中的妥协与限制约束。
>
> 本阶段验证结论（v1 探测）：服务器当前只在 `/music/*` 托管 3 首内置曲（`api.pomogrow.top`
> 与 `start.pomogrow.top` 均可达）；曲库清单在 `start.pomogrow.top/music-manifest.json`。
> 曲库会随服务器扩展增长 —— 下载层按清单通用实现，曲目多了自然可用。

### V1 里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **m1 · 下载功能原生化** | Kotlin+Compose 原生壳；服务器曲库拉取 / 内置曲种子 / 下载→`filesDir` 落盘 / 本地导入(SAF) / 离线播放 / 状态持久化 | ✅ 完成 |
| **m2 · 播放体验** | 下载续传与取消、后台播放 + 系统通知、锁屏媒体控制(MediaSession) | ⏳ 待办 |
| **m3 · 计时与专注** | 番茄钟原生前台服务精确计时、桌面端同款首页 UI | ⏳ 待办 |
| **m4 · 云端** | 账号体系（与服务器 REST/WS 打通）、自习室 WS、P2P 传歌 | ⏳ 规划 |

### 与 PWA 部门的关系（分工边界）

- **共享**：产品功能定义、服务器接口（REST / WS / P2P）、账号体系 —— 接口改动必须同步主仓库 `server-planning/` 文档；
- **参考**：桌面端 Vue `src/` 与 PWA 的 `src/pwa/` 源码仅作界面/交互参考，不 copy、不引用其代码；
- **不依赖**：安卓交付内容与 PWA 构建产物解耦；PWA 在浏览器里做不到的（可靠文件下载、前台检测、系统媒体控制…）安卓端用原生方案达成。

## 技术栈（V1 原生）

| 项 | 方案 | 说明 |
|----|------|------|
| 语言 | Kotlin 1.9 + Jetpack Compose (Material3) | 单 Activity 原生 UI，无 WebView |
| 播放 | Media3 ExoPlayer 1.3 | 本地 `file://` + 在线流统一播放，进度/播放态 StateFlow 驱动 UI |
| 网络 | OkHttp 4.12 | 曲库清单拉取；下载用流式写入 `.part` → 原子改名，进度 0–100 |
| 存储 | `filesDir/music/` + `index.json` | 下载/导入状态持久化，重启可识别已下载内容 |
| 导入 | SAF（系统文件选择器） | 把用户本地音频拷入私有目录统一管理 |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 26 | JDK 17 |

## 目录结构

```
pomodoro/
├── app/src/main/java/com/pomogrow/pomosolo/
│   ├── MainActivity.kt            # Compose 单 Activity（V1 主入口）
│   ├── data/
│   │   ├── Song.kt                # 歌曲模型（目录/本地导入）
│   │   └── MusicStore.kt          # 音乐中心：清单拉取 / 下载 / 导入 / 删除 / 索引持久化
│   ├── player/
│   │   └── PlayerController.kt    # ExoPlayer 封装（队列/进度/状态流）
│   └── ui/
│       ├── theme/Theme.kt         # PomoSolo 品牌深色 Material3 主题
│       └── MusicScreen.kt         # 曲库下载 / 本地管理 / 迷你播放器 / 展开播放面板
├── app/src/main/assets/tracks/    # 3 首内置 mp3（内容种子，首启拷入私有目录）
├── app/src/main/AndroidManifest.xml
├── app/build.gradle.kts
├── build.gradle.kts / settings.gradle.kts
└── README.md
```

## 构建与运行

环境：JDK 17、Android SDK（compileSdk 34）。Android Studio 直接打开本目录即可；命令行：

```powershell
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 功能说明（m1）

- **在线曲库**：启动即拉取服务器 `music-manifest.json`（失败用上次缓存），内置 3 首固定置顶；
  点「下载」即原生落盘（进度环形显示），完成后状态本地化 —— 飞行模式照样播放；
- **本地音乐**：内置曲 + 已下载 + 本地导入统一管理；显示歌曲数与占用空间；可删除；
- **导入**：右上角文件图标 → 系统文件选择器选音频 → 拷入私有目录；
- **播放**：点本地行按列表播放；内置曲在未下载前也可在线播放（仅联网时）。

## 已知限制 / 待办

- 下载不支持续传/取消（m2 补）；进程被杀时进行中下载会残留 `.part`，下次启动自动忽略并可由用户重新下载；
- 后台/息屏播放目前仍会随进程回收停止（m2 用前台服务 + MediaSession 解决）；
- 服务器目前仅有 3 首内置曲可下载；曲库扩展由服务器侧决定；
- 番茄计时/自习室/P2P 等其余产品功能在 m3/m4，暂未进入本包。

## 协作规范（沿用主仓库 TEAM_GUIDE）

- 正式输出（commit/文档/回复）声明部门：**【安卓端部门】**；
- commit 格式：`[安卓端部门] feat: xxx` / `fix:` / `docs:` 等；
- **文档同步铁律**：改动接口/架构/功能行为前先同步主仓库对应文档（`server-planning/`、README 等），
  跨部门改动必须留痕（见 TEAM_GUIDE §9.2/§10.3）；
- 与服务器部门协作：曲库清单与 `/music` 文件托管、将来下载鉴权等（接口以主仓库文档为准）。

## 参考

- 主仓库：`D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/`（GitHub: `liaowenqi123/PomoSolo`）
  - `README.md`、`TEAM_GUIDE.md`、`server-planning/EXTERNAL-INTERFACES.md`
- 界面/交互参考：主仓库 `src/`（桌面端 Vue）与 `src/pwa/` —— 仅参考，不 copy
