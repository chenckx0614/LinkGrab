# LinkGrab

抖音/小红书无水印媒体解析下载器 Android 应用。

## ⚠️ 每次修改必须执行（记死）

每次修复 bug 或更新功能后，**最后一步**必须做以下所有事项：

1. **更新 `app/build.gradle.kts`** 中的 `versionCode` 和 `versionName`
2. **更新 `UpdateLogScreen.kt`** 中的 `updateLogs` 列表，添加新版本条目
3. **构建 APK** 后复制为带版本号的文件名：
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/LinkGrab-v{版本号}.apk
   ```
4. **更新 `CLAUDE.md`** 底部的版本历史表
5. **Push 到 GitHub 和 Gitee**：
   ```bash
   git add -A && git commit -m "v{版本号}: {更新摘要}"
   git push origin main
   git push gitee main
   ```
6. **创建 GitHub Release 并上传 APK**：
   ```bash
   gh release create v{版本号} app/build/outputs/apk/debug/LinkGrab-v{版本号}.apk \
     --title "LinkGrab v{版本号}" \
     --notes "{更新内容列表}"
   ```

版本号格式：`major.minor.patch`（如 1.2.1），versionCode 递增。

**不允许跳过此步骤。**

## 技术栈

- Kotlin 2.3.20 + Jetpack Compose
- miuix-ui-android 0.9.0 (UI 组件)
- miuix-preference-android 0.9.0 (设置组件)
- miuix-icons-android 0.9.0 (图标)
- miuix-blur-android 0.9.0 (模糊效果，minSdk=31)
- Coil 3 (图片加载)
- Retrofit + OkHttp (网络)
- kotlinx.serialization (JSON)
- DataStore Preferences (设置存储)
- Media3/ExoPlayer (视频)
- Navigation Compose (导航)

## 构建配置

- AGP: 8.10.0
- Kotlin: 2.3.20
- compileSdk: 37
- minSdk: 31 (miuix-blur 要求)
- targetSdk: 35
- JVM Toolchain: 17
- Gradle: 8.11.1
- local.properties: sdk.dir=c:\Users\chenc\AppData\Local\Android\Sdk

## 构建命令

```bash
cd "c:/Users/chenc/Desktop/Claude Code/linkgrab"
./gradlew assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
linkgrab/app/src/main/java/com/linkgrab/app/
├── MainActivity.kt              # 入口，处理分享 Intent
├── LinkGrabApp.kt               # Application
├── ui/
│   ├── theme/Theme.kt           # miuix 主题 (深色/浅色/跟随系统)
│   ├── screens/
│   │   ├── HomeScreen.kt        # 首页 - 链接输入
│   │   ├── ResultScreen.kt      # 结果展示 (图片/视频)
│   │   ├── SettingsScreen.kt    # 设置 (主题/调试/更新日志/关于)
│   │   └── BlurUtils.kt        # 模糊工具
│   └── navigation/AppNavigation.kt
├── data/
│   ├── api/
│   │   ├── UcmaoParser.kt       # 优创猫 API (抖音)
│   │   └── WebViewParser.kt     # WebView 解析 (小红书 tools.emmmm.dev)
│   ├── model/MediaResult.kt
│   └── repository/
│       ├── ParseRepository.kt   # 解析仓库 + 口令链接提取
│       └── SettingsRepository.kt
├── notification/
│   ├── LiveUpdatesHelper.kt     # 通知工具类
│   └── LiveUpdatesService.kt    # 前台服务 (Android 16 上岛)
└── viewmodel/MainViewModel.kt
```

## 解析策略

| 平台 | 主方案 | 降级方案 |
|------|--------|----------|
| 抖音 | ucmao API (parse.ucmao.cn) | 直接 HTML → WebView(peanutdl) |
| 小红书 | WebView (tools.emmmm.dev) | 直接 HTML |

## miuix API 要点

- `TopAppBar(title, largeTitle, scrollBehavior)` — 大标题 + 折叠动画
- `MiuixScrollBehavior()` — 滚动行为
- `SmallTitle(text)` — 简单标题
- `NavigationBarItem(icon, label, ...)` — icon 是 ImageVector，label 是 String
- `CircularProgressIndicator()` — 无 color 参数
- `MiuixTheme.textStyles.title1` — 标题样式 (不是 title)
- `MiuixTheme.colorScheme.onSurfaceVariantSummary` — 次要文字颜色
- Icons: `MiuixIcons.Back`, `MiuixIcons.Download`, `MiuixIcons.Image`, `MiuixIcons.Settings`

## 版本历史

| 版本 | 日期 | 内容 |
|------|------|------|
| 2.4.0 | 2026-09-27 | 抖音新增保存清晰度选择（解析 bit_rate 全档位，UI 可选，默认最高；预览仍为最高画质）；实况照片保存修复（候选地址回退 + HTTP 状态/内容类型校验 + CDN 请求头） |
| 2.3.9 | 2026-09-27 | 小红书主通道改为 Cronet（Chromium 网络栈 = Chrome 同源 TLS 指纹），照搬高星项目 JoeanAmier/XHS-Downloader 的方案（curl_cffi impersonate=chrome* 的等价实现）；通道顺序 Cronet→WebView→直连→旧链 |
| 2.3.8 | 2026-09-27 | 紧急修复：v2.3.7 的小红书主通道在主线程协程里调阻塞网络请求（NetworkOnMainThreadException），整条链路从未执行；XHS 分支移到 IO 线程；失败时展示双通道原因 |
| 2.3.7 | 2026-09-27 | 小红书方案重构：主通道 = 展开短链（OkHttp，从未失败）→ WebView 直载笔记直链（跳过短链跳转的降级不确定性）；失败时聚合展示各通道真实错误 |
| 2.3.6 | 2026-09-27 | 小红书解析核心修复：Shield 的 TLS 指纹检测会拦截 HTTP 库客户端（302 到登录页），新增 WebView 直载笔记页通道（Chromium 指纹可过）提取 SSR 数据；短链展开本身正常（带回 xsec_token） |
| 2.3.5 | 2026-09-27 | 修复「不支持的链接格式」误导性报错（降级链平台识别漏 xhslink.cn/xhs.link，且会盖住原生真实错误）；短链解析加固（带会话 + 移动 UA 优先，展开结果非笔记页时回退原始短链）；兼容短链被分享文案折行 |
| 2.3.4 | 2026-09-27 | 小红书解析修复：引导 a1/webId 匿名会话（被墙时本地合成）、移除站内 Referer（会被重定向到登录页）、短链展开支持 200+HTML 跳转指针并逐跳解析。会话逻辑移植自 tamnd/xiaohongshu-cli |
| 2.3.3 | 2026-09-27 | 抖音清晰度修复：改从 video.bit_rate[] 按码率取最高画质档位（原来固定取 play_addr，恒定 720p）；优先 H.264；封面改用 origin_cover；修复 webp 过滤失效 |
| 2.3.2 | 2026-09-16 | 应用内更新：检测到新版本后使用 DownloadManager 在后台下载 APK，下载完成后调起系统安装器；无 APK 资产时兜底打开 release 页面 |
| 2.3.1 | 2026-09-15 | iOS 27 风格全局统一：新增 iOS 27 输入框/加载器/错误色，替换各页面残留 Material3 组件；列表行增加按压反馈 |
| 2.0.0 | 2026-09-07 | 大版本：解析系统全面重构，原生抖音/小红书解析引擎（移植自 parse-video-py），支持小红书实况照片与抖音图文/LivePhoto 解析、播放与下载 |
| 1.8.3 | 2026-09-07 | 基于 iOS 26 Liquid Glass 设计规范重构全局 UI：系统蓝主题、更通透的玻璃面板、更精致的圆角与阴影、优化转场动画与按压反馈 |
| 1.8.2 | 2026-09-07 | 基于 NexioSchedule / HyperOS 4 风格重构全局 UI：紫色主题、液态玻璃面板、Squircle 圆角与深色模式适配 |
| 1.2.5 | 2026-06-19 | 更新弹窗改为miuix风格 |
| 1.2.4 | 2026-06-19 | 优化设置页UI排版，分组展示更清晰 |
| 1.2.3 | 2026-06-19 | 修复检查更新检测，已是最新版提示，双平台Release |
| 1.2.2 | 2026-06-19 | 检查更新功能，启动自动检测，跳转Gitee下载 |
| 1.2.1 | 2026-06-19 | 历史记录模块，修复返回栈，修复模糊遮挡 |
| 1.2.0 | 2026-06-19 | v1.2 大版本：历史记录 + DataStore持久化 |
| 1.1.9 | 2026-06-18 | 全面屏沉浸式，版本号动态读取 |
| 1.1.8 | 2026-06-18 | 顶栏/底栏模糊，预测返回手势 |
| 1.1.7 | 2026-06-18 | Live Updates 重构，targetSdk 36 |
| 1.1.6 | 2026-06-18 | Live Updates 进度通知 |
| 1.1.5 | 2026-06-18 | 修复跟随系统开关 |
| 1.1.4 | 2026-06-18 | 外观选项联动 |
| 1.1.3 | 2026-06-18 | 社交媒体真实图标 |
| 1.1.2 | 2026-06-18 | 导航动画优化 |
| 1.1.1 | 2026-06-18 | 修复关于闪退 |
| 1.1.0 | 2026-06-18 | 标题折叠动画，Live Updates 前台服务 |
| 1.0.9 | 2026-06-18 | 标题折叠动画，Live Updates 前台服务 |
| 1.0.8 | 2026-06-18 | Live Updates 通知，调试开关 |
| 1.0.7 | 2026-06-18 | 分享接收功能，应用图标 |
| 1.0.6 | 2026-06-18 | 小红书改回 tools.emmmm.dev |
| 1.0.5 | 2026-06-18 | 修复返回暂无数据 |
| 1.0.4 | 2026-06-18 | 修复返回白屏 |
| 1.0.3 | 2026-06-18 | 口令文字自动提取链接 |
| 1.0.2 | 2026-06-18 | 集成 ucmao API |
| 1.0.1 | 2026-06-18 | WebView 解析引擎 |
| 1.0.0 | 2026-06-18 | 首次发布 |

## 注意事项

- miuix-blur 要求 minSdk 31
- 小红书解析依赖 tools.emmmm.dev 网页，可能受 Cloudflare 保护
- ucmao API 需要 Vigenere 密码认证
- 口令链接提取支持抖音/小红书分享口令格式
- Live Updates 需要前台服务才能在 Android 16 上岛
- Live Updates 闇€瑕佸墠鍙版湇鍔℃墠鑳藉湪 Android 16 涓婂矝

## 2026-08-13 维护规范补充

- UI 重构优先检索 GitHub 开源实现和参考项目，评估 license、维护状态与 Compose 兼容性后再决定是否引入。
- 液态玻璃优先复用现有 Miuix blur；如需真实 backdrop blur、折射或色散，再评估 Cloudy / AndroidLiquidGlass 等方案。
- Android 16 Live Updates 必须同时具备 POST_NOTIFICATIONS、POST_PROMOTED_NOTIFICATIONS、ongoing、CATEGORY_PROGRESS、promoted ongoing 请求和前台服务。
- 每次修改后必须更新版本号、更新日志、构建带版本号 APK，并完成编译验证。
