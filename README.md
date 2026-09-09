# 🍅 PomoSolo Android（安卓端部门）

> **部门：安卓端部门** ｜ 成立：2026-09-10 ｜ 状态：🚧 v0 开发中 ｜ 仓库：[PomoSolo-Android](https://github.com/liaowenqi123/PomoSolo-Android)
>
> 上级项目：**PomoSolo**（番茄钟专注应用，主仓库 `liaowenqi123/PomoSolo`，Tauri 桌面端 + PWA + 自建服务器）。
> 团队协作、部门分工、接口权威文档见主仓库 [TEAM_GUIDE.md](D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/TEAM_GUIDE.md)。

## 这是什么

把 PomoSolo 移植为 **Android 原生应用**。代码由本部门**自研**，仅参考桌面端/PWA 的 Vue 源码思路，不 copy 其代码。

> **路线定调（2026-09-10）**：安卓端**不再把 PWA 构建产物当作应用内容**。v0 先借
> WebView + PWA 产物做功能可行性验证（纯过渡桥接）；**v1 起改为以主仓库桌面端 Vue
> 源码（`src/`）为蓝本，在本工程内复刻界面与业务**（对"原样复用其组件 vs 原生实现"
> 做逐功能取舍），并叠加安卓原生能力 —— 数据真正落盘到 App 私有文件，而不是依赖
> PWA 那套为浏览器/Service Worker 妥协的方案。

### 移植路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| **v0（WebView 验证壳 · 过渡桥接）** | WebView 加载随 APK 打包的 PWA 构建产物，验证核心功能在手机上的可行性；壳内注入云端域名适配层 | 🚧 当前 |
| **v1（Vue 源码移植）** | 以桌面端 `src/` Vue 组件/store 为参考，复刻界面与业务；下载/P2P 歌曲改为**原生文件级持久化**（filesDir），逐步摆脱 WebView/Cache/IDB | ⏳ 规划中 |
| **v2（原生深度增强）** | 系统通知、后台播放 + 锁屏媒体控制、前台检测（防分心）、原生前台服务精确计时等 | ⏳ 待启动 |

### 与 PWA 部门的关系（分工边界）

- **共享**：产品功能定义、服务器接口（REST / WS / P2P）、账号体系 —— 接口改动必须同步主仓库 `server-planning/` 文档；
- **参考**：桌面端 Vue `src/` 与 PWA 的 `src/pwa/` 源码仅作实现参考（复用组件设计与交互思路），不 copy；
- **不依赖**：安卓交付内容与 PWA 构建产物解耦；PWA 在浏览器内做不到的能力（可靠文件持久化、前台检测、系统媒体控制…），安卓端用原生方案达成或更优。

## 技术栈

| 项 | 方案 | 说明 |
|----|------|------|
| 语言 | Java（Android 原生） | v0 壳以 Java 实现，后续按需引入 Kotlin |
| WebView 资源 | `androidx.webkit` WebViewAssetLoader | 以 `https://appassets.androidplatform.net/` 虚拟源加载 assets，规避 `file://` 同源/缓存限制 |
| 云端域名适配 | 页面启动前注入 JS（`MainActivity` 内，本部门自研） | PWA 产物按"生产同源"打包（API 基址为空）时，把相对 `/api`、`/ws`、`/music` 请求的主机改写为 `https://api.pomogrow.top`，再跨域访问（服务器 CORS 白名单放行本虚拟源） |
| 内置前端（v0 过渡） | `app/src/main/assets/` | 暂载 PWA 构建产物（含 3 首内置曲）；v1 起替换为自研前端 |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 26 | JDK 17 |

## 目录结构

```
pomodoro/
├── app/                                    # Android 应用模块
│   ├── src/main/
│   │   ├── java/com/pomogrow/pomosolo/
│   │   │   └── MainActivity.java           # v0 WebView 壳（本部门自研）
│   │   ├── assets/                         # v0 过渡内容源（PWA 产物，tools 同步，勿手改）
│   │   ├── AndroidManifest.xml
│   │   └── res/                            # 主题/颜色/图标（PomoSolo 品牌色）
│   └── build.gradle.kts
├── tools/
│   └── copy-pwa-assets.mjs                 # 从源项目同步 PWA 产物 → assets/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 构建与运行

环境：JDK 17、Android SDK（compileSdk 34）。Android Studio 直接打开本目录即可；命令行：

```powershell
# 调试包
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 同步 PWA 产物（v0 过渡桥接，v1 起停用）

> 本节仅服务于 v0 验证壳。v1（Vue 源码移植）将不再以 PWA 构建产物为内容源，
> 同步产物是临时手段。如需在 v0 内更新产物：先在源项目（`electron_pomodoro`）
> 构建产出最新的 `pwa-dist/`（`npm ci && npm run pwa:build`），再执行下方同步。

```powershell
# 默认从 D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/pwa-dist 同步
node tools/copy-pwa-assets.mjs

# 也可以显式指定源
node tools/copy-pwa-assets.mjs D:/any/pwa-dist
```

同步注意事项：

- `assets/registerSW.js` 会被保留为空操作 —— v0 不在 WebView 内注册 Service Worker；
- 同步的产物**保持源项目默认构建即可**（即同源 `API_ORIGIN` 为空，无需带
  `VITE_API_ORIGIN` 重新构建）：本地壳的"云端域名适配层"会把指向虚拟源主机的
  `/api`、`/ws`、`/music` 请求改写为 `https://api.pomogrow.top`（见 MainActivity 注释）；
  即便将来产物改为绝对域名，改写也只命中本地虚拟源路径，互不冲突；
- PWA 产物变化后需重新同步并提交，保持 APK 内置前端与源项目一致。

## v0 已知限制 / 待办

- **歌曲重进消失（2026-09-10 已定位）**：内置产物为 PWA v0.5.0，缺 `pomo-pwa:library`
  （"已下载/P2P 歌名索引"的 localStorage 持久化）→ P2P 收到的歌字节虽在 IndexedDB，
  但启动无恢复入口；曲库下载歌又依赖 Service Worker 运行时缓存（壳内未注册 SW）——
  两者重进即从列表消失。主仓库 `src/pwa` 已实现修复（`music/library.ts` + 启动恢复），
  需用含修复的产物重建同步（见上节）；**v1 起改为原生文件级持久化，不再受此约束**；
- **云端域名适配（已落地）**：服务器部门已把 `https://appassets.androidplatform.net`
  加入 `api.pomogrow.top` 的 CORS 白名单（2026-09-10，允许 `Content-Type`/`Authorization` 头，
  OPTIONS 预检通过）。配合本端"启动前注入改写"后，登录/会话等云端请求可达；
  在线曲目与自习室 WS 属同一改写规则（v1 起验证）；
- **Service Worker / Cache API**：WebView 内受限，v0 不依赖（外壳本地打包、内置曲目离线可播），
  曲库在线曲目播放依赖网络；
- **系统通知 / 锁屏媒体控制**：v0 未接入，PWA 的 Notification / MediaSession 在 WebView 内行为受限，
  由 v1 原生实现；
- **前台分心检测**：桌面端依赖"检测前台窗口"，手机端需换用原生 Activity 栈检测方案（v1+）；
- **切后台**：WebView 计时基于 JS，应用切后台/息屏可能被系统冻结 —— v1 用原生前台服务 + 精确计时替换。

## 协作规范（沿用主仓库 TEAM_GUIDE）

- 正式输出（commit/文档/回复）声明部门：**【安卓端部门】**；
- commit 格式：`[安卓端部门] feat: xxx` / `fix:` / `docs:` 等；
- **文档同步铁律**：改动接口/架构/功能行为前先同步主仓库对应文档（`server-planning/`、README 等），
  跨部门改动必须留痕（见 TEAM_GUIDE §9.2/§10.3）；
- 与服务器部门协作：CORS 白名单、曲库托管、域名（待启动，见 PWA-requirements.md 同类要求）。

## 参考

- 主仓库：`D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/`（GitHub: `liaowenqi123/PomoSolo`）
  - `README.md`、`TEAM_GUIDE.md`、`server-planning/EXTERNAL-INTERFACES.md`
- PWA 端设计：主仓库 `src/pwa/README.md`（复用思路参考，不 copy）
