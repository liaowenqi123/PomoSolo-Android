# 🍅 PomoSolo Android（安卓端部门）

> **部门：安卓端部门** ｜ 成立：2026-09-10 ｜ 状态：🚧 v0 开发中 ｜ 仓库：[PomoSolo-Android](https://github.com/liaowenqi123/PomoSolo-Android)
>
> 上级项目：**PomoSolo**（番茄钟专注应用，主仓库 `liaowenqi123/PomoSolo`，Tauri 桌面端 + PWA + 自建服务器）。
> 团队协作、部门分工、接口权威文档见主仓库 [TEAM_GUIDE.md](D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/TEAM_GUIDE.md)。

## 这是什么

把 PomoSolo 移植为 **Android 原生应用**。代码由本部门**自研**，仅参考 PWA 部门的"真实复用"思路，不 copy 其代码。

### 移植路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| **v0（WebView 壳）** | WebView 加载随 APK 打包的 PWA 构建产物，快速验证核心功能在手机上的可行性 | 🚧 当前 |
| **v1+（原生化）** | 逐步用原生组件/服务替换 WebView：系统通知、后台播放 + 锁屏媒体控制、前台检测（防分心）、本地文件、下载等 | ⏳ 待启动 |

### 与 PWA 部门的关系（分工边界）

- **共享**：服务器接口（REST / WS / P2P）、账号体系、产品功能定义 —— 改动必须同步主仓库 `server-planning/` 文档；
- **不共享**：凡依赖"浏览器/系统 WebView 之外能力"的功能，需要本部门原生实现（PWA 做不到的事，安卓端可以做到）。

## 技术栈

| 项 | 方案 | 说明 |
|----|------|------|
| 语言 | Java（Android 原生） | v0 壳以 Java 实现，后续按需引入 Kotlin |
| WebView 资源 | `androidx.webkit` WebViewAssetLoader | 以 `https://appassets.androidplatform.net/` 虚拟源加载 assets，规避 `file://` 同源/缓存限制 |
| 宿主 | `app/src/main/assets/` | 打包进 APK 的 PWA 构建产物（含 3 首内置曲） |
| SDK | compileSdk 34 / targetSdk 34 / minSdk 26 | JDK 17 |

## 目录结构

```
pomodoro/
├── app/                                    # Android 应用模块
│   ├── src/main/
│   │   ├── java/com/pomogrow/pomosolo/
│   │   │   └── MainActivity.java           # v0 WebView 壳（本部门自研）
│   │   ├── assets/                         # PWA 构建产物（由 tools 同步，勿手改）
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

## 同步 PWA 产物（重要）

前置：先在源项目（`electron_pomodoro`）跑 `npm run pwa:build` 产出最新的 `pwa-dist/`。

```powershell
# 默认从 D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/pwa-dist 同步
node tools/copy-pwa-assets.mjs

# 也可以显式指定源
node tools/copy-pwa-assets.mjs D:/any/pwa-dist
```

同步注意事项：

- `assets/registerSW.js` 会被保留为空操作 —— v0 不在 WebView 内注册 Service Worker；
- PWA 产物变化后需重新同步并提交，保持 APK 内置前端与源项目一致。

## v0 已知限制 / 待办

- **CORS**：Web 页面源为 `https://appassets.androidplatform.net`，访问云端 `api.pomogrow.top`
  需要服务器部门将该源加入 CORS 白名单（待沟通，见主仓库 `server-planning/`）；
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
