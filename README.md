# 🍅 PomoSolo Android（安卓端部门）

> **部门：安卓端部门** ｜ 成立：2026-09-10 ｜ 状态：🚧 **v1 开发中**（原生落地） ｜ 仓库：[PomoSolo-Android](https://github.com/liaowenqi123/PomoSolo-Android)
>
> 上级项目：**PomoSolo**（番茄钟专注应用，主仓库 `liaowenqi123/PomoSolo`，Tauri 桌面端 + PWA + 自建服务器）。
> 团队协作、部门分工、接口权威文档见主仓库 [TEAM_GUIDE.md](D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/TEAM_GUIDE.md)。

## 这是什么

PomoSolo 的 **Android 原生应用（V1）**。代码全部在本工程内**自研**，不依赖任何 PWA 构建产物，
不复用原前端代码；仅把桌面端/PWA 的 Vue/Rust 源码当作「界面长什么样 / 功能怎么实现」的参考蓝本。

> **路线定调（2026-09-10）**：安卓端**不再把 PWA 构建产物当作应用内容**。v0 的 WebView+PWA
> 验证壳已从本仓库移除（保留在 git 历史），**v1 起以桌面端源码为参考、原生实现界面与业务**，
> 并叠加安卓原生能力 —— 数据真正落盘到 App 私有文件，不再受
> Service Worker / Cache API / IndexedDB 在 WebView 中的妥协与限制约束。

### PWA / 桌面端可见能力 → 安卓 V1 映射

| 原始能力 | 安卓 V1 | 状态 |
|----------|---------|------|
| 主计时页（模式滑块 / 专注·休息切换 / 计时圆环 / 专注模式开关 / 开始·重置 / 统计） | **专注** tab | ✅ |
| MusicPlayer（曲库 / 本地管理 / 播放列表 / 播放控制） | **音乐** tab（曲库 · 热榜 · 本地）+ 全局播放条 | ✅ |
| **音乐热榜 + 爬虫下载**（Charts.vue / DownloadDialog / downloader.rs） | **热榜** tab（原生爬虫 + 音频提取） | ✅ |
| SettingsPanel（计时、提醒、显示等） | **设置** tab | ✅（安卓适用项） |
| 登录 / 账号面板 | — | ⏳ m4 |
| 自习室 StudyRoom（成员 / 同步听歌 / P2P 传歌） | — | ⏳ m4 |
| 教程页 | — | ⏳ 待办 |
| 桌面端专属（AI 助手 / 菜园子 / 前台检测 / 统计图表 / 太空旅行） | 依赖桌面环境 | 暂不做 |

### V1 里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **m1 · 下载功能原生化** | 服务器曲库拉取 / 内置曲种子 / 下载→`filesDir` 落盘 / 本地导入(SAF) / 离线播放 / 状态持久化 | ✅ 完成 |
| **m2 · 页面复刻与计时** | 底部导航；专注页（单次 / 计划 / 正向 + 圆环 + 专注模式开关 + 统计）；设置页；计时状态机；阶段完成提醒；屏幕常亮 | ✅ 完成 |
| **m2.1 · 行为对齐修复** | 模式切换不打断计时；「运行中禁止暂停/重置」回归专注模式专有；单次模式无跳过按钮；播放栏独立占位；文案入固定高度容器；容器渐变对齐 PWA 色板 | ✅ 完成 |
| **m2.2 · 热榜与番茄图标** | 音乐热榜（网易云 / QQ）原生爬取；单曲「爬虫 + 音频提取」下载器（B站搜索 → DASH 音频流 → 落盘）；App 图标替换为番茄 | ✅ 完成 |
| **m3 · 播放与计时体验** | 后台播放 + 系统通知、锁屏媒体控制(MediaSession)、下载续传/取消、计时前台服务 | 🚧 部分 |
| **m4 · 云端** | 账号体系（REST）、自习室 WS、P2P 传歌 | ⏳ 规划 |

### 与 PWA 部门的关系（分工边界）

- **共享**：产品功能定义、服务器接口（REST / WS / P2P）、账号体系 —— 接口改动必须同步主仓库 `server-planning/` 文档；
- **参考**：桌面端 Vue/Rust 源码与 PWA 的 `src/pwa/` 仅作界面/交互参考，不 copy、不引用其代码；
- **不依赖**：安卓交付内容与 PWA 构建产物解耦；PWA 在浏览器里做不到的（可靠文件下载、屏幕常亮、系统媒体控制、本地音频落盘…）安卓端用原生方案达成。

## 技术栈（V1 原生）

| 项 | 方案 | 说明 |
|----|------|------|
| 语言 | Kotlin 1.9 + Jetpack Compose (Material3) | 单 Activity 原生 UI，无 WebView |
| 视觉 | 容器渐变对齐 PWA 色板 | 工作 `#EA6666→#8C3232`、休息 `#5AB48C→#4B76A2`、正向 `#667EEA→#764BA2`（取自 `src/styles/global.css`） |
| 图标 | 番茄 logo（复用桌面端 Tauri 生成的安卓自适应图标） | `src-tauri/icons/android/mipmap-*` → `res/mipmap-*` |
| 播放 | Media3 ExoPlayer 1.3 | 本地 `file://` + 在线流；支持 mp3 / m4a(AAC) |
| 网络 | OkHttp 4.12 | 曲库下载、热榜爬取、B站搜索/音频流；流式写 `.part` → 原子改名，进度 0–100 |
| 存储 | `filesDir/music/` + `index.json`；设置/统计用 SharedPreferences | 下载、导入、设置、统计全部本地持久化 |
| 计时 | `SystemClock.elapsedRealtime()` 时间戳基准 + StateFlow | 不按 tick 累加，切后台/锁屏回来时间依旧准确 |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 26 | JDK 17 |

## 目录结构

```
pomodoro/
├── app/src/main/java/com/pomogrow/pomosolo/
│   ├── MainActivity.kt            # Compose 单 Activity（初始化各 store 后进入 PomodoroApp）
│   ├── data/
│   │   ├── Song.kt                # 歌曲模型（目录 / 本地导入）
│   │   ├── MusicStore.kt          # 音乐中心：清单拉取 / 下载(批量队列) / 导入 / 删除 / 索引持久化
│   │   ├── ChartsStore.kt         # 音乐热榜：网易云 / QQ 榜单直连爬取
│   │   ├── BiliClient.kt          # 音源客户端：B站搜索 / DASH 音频流提取 / 流式下载（CookieJar + 退避重试）
│   │   ├── SongDownloader.kt      # 单曲下载器：搜索 → 规则选片 → 提取 → 落盘 → 登记本地库（串行队列）
│   │   ├── PomodoroSettings.kt    # 设置（时长、计划轮数、自动开始、提醒、常亮…）
│   │   ├── StatsStore.kt          # 统计（今日完成 / 累计专注分钟，跨天自动重置）
│   │   └── PomodoroTimer.kt       # 计时状态机（单次/计划/正向，work↔break，专注模式，完成事件）
│   ├── player/
│   │   └── PlayerController.kt    # ExoPlayer 封装（队列/进度/状态流/错误上报）
│   └── ui/
│       ├── theme/Theme.kt         # 品牌色 + PWA 容器渐变
│       ├── PomodoroApp.kt         # 骨架：底部导航 + 迷你播放器(bottomBar 占位) + 完成提醒
│       ├── FocusScreen.kt         # 专注页（番茄钟主计时页）
│       ├── MusicScreen.kt         # 音乐页（曲库 / 热榜 / 本地 三 tab）
│       ├── ChartsTab.kt           # 热榜页（来源切换 + 榜单 + 下载/进度/重试）
│       ├── SettingsScreen.kt      # 设置页
│       └── PlayerUi.kt            # 迷你播放条 + 展开播放面板
├── app/src/main/res/mipmap-*/     # 番茄图标（含自适应图标 mipmap-anydpi-v26）
├── app/src/main/assets/tracks/    # 3 首内置 mp3（内容种子，首启拷入私有目录）
├── app/src/main/AndroidManifest.xml
├── app/build.gradle.kts
├── build.gradle.kts / settings.gradle.kts
└── README.md
```

## 功能说明

### 专注页（对齐 PWA 主计时页）

- **模式滑块**：单次 / 计划 / 正向；
- **切换模式不会打断计时**：对齐 PWA `setAppMode`（`if (phase === "running") return`）——
  运行中点切换会被忽略，当前计时继续；
- **单次**：专注 ↔ 休息 手动切换，倒计时结束进入下一阶段并停在 READY（可设置自动开始）；
- **计划**：按设置轮数循环执行，阶段完成自动进入下一项，全部完成提示「计划全部完成」；
- **正向**：从零累计，超过 1 分钟才计入统计，点「结束记录」写入统计；
- **计时圆环**：线宽 5、背景 12% 白、进度 85% 白、从顶部顺时针（同 PWA TimerProgress）；
- **专注模式开关**（PWA `FocusModeSwitch` + 奖惩机制）：仅 READY 阶段可切换，开启后运行中**禁止暂停**、
  重置 = **中断专注**（关开关 + 重置 + 提示，本轮不计入统计）；未开启时可自由暂停/重置；
- **跳过当前阶段**：仅**计划模式**提供；
- **布局稳定**：状态文案、专注模式提示、跳过按钮都在固定高度容器内，出现/消失不挤动页面；
- **原生增强**：阶段结束提示音 + 震动、计时中保持屏幕常亮。

### 音乐页（三个 tab）

- **曲库**：拉取服务器 `music-manifest.json`（失败用缓存），内置曲置顶；单曲下载 / **一键下载全部**
  （串行队列，对齐桌面端 DownloadDialog 队列语义）；
- **热榜**：网易云热歌榜 / QQ音乐热歌榜，直连平台公开接口（无签名、不经自建服务器）；
- **本地**：内置曲 + 已下载 + 热榜下载 + 本地导入统一管理；显示数量与占用空间；可删除；
- **导入**：系统文件选择器（SAF）把本地音频拷进私有目录；
- **播放**：全局迷你播放条（任意页面可见）+ 展开面板（进度拖拽 / 上一首 / 下一首 / 停止）。

### 热榜下载器（爬虫 + 音频提取）

复刻桌面端 `charts.rs` + `modules/downloader.rs` 的下载链路，**全部在手机端原生完成**：

```
榜单(网易云/QQ，只需歌名)
   → B站搜索(api.bilibili.com/x/web-interface/search/type)
   → 规则选片（过滤合集/教程/翻唱/超长视频，优先 纯音乐·伴奏·钢琴，时长 2.5–7 分钟）
   → view 取 cid → playurl(fnval=16) 取 DASH 音频流（bandwidth 最大）
   → 下载音频流 → 落盘 filesDir/music/*.m4a → 登记进「本地」并可离线播放
```

与桌面端的两处**有意差异**：

| 环节 | 桌面端 | 安卓端 | 原因 |
|------|--------|--------|------|
| 选片 | 调 DeepSeek 大模型判断「哪条是纯音乐」 | **规则打分**（关键词 + 时长） | 不引入外部 LLM 依赖与 API Key，结果确定、可离线 |
| 转码 | ffmpeg / symphonia+mp3lame 转 mp3 | **跳过转码，直接存 m4a** | Android 无 ffmpeg 可执行环境；ExoPlayer 原生支持 AAC/M4A，且无二次编码损失 |

工程细节（对齐桌面端）：内存 CookieJar + 首次访问站点根拿 `buvid3`、失败退避重试
（0.8s→2.4s→7.2s，对应桌面端 3 次退避）、串行队列一次一首、`.part` 临时文件 + 原子改名。

> **实测记录（2026-09-10）**：搜索 200 / view 200 / playurl 200（`code=0`，3 条 dash.audio，
> maxBandwidth 306565）/ 音频流 206 且返回 `ftypiso5` 合法 m4a 容器；连续高频请求会触发 412，
> 故实现里必须带 Cookie 并退避重试。
>
> ⚠️ **合规提示**：音源来自 B站公开接口，与桌面端做法一致，仅供个人学习/自用，
> 请勿用于分发或商业用途；排查问题时优先确认「网络是否可达 + 是否被风控」。

### 设置页

计时（专注/休息时长、计划轮数、自动开始）、提醒（提示音、震动）、
显示（屏幕常亮、正向计时阈值）、数据（今日/累计、清空今日）、关于。

## 行为对齐说明（PWA / 桌面端 → 安卓）

| 行为 | 原始实现 | 安卓实现 |
|------|----------|----------|
| 运行中切换模式 | `setAppMode` 直接 return，不打断计时 | 同 |
| 运行中禁止暂停/重置 | 仅当 `focusModeEnabled`（专注模式）时 | 同（`TimerState.lockedByFocusMode`） |
| 运行中重置（专注模式） | `runPunishment()`：花园枯萎 + 弹窗 + 重置 + 关开关 | 重置 + 关开关 + Snackbar「专注已中断」（无花园/前台检测） |
| 阶段完成 | work 完成 → 切 break 停在 ready；计划模式 1s 后自动开始 | 同；单次模式按设置项决定 |
| 跳过阶段按钮 | PWA 无此按钮 | 仅计划模式提供（安卓增强） |
| 计时推进 | 200ms tick 累加（浏览器休眠会漂移） | `elapsedRealtime` 时间戳反推（不漂移） |
| 计时页配色 | `.container` 渐变（工作红 / 休息绿蓝 / 正向蓝紫） | 同色值渐变 |
| 音乐播放器占位 | `.timer-section { padding-bottom: 120px }` | 与底部导航同处 `Scaffold.bottomBar`，由 Scaffold 预留 |
| 热榜取数 | 直连网易云 / QQ 公开接口 | 同（`ChartsStore`） |
| 单曲下载 | B站搜索 → DeepSeek 选片 → DASH → ffmpeg 转 mp3 | B站搜索 → 规则选片 → DASH → 直接存 m4a |
| 下载队列 | 前端串行队列 + 伪进度条（后端无进度事件） | 串行队列 + **真实进度**（按 Content-Length 计算） |

## 已知限制 / 待办

- **后台播放**：息屏/切后台仍可能随进程回收停止（m3 用前台服务 + MediaSession 解决）；
- **下载**：不支持续传/取消；进程被杀会残留 `.part`，下次启动自动忽略并可重新下载；
- **热榜下载**：依赖 B站公开接口，高频使用可能触发 412 风控（已做 Cookie + 退避，仍可能失败）；
  选片为规则实现，遇到冷门曲目可能找不到合适音源；如需更高准确率可后续接入 LLM 选片；
- **计时后台**：切后台回到前台时间准确（时间戳基准），但无系统通知/悬浮计时（m3 前台服务）；
- **计划模式形态差异**：PWA 的计划是「可自由增删的任务列表」，安卓当前是「N 轮 × 固定时长」简化版；
- 服务器曲库目前仅 3 首内置曲；曲库扩展由服务器侧决定；
- 账号 / 自习室 / P2P 传歌 / 教程页在 m4。

## 协作规范（沿用主仓库 TEAM_GUIDE）

- 正式输出（commit/文档/回复）声明部门：**【安卓端部门】**；
- commit 格式：`[安卓端部门] feat: xxx` / `fix:` / `docs:` 等；
- **文档同步铁律**：改动接口/架构/功能行为前先同步主仓库对应文档（`server-planning/`、README 等），
  跨部门改动必须留痕（见 TEAM_GUIDE §9.2/§10.3）；
- 与服务器部门协作：曲库清单与 `/music` 文件托管、将来下载鉴权等（接口以主仓库文档为准）。

## 参考

- 主仓库：`D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/`（GitHub: `liaowenqi123/PomoSolo`）
  - `TEAM_GUIDE.md`（§3.4 安卓端部门、§9.2 文档同步铁律、§15 提交规范）、`server-planning/`
  - 计时：`src/stores/timer.ts`、`src/App.vue`（onToggleClick / onResetClick / runPunishment）
  - 页面：`src/components/{ModeSlider,ModeSwitch,TimerProgress,FocusModeSwitch,MusicPlayer}.vue`、`src/styles/global.css`
  - 热榜下载：`src-tauri/src/commands/charts.rs`、`src-tauri/src/modules/downloader.rs`、
    `src/components/{Charts,DownloadDialog}.vue`、`src/stores/downloadQueue.ts`
  - 图标：`src-tauri/icons/android/`
- 仅参考实现思路与交互，不 copy 代码
