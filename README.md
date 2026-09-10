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
> 探测结论：服务器当前只在 `/music/*` 托管 3 首内置曲（`api.pomogrow.top` 与
> `start.pomogrow.top` 均可达）；曲库清单在 `start.pomogrow.top/music-manifest.json`。
> 曲库会随服务器扩展增长 —— 下载层按清单通用实现，曲目多了自然可用。

### PWA 可见页面 → 安卓 V1 映射

PWA 是「单页外壳 + 浮层面板」结构（无 vue-router）。安卓端用底部导航承载同样的页面集合：

| PWA 可见内容 | 安卓 V1 | 状态 |
|--------------|---------|------|
| 主计时页（模式滑块 / 专注·休息切换 / 计时圆环 / 开始·重置 / 侧栏统计） | **专注** tab | ✅ |
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
| **m2 · 页面复刻与计时** | 底部导航骨架；专注页（单次 / 计划 / 正向 + 圆环 + 统计）；设置页；计时状态机；阶段完成提醒（提示音 + 震动）；计时中屏幕常亮 | ✅ 完成 |
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
│   │   ├── MusicStore.kt          # 音乐中心：清单拉取 / 下载 / 导入 / 删除 / 索引持久化
│   │   ├── PomodoroSettings.kt    # 设置（专注/休息时长、计划轮数、自动开始、提醒、常亮…）
│   │   ├── StatsStore.kt          # 统计（今日完成 / 累计专注分钟，跨天自动重置）
│   │   └── PomodoroTimer.kt       # 计时状态机（单次/计划/正向，work↔break，完成事件）
│   ├── player/
│   │   └── PlayerController.kt    # ExoPlayer 封装（队列/进度/状态流/错误上报）
│   └── ui/
│       ├── theme/Theme.kt         # PomoSolo 品牌深色 Material3 主题
│       ├── PomodoroApp.kt         # 骨架：底部导航 + 全局迷你播放器 + 完成提醒
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

## 构建与运行

环境：JDK 17、Android SDK（compileSdk 34）。Android Studio 直接打开本目录即可；命令行：

```powershell
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 功能说明

### 专注页（对齐 PWA 主计时页）

- **模式滑块**：单次 / 计划 / 正向（对应 PWA 的 single / plan / stopwatch）；
- **单次**：专注 ↔ 休息 手动切换，倒计时结束自动进入下一阶段（可选自动开始）；
- **计划**：按设置轮数循环执行（第 i / N 轮），全部完成时提示「计划全部完成」；
- **正向**：从零累计，超过 1 分钟才计入统计（对齐 PWA 文案），点「结束记录」写入统计；
- **计时圆环**：线宽 5、背景 12% 白、进度 85% 白、从顶部顺时针（同 PWA TimerProgress）；
- **惩罚机制**：复刻 PWA —— 专注阶段运行中禁止暂停/重置，可在设置里放开；
- **统计**：今日完成 X 个 / 累计专注 X 分钟（本地持久化，跨天自动重置）；
- **原生增强**：阶段结束提示音 + 震动、计时中保持屏幕常亮、跳过当前阶段。

### 音乐页（m1 下载能力）

- **在线曲库**：启动拉取服务器 `music-manifest.json`（失败用上次缓存），内置 3 首固定置顶；
  点「下载」即原生落盘（环形进度），完成后本地化 —— 飞行模式照样播放；
- **本地音乐**：内置曲 + 已下载 + 本地导入统一管理；显示歌曲数与占用空间；可删除；
- **导入**：右上角文件图标 → 系统文件选择器选音频 → 拷入私有目录；
- **播放**：全局迷你播放条（任意页面可见）+ 展开面板（进度拖拽 / 上一首 / 下一首 / 停止）。

### 设置页

计时（专注/休息时长、计划轮数、自动开始、专注中允许暂停）、提醒（提示音、震动）、
显示（屏幕常亮、正向计时阈值）、数据（今日/累计、清空今日）、关于。

## 已知限制 / 待办

- **后台播放**：息屏/切后台仍可能随进程回收停止（m3 用前台服务 + MediaSession 解决）；
- **下载**：不支持续传/取消；进程被杀会残留 `.part`，下次启动自动忽略并可重新下载；
- **计时后台**：切后台回到前台时间准确（时间戳基准），但无系统通知/悬浮计时（m3 前台服务）；
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
  - `README.md`、`TEAM_GUIDE.md`、`server-planning/EXTERNAL-INTERFACES.md`
  - 参考实现：`src/App.vue`（主计时页）、`src/stores/timer.ts`、`src/stores/stats.ts`、
    `src/stores/settings.ts`、`src/components/{ModeSlider,ModeSwitch,TimerProgress,SettingsPanel,StudyRoom,MusicPlayer}.vue`
- 仅参考界面与交互，不 copy 代码
