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
> 曲库现状（已实测）：服务器 `start.pomogrow.top/music-manifest.json` 当前仅 3 首
> `source: bundled` 曲目（运动 / 学习 / 主题曲），与 APK 内置曲一致；`/music/*` 由
> `api.pomogrow.top` 托管。下载层按清单通用实现，曲库扩展后自动可用。

### PWA 可见页面 → 安卓 V1 映射

PWA 是「单页外壳 + 浮层面板」结构（无 vue-router）。安卓端用底部导航承载同样的页面集合：

| PWA 可见内容 | 安卓 V1 | 状态 |
|--------------|---------|------|
| 主计时页（模式滑块 / 专注·休息切换 / 计时圆环 / 专注模式开关 / 开始·重置 / 侧栏统计） | **专注** tab | ✅ |
| 底部 MusicPlayer（曲库 / 下载 / 本地管理 / 播放列表） | **音乐** tab + 全局迷你播放器 | ✅ |
| SettingsPanel（计时、提醒、显示等） | **设置** tab | ✅（安卓适用项） |
| 登录 / 账号面板 | — | ⏳ m4 |
| 自习室 StudyRoom（成员 / 同步听歌 / P2P 传歌） | — | ⏳ m4 |
| 教程页 | — | ⏳ 待办 |
| 桌面端专属（统计图表 / AI 助手 / 菜园子 / 图表） | 不在 PWA 范围 | 暂不做 |

### V1 里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **m1 · 下载功能原生化** | 服务器曲库拉取 / 内置曲种子 / 下载→`filesDir` 落盘 / 本地导入(SAF) / 离线播放 / 状态持久化 | ✅ 完成 |
| **m2 · 页面复刻与计时** | 底部导航骨架；专注页（单次 / 计划 / 正向 + 圆环 + 专注模式开关 + 统计）；设置页；计时状态机；阶段完成提醒（提示音 + 震动）；计时中屏幕常亮 | ✅ 完成 |
| **m2.1 · 行为对齐修复** | 模式切换不再打断计时；「运行中禁止暂停/重置」回归为**专注模式**专有；单次模式不再出现跳过按钮；播放栏独立占位不遮挡内容；状态文案放入固定高度容器；容器渐变对齐 PWA 色板；音乐页支持一键下载全部 | ✅ 完成 |
| **m3 · 播放与计时体验** | 后台播放 + 系统通知、锁屏媒体控制(MediaSession)、下载续传/取消、计时前台服务 | 🚧 部分（播放无声已修复、错误可见化） |
| **m4 · 云端** | 账号体系（REST）、自习室 WS、P2P 传歌 | ⏳ 规划 |

### 与 PWA 部门的关系（分工边界）

- **共享**：产品功能定义、服务器接口（REST / WS / P2P）、账号体系 —— 接口改动必须同步主仓库 `server-planning/` 文档；
- **参考**：桌面端 Vue `src/` 与 PWA 的 `src/pwa/` 源码仅作界面/交互参考，不 copy、不引用其代码；
- **不依赖**：安卓交付内容与 PWA 构建产物解耦；PWA 在浏览器里做不到的（可靠文件下载、前台检测、屏幕常亮、系统媒体控制…）安卓端用原生方案达成。

## 技术栈（V1 原生）

| 项 | 方案 | 说明 |
|----|------|------|
| 语言 | Kotlin 1.9 + Jetpack Compose (Material3) | 单 Activity 原生 UI，无 WebView |
| 视觉 | 容器渐变对齐 PWA 色板 | 专注页按模式切换渐变：工作 `#EA6666→#8C3232`、休息 `#5AB48C→#4B76A2`、正向 `#667EEA→#764BA2`（取自 `src/styles/global.css`） |
| 播放 | Media3 ExoPlayer 1.3 | 本地 `file://` + 在线流统一播放，显式 AudioAttributes + 音频焦点 |
| 网络 | OkHttp 4.12 | 曲库清单拉取；下载用流式写入 `.part` → 原子改名，进度 0–100 |
| 存储 | `filesDir/music/` + `index.json`；设置/统计用 SharedPreferences | 下载、导入、设置、统计全部本地持久化 |
| 导入 | SAF（系统文件选择器） | 把用户本地音频拷入私有目录统一管理 |
| 计时 | `SystemClock.elapsedRealtime()` 时间戳基准 + StateFlow | 不按 tick 累加，切后台/锁屏回来时间依旧准确 |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 26 | JDK 17 |

## 目录结构

```
pomodoro/
├── app/src/main/java/com/pomogrow/pomosolo/
│   ├── MainActivity.kt            # Compose 单 Activity（初始化各 store 后进入 PomodoroApp）
│   ├── data/
│   │   ├── Song.kt                # 歌曲模型（目录 / 本地导入）
│   │   ├── MusicStore.kt          # 音乐中心：清单拉取 / 下载(含批量队列) / 导入 / 删除 / 索引持久化
│   │   ├── PomodoroSettings.kt    # 设置（专注/休息时长、计划轮数、自动开始、提醒、常亮…）
│   │   ├── StatsStore.kt          # 统计（今日完成 / 累计专注分钟，跨天自动重置）
│   │   └── PomodoroTimer.kt       # 计时状态机（单次/计划/正向，work↔break，专注模式，完成事件）
│   ├── player/
│   │   └── PlayerController.kt    # ExoPlayer 封装（队列/进度/状态流/错误上报）
│   └── ui/
│       ├── theme/Theme.kt         # 品牌色 + PWA 容器渐变
│       ├── PomodoroApp.kt         # 骨架：底部导航 + 迷你播放器(bottomBar 占位) + 完成提醒
│       ├── FocusScreen.kt         # 专注页（番茄钟主计时页）
│       ├── MusicScreen.kt         # 音乐页（在线曲库 / 本地音乐）
│       ├── SettingsScreen.kt      # 设置页
│       └── PlayerUi.kt            # 迷你播放条 + 展开播放面板
├── app/src/main/assets/tracks/    # 3 首内置 mp3（内容种子，首启拷入私有目录）
├── app/src/main/AndroidManifest.xml
├── app/build.gradle.kts
├── build.gradle.kts / settings.gradle.kts
└── README.md
```

## 功能说明

### 专注页（对齐 PWA 主计时页）

- **模式滑块**：单次 / 计划 / 正向（对应 PWA 的 single / plan / stopwatch）；
- **切换模式不会打断计时**：对齐 PWA `setAppMode`（`if (phase === "running") return`）——
  运行中点切换会被忽略，当前计时继续；
- **单次**：专注 ↔ 休息 手动切换，倒计时结束进入下一阶段并停在 READY（可设置自动开始）；
- **计划**：按设置轮数循环执行（第 i / N 轮），阶段完成自动进入下一项，全部完成提示「计划全部完成」；
- **正向**：从零累计，超过 1 分钟才计入统计（对齐 PWA 文案），点「结束记录」写入统计；
- **计时圆环**：线宽 5、背景 12% 白、进度 85% 白、从顶部顺时针（同 PWA TimerProgress）；
- **专注模式开关**（PWA `FocusModeSwitch` + 奖惩机制）：仅 READY 阶段可切换，开启后
  - 运行中**禁止暂停**；
  - 运行中**重置 = 中断专注**（关闭开关 + 重置 + 提示，本轮不计入统计）；
  - 未开启专注模式时，运行中可自由暂停 / 重置（这是默认行为）。
- **统计**：今日完成 X 个 / 累计专注 X 分钟（本地持久化，跨天自动重置）；
- **原生增强**：阶段结束提示音 + 震动、计时中保持屏幕常亮；
- **跳过当前阶段**：仅**计划模式**提供（单次 / 正向模式不显示）；
- **布局稳定**：状态文案、专注模式提示、跳过按钮都放在固定高度的容器里，
  按钮点击后出现/消失的文案不会挤动页面其他内容。

### 音乐页（m1 下载能力 + 原生下载器）

- **在线曲库**：启动拉取服务器 `music-manifest.json`（失败用上次缓存），内置 3 首固定置顶；
  每行点「下载」即原生落盘（环形进度），完成后本地化 —— 飞行模式照样播放；
- **一键下载全部**：标题栏下载图标 = 串行队列依次下载未本地化曲目（对齐桌面端
  `DownloadDialog` 的队列语义；单曲失败不影响后续），右侧显示「下载中…」；
- **本地音乐**：内置曲 + 已下载 + 本地导入统一管理；显示歌曲数与占用空间；可删除；
- **导入**：右上角文件图标 → 系统文件选择器选音频 → 拷入私有目录；
- **播放**：全局迷你播放条（任意页面可见）+ 展开面板（进度拖拽 / 上一首 / 下一首 / 停止）。

> 关于桌面端的「音乐下载器」：桌面端 `DownloadDialog` 的下载由 Rust 后端完成（按歌名从
> 网络音源抓取 + 转码）。安卓端复刻的是**同一条产品链路中可在端上可靠实现的部分**：
> 服务器曲库清单 → 原生落盘 → 离线播放 → 队列/进度/失败提示。当前服务器曲库只有 3 首
> bundled 曲目（且已内置在 APK 中），因此「要联网下载」的实际内容取决于服务器曲库扩展；
> 端上按歌名抓取第三方音源属于独立课题（音源与合规），建议单独立项。

### 设置页

计时（专注/休息时长、计划轮数、自动开始）、提醒（提示音、震动）、
显示（屏幕常亮、正向计时阈值）、数据（今日/累计、清空今日）、关于。

设置里不再有「专注中允许暂停」——该限制属于**专注模式开关**（专注页），不是全局设置。

## 行为对齐说明（PWA / 桌面端 → 安卓）

| 行为 | 原始实现 | 安卓实现 |
|------|----------|----------|
| 运行中切换模式 | `setAppMode` 直接 return，不打断计时 | 同（`PomodoroTimer.setAppMode`） |
| 运行中禁止暂停/重置 | 仅当 `focusModeEnabled`（专注模式）时 | 同（`TimerState.lockedByFocusMode`） |
| 运行中重置（专注模式） | `runPunishment()`：花园枯萎 + 弹窗 + 重置 + 关开关 | 重置 + 关闭开关 + Snackbar「专注已中断」（无花园/前台检测） |
| 阶段完成 | work 完成 → 切 break 停在 ready；计划模式 1s 后自动开始 | 同；单次模式按设置项 `autoStartNext` 决定是否自动开始 |
| 跳过阶段按钮 | PWA 无此按钮 | 仅计划模式提供（安卓增强） |
| 计时推进 | 200ms tick 累加（浏览器休眠会漂移） | `elapsedRealtime` 时间戳反推（不漂移） |
| 计时页配色 | `.container` 渐变（工作红 / 休息绿蓝 / 正向蓝紫） | 同色值渐变（`Theme.kt` 的 `PomoGradient*`） |
| 音乐播放器占位 | `.timer-section { padding-bottom: 120px }` 为绝对定位播放器留白 | 播放器与底部导航同处 `Scaffold.bottomBar`，由 Scaffold 统一预留 |

## 已知限制 / 待办

- **后台播放**：息屏/切后台仍可能随进程回收停止（m3 用前台服务 + MediaSession 解决）；
- **下载**：不支持续传/取消；进程被杀会残留 `.part`，下次启动自动忽略并可重新下载；
- **计时后台**：切后台回到前台时间准确（时间戳基准），但无系统通知/悬浮计时（m3 前台服务）；
- **计划模式的形态差异**：PWA 的计划是「可自由增删的任务列表（+工作/+休息，每项可自定义分钟）」，
  安卓当前是「N 轮 × 固定时长」的简化版；如需要，后续可按任务列表形态补齐；
- 服务器目前仅有 3 首内置曲可下载；曲库扩展由服务器侧决定；
- 账号 / 自习室 / P2P 传歌 / 教程页在 m4，暂未进入本包。

## 协作规范（沿用主仓库 TEAM_GUIDE）

- 正式输出（commit/文档/回复）声明部门：**【安卓端部门】**；
- commit 格式：`[安卓端部门] feat: xxx` / `fix:` / `docs:` 等；
- **文档同步铁律**：改动接口/架构/功能行为前先同步主仓库对应文档（`server-planning/`、README 等），
  跨部门改动必须留痕（见 TEAM_GUIDE §9.2/§10.3）；
- 与服务器部门协作：曲库清单与 `/music` 文件托管、将来下载鉴权等（接口以主仓库文档为准）。

## 参考

- 主仓库：`D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/`（GitHub: `liaowenqi123/PomoSolo`）
  - `TEAM_GUIDE.md`（§3.4 安卓端部门、§9.2 文档同步铁律、§15 提交规范）、`server-planning/`
  - 行为参考：`src/App.vue`（onToggleClick / onResetClick / runPunishment）、`src/stores/timer.ts`、
    `src/components/{ModeSlider,ModeSwitch,TimerProgress,FocusModeSwitch,MusicPlayer,DownloadDialog}.vue`、
    `src/styles/global.css`（渐变变量）、`src/stores/downloadQueue.ts`
- 仅参考界面与交互，不 copy 代码
