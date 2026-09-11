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
| 登录 / 账号面板（AuthPanel.vue） | **设置 → 账号**（登录 / 注册 / 会话 / admin Key 下发） | ✅ |
| 自习室 StudyRoom（成员 / 聊天 / 同步听歌 / P2P 传歌） | **自习室** tab | ✅（传歌走服务器中转） |
| 教程页 | — | ⏳ 待办 |
| 桌面端专属（AI 助手 / 菜园子 / 前台检测 / 统计图表 / 太空旅行） | 依赖桌面环境 | 暂不做 |

### V1 里程碑

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **m1 · 下载功能原生化** | 服务器曲库拉取 / 内置曲种子 / 下载→`filesDir` 落盘 / 本地导入(SAF) / 离线播放 / 状态持久化 | ✅ 完成 |
| **m2 · 页面复刻与计时** | 底部导航；专注页（单次 / 计划 / 正向 + 圆环 + 专注模式开关 + 统计）；设置页；计时状态机；阶段完成提醒；屏幕常亮 | ✅ 完成 |
| **m2.1 · 行为对齐修复** | 模式切换不打断计时；「运行中禁止暂停/重置」回归专注模式专有；单次模式无跳过按钮；播放栏独立占位；文案入固定高度容器；容器渐变对齐 PWA 色板 | ✅ 完成 |
| **m2.2 · 热榜与番茄图标** | 音乐热榜（网易云 / QQ）原生爬取；单曲「爬虫 + 音频提取」下载器（B站搜索 → **DeepSeek LLM 选片** → DASH 音频流 → 落盘）；App 图标替换为番茄 | ✅ 完成 |
| **m3 · 播放与计时体验** | 后台播放 + 系统通知、锁屏媒体控制(MediaSession)、下载续传/取消、计时前台服务 | 🚧 部分 |
| **m4 · 账号体系** | REST 对接（注册 / 登录 / 刷新 / 登出 / 会话）、Token 持久化 + 401 自动刷新重试、连接测试、admin 的 DeepSeek Key 下发 | ✅ 完成 |
| **m5 · 自习室** | WebSocket 客户端（心跳 / 退避重连 / 4001 踢线处理）、房间列表 · 创建 · 加入、成员、聊天、番茄完成广播、DJ 同步听歌 | ✅ 完成 |
| **m5.1 · P2P 传歌** | 服务器中转分片传歌；WebRTC DataChannel 直连（省服务器带宽、P2P 提速）待接入 | 🚧 部分 |

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
│   │   ├── DeepSeekClient.kt      # AI 选片：对齐桌面端 deepseek_select（提示词/温度/解析完全一致）
│   │   ├── AiPickStore.kt         # AI 选片配置（开关 / API Key / 模型 / 来源），本机持久化
│   │   ├── AuthStore.kt           # 账号体系：注册/登录/刷新/登出/会话 + admin Key 下发
│   │   ├── StudyRoomStore.kt      # 自习室 WebSocket：房间/成员/聊天/DJ 同步听歌
│   │   ├── SongDownloader.kt      # 单曲下载器：搜索 → AI 选片 → 提取 → 落盘 → 登记本地库（串行队列）
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
│       ├── SettingsScreen.kt      # 设置页（含账号入口）
│       ├── AuthScreen.kt          # 账号页（登录 / 注册 / 已登录信息 / 连接测试）
│       ├── StudyRoomScreen.kt     # 自习室页（房间列表 / 创建 / 加入 / 成员 / 聊天 / DJ）
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
   → B站搜索(api.bilibili.com/x/web-interface/search/type)，取前 6 条候选
   → DeepSeek AI 选片（POST api.deepseek.com/chat/completions，temperature=0）
   → view 取 cid → playurl(fnval=16) 取 DASH 音频流（bandwidth 最大）
   → 下载音频流 → 落盘 filesDir/music/*.m4a → 登记进「本地」并可离线播放
```

**AI 选片**（`DeepSeekClient`）与桌面端 `deepseek_select` 完全对齐：同一份 system prompt
（纯音乐判断器）、`model=deepseek-chat`、`temperature=0`、只取前 6 条、模型只回 `1-6` 或 `None`；
Key 无效 / 余额不足 / 频率超限分别给出 401 / 402 / 429 中文提示。

Key 的配置方式（设置 → 音乐 · AI 选片）：
- 本机填写 DeepSeek API Key（仅存 SharedPreferences）；带「测试连接」按钮；
- 关闭「AI 选片」开关可退化为本地规则打分（关键词 + 时长，可离线）；
- 桌面端还支持登录后由服务器 `GET /api/v1/config/deepseek-key` 下发，安卓端留待账号体系（m4）接入。

与桌面端的**唯一有意差异**：

| 环节 | 桌面端 | 安卓端 | 原因 |
|------|--------|--------|------|
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
**音乐 · AI 选片**（AI 选片开关、DeepSeek API Key、测试连接）、
显示（屏幕常亮、正向计时阈值）、数据（今日/累计、清空今日）、关于。

### 账号体系（m4，对接自建服务器）

入口：**设置 → 账号**（登录 / 注册 Tab；已登录显示用户信息与会话操作）。

接口（`https://api.pomogrow.top/api/v1`，对齐 `server-planning/EXTERNAL-INTERFACES.md` 与桌面端 `cloud_auth.rs`）：

| 端点 | 说明 |
|------|------|
| `POST /auth/register` | `{username, password}` → 201 + `{user, access_token, refresh_token}`；409 用户名/邮箱已注册 |
| `POST /auth/login` | `{username, password}` → 200 + 同上；失败返回 `{error}` |
| `POST /auth/refresh` | `{refresh_token}` → 新 access + **滚动刷新**的 refresh |
| `POST /auth/logout` | 带 `refresh_token`，204；失败也照样清空本地会话 |
| `GET /auth/session` | 校验会话，返回 `{user}` |
| `GET /config/deepseek-key` | 仅 admin：下发 AI 选片用的 Key |
| `GET /api/status`（+ `/api/v1/health` 兜底） | 连接测试（桌面端 / PWA 同款） |

工程要点（对齐 PWA `http.ts`）：
- `Authorization: Bearer <access_token>`；access 15 分钟 / refresh 30 天；
- **401 自动刷新一次并重试原请求**，刷新失败才登出（网络抖动不登出）；
- 错误统一读 `error` 字段（兼容 `detail`）；
- 本地校验与桌面端一致：用户名 ≥2、密码 ≥6、注册两次密码一致；
- Token 与用户信息存 SharedPreferences（应用私有目录）；设置页顶部显示账号摘要。

**与 AI 选片的联动**：admin 账号登录后自动调用 `/config/deepseek-key`，把服务器下发的 Key
写入 AI 选片配置（设置页「Key 来源」会显示"服务器下发"），对齐桌面端 `sync_deepseek_key`。

> 实测（2026-09-11）：`GET /api/status` → 200（服务 v1.0.0）；无 token 访问 `/auth/session` → 401
> `{"error":"未登录"}`；空参数登录 → 400 `{"error":"用户名和密码不能为空"}` —— 与客户端错误处理一致。

### 自习室（m5，WebSocket）

入口：底部导航 **自习室** tab（需先在设置里登录）。

协议权威：`server-planning/ws_server.py` 与 `EXTERNAL-INTERFACES.md` §3–§8；
客户端行为对齐 PWA `src/pwa/ws.ts`：

| 项 | 实现 |
|----|------|
| 连接 | `wss://api.pomogrow.top/ws?token=<access_token>`（token 走 query） |
| 请求-响应 | 带 `id` 的消息等待服务器回同名 id，8s 超时（创建/加入房间等） |
| 广播 | 不带 id（聊天、番茄广播、同步听歌状态） |
| 心跳 | 每 10s 发 `{"type":"ping"}` → 服务器回 `pong` |
| 重连 | 指数退避 1s×2^n（上限 15s），重连前先刷新 access token |
| 踢线 | 关闭码 **4001 = 同账号异地登录**，停止自动重连，避免双端互踢死循环 |
| 房间列表 | REST `GET /api/v1/rooms`（复用账号 token，401 自动刷新） |

已实现功能：
- **房间**：公开列表（含人数 / 是否需要密码 / 房主）、创建（名称/描述/密码）、按 ID 加入、离开；
- **成员**：`room:members` 快照 + `member_joined/left/status` 增量，在线状态圆点；DJ 成员带 🎧；
- **聊天**：`room:chat` 收发，气泡区分自己/他人，自动滚到底部，保留最近 200 条；
- **番茄广播**：本机完成一个番茄 → `room:pomo_done`，房间内显示「XX 完成了一个番茄 🍅」；
- **同步听歌（DJ 模式）**：任何人可「申请当 DJ」（最后申请者即 DJ）；DJ 端本地播放状态变化 →
  广播 `music:sync_state`（song_id 用曲目标题）；听众端收到后**若本地已有该曲目**则跟随播放并
  对齐进度；未下载的曲目会提示（等 P2P 传歌补齐）。

**P2P 传歌**：桌面端走 WebRTC DataChannel 直连（`peer:offer/answer/ice/bye` 信令 + `music:request_song`
/`music:offer_song` 分片），Android 直连需要 WebRTC native 库（数 MB，`.so`）。
当前策略是**先走服务器中转降级路径**（纯 OkHttp + Kotlin，无需 WebRTC），WebRTC 直连列入 m5.1。

> 探测记录（2026-09-11，本机）：REST 全部可用（`/api/status` 200、`/auth/session` 401 等）；
> 但 `wss://api.pomogrow.top/ws` 在本机连续两次握手 12s 超时（`state=Connecting`）。
> 可能原因：本机网络对 WS 升级的限制，或服务器 `/ws` 反代尚未就绪
> （`/api/status` 返回 `"ws_port": 3001`，提示 WS 有独立端口）。
> **建议在手机真机上验证**；若仍不通，请与服务器部门确认 `/ws` 的 Nginx 反代配置
> （PWA 生产环境同样依赖该反代，`src/pwa/ws.ts` 有同款说明）。

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
| 单曲下载 | B站搜索 → DeepSeek 选片 → DASH → ffmpeg 转 mp3 | B站搜索 → DeepSeek 选片 → DASH → 直接存 m4a |
| 下载队列 | 前端串行队列 + 伪进度条（后端无进度事件） | 串行队列 + **真实进度**（按 Content-Length 计算） |

## 已知限制 / 待办

- **后台播放**：息屏/切后台仍可能随进程回收停止（m3 用前台服务 + MediaSession 解决）；
- **下载**：不支持续传/取消；进程被杀会残留 `.part`，下次启动自动忽略并可重新下载；
- **热榜下载**：依赖 B站公开接口，高频使用可能触发 412 风控（已做 Cookie + 退避，仍可能失败）；
- **AI 选片**：需自备 DeepSeek API Key（在设置里填写）；未配置且开关仍开启时会直接提示，
  不会静默降级；关闭开关则退回本地规则选片（冷门曲目可能挑不到）；
- **计时后台**：切后台回到前台时间准确（时间戳基准），但无系统通知/悬浮计时（m3 前台服务）；
- **计划模式形态差异**：PWA 的计划是「可自由增删的任务列表」，安卓当前是「N 轮 × 固定时长」简化版；
- 服务器曲库目前仅 3 首内置曲；曲库扩展由服务器侧决定；
- **账号体系（m4）与自习室（m5）已就绪**；P2P 直连传歌（WebRTC，m5.1）与教程页待做；
- 自习室同步听歌目前只在「听众本地已有该曲目」时跟随播放；缺歌需要 P2P 传歌补齐（m5.1）；
- Token 以明文存于应用私有的 SharedPreferences（Android 沙箱隔离）；如需更强保护可换
  EncryptedSharedPreferences + Keystore。

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
