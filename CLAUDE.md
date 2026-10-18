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
| 3.0.15 | 2026-10-18 | **文件传输二级页 + 菜单玻璃换采样源 + 视口自愈**：① 工具箱「文叔叔」升级为「文件传输」入口 → 新建 `FileTransferScreen`（`Screen.FileTransfer`），收 6 家免登录中转站：文叔叔 / AirPortal 快传 / 轻松传 / file.kiwi / JiDrop / SendTomo；每行经 `onOpenUrl` 交给 AppNavigation 分流（跟随「工具用浏览器打开」开关，默认应用内 `Screen.ToolWeb`）。`ToolRow` 由 private 改 internal 复用。② **菜单毛玻璃不再采样活 WebView**：新建 `menuShadow`（开菜单瞬间截 200×380dp 的网页区域，用 `rememberCanvasBackdrop` 画出来当 `Backdrop`），玻璃里是真实页面模糊；截图是死图，网页永远不闪、不缺块。删掉顶栏的 `layerBackdrop(headerBackdrop)`。③ **文叔叔「显示不全」根因二（视口）**：CDP 实测站点是响应式的（`html` font-size 由 JS 按 `innerWidth/10` 算，390→39px），所以只要布局视口宽度对就完整；WebView 若报出偏宽的 `innerWidth`（初始宽视口/尺寸未定）整页就按错宽度排版，只剩左半屏。新增 `buildFitViewportJs(cssWidth)` 自愈：`innerWidth > 视图宽×1.08` → 强制 viewport meta `width=<视图宽>, initial-scale=1`；内容比视口宽 → 按浏览器 shrink-to-fit 缩放；改完补发 `resize` 让站点重算 rootFont/`--vh`。`onPageFinished` 后 0/400/1200ms 各跑一次，视口尺寸变化时再跑。④ 菜单新增「页面诊断」：`evaluateJavascript` 取回 kernel 版本、视图宽、`innerWidth/innerHeight`、文档 `scrollWidth/scrollHeight`、root font、`--vh`、图片加载数、节点数，面板里可选中复制。⑤ 工具页 `loadWithOverviewMode` 恢复 true（无 meta 的页面铺满），`--vh` resize 补发保留。 |
| 3.0.14 | 2026-10-18 | **文叔叔「显示不全」彻底修**：① **根因一（主因）**——AppNavigation 的 `liquidBackdrop` 采样层从 2.5.23 起是挂在**整个 NavHost** 上的（`!transitioning` 时常挂），WebView 页面同样被录进 GraphicsLayer；WebView 走 Chromium 的 functor 合成，录进采样层后只能拿到部分帧 → 页面大片灰块/白块、图片与列表缺一半（同一页面系统浏览器完整）。3.0.12 只把页面内菜单的采样层从 Column 挪到顶栏，**NavHost 这层一直没动**。改为 `recordNavBackdrop = !transitioning && onMainTabs`：采样层只为底栏玻璃存在，非主标签页（结果页/工具页/游戏页）一律不录。② **根因二**——`IOSPageSurface` 的 `blur(18dp*cover)` + `Modifier.shadow` 外壳套在所有 `iosPage` 上，WebView 被包在 RenderEffect/graphicsLayer 里同样会整块灰白、局部不重绘；新增 `WebViewHostRoutes`（game/tool_web）直接跳过外壳，原样渲染（这类页不透明也不会被别的页盖住）。③ **加载闸门**——把 `loadUrl` 挪到 `addOnLayoutChangeListener` 尺寸 > 0 之后（`post{}` 兜底）：0×0 时开跑，站点在 `<head>` 里用 `window.innerHeight` 算出的 `--vh` 会是 0，`height:calc(var(--vh)*100)` 的容器整块塌陷（文叔叔的全屏引导弹层就是这么写的）。④ `onPageFinished` 与视口尺寸变化时补发 `resize` 事件，让站点用真实 innerHeight 重算 `--vh`（命中缓存/首帧尺寸未定也能自愈）。⑤ 工具页：`loadWithOverviewMode=false`（避免内容超宽时整页缩小米粒化）、去掉 `setSupportMultipleWindows(true)`（开着不实现 `onCreateWindow`，`window.open`/`target=_blank` 会被静默吞掉）、白底垫层、淡入结束后摘掉 alpha 合成层。⑥ **补齐 `onShowFileChooser`**（ActivityResult 文件选择器）——没有它网页里的 `<input type=file>` 点了毫无反应，而文叔叔的核心动作就是选文件上传。 |
| 3.0.1 | 2026-10-16 | **3.0 欢迎页重构**：① 引导展示**版本化**——`guide_shown` key 语义从「看过=1」改为「看过的最新大版本号（versionCode）」，`guideVersion < 300` 就展示 → 老用户升级 3.0 也出现一次，看完写 300，之后不再出现；② GuideScreen 全重写：弥散渐变流动背景（#9698E7/#EEC1DD/#D1D0EF 色团以 13s/19s/11s 不同周期游走在 #FCF8FB 底上，radialGradient 衰减叠加）+ 中央 210sp 白色大「3」（带紫色柔影）+ LinkGrab 标题 + 底部「下一步」白底药丸按钮；③ 入场错峰动画（背景淡入→「3」弹簧放大→标题上浮→按钮浮现）；④ 离场动画：点击后整页放大 12% + 淡出 460ms 送进主界面（onFinished(null)，底栏落位动画无来源时默认直落，兼容）。 |
| 3.0.0 | 2026-10-16 | **3.0 大版本：体积 / 队列 / 批量 / 整理**。① **APK 93MB→26.7MB**：交付链路从 assembleDebug 切到 assembleRelease（release 挂 debug 签名 + R8 `minifyEnabled` + `shrinkResources`，一次通过无需新增 keep 规则）；dex 占 74MB 是未混淆 debug 包的根因；ffmpeg（视频转音频）与 cronet（小红书 TLS 指纹）为功能刚需保留。② **下载队列引擎重构**：`DownloadTaskItem`+`DownloadStatus` 任务模型，Semaphore 按设置并发（1-4，默认 2）调度；worker 写入循环每 8KB 轮询 pauseFlags/cancelFlags（TaskPausedSignal/TaskCanceledSignal 控制流信号）实现**暂停/断点续传**（MediaStore 半成品 IS_PENDING=1 保留 + Range 头 + openOutputStream("wa") 追加；416=已写满直接收尾；服务器不支持 Range 自动重来）；视频多候选 candidateIndex 记进度位置；失败清半成品可重试。③ **下载管理页**（我的→下载管理，route downloads）：并发数 chip 组 + 自动整理开关 + 任务列表（细进度条/暂停/继续/重试/取消/清除已完成），入口行带进行中数量徽标。④ **批量解析**：extractUrls 过滤支持平台后 >1 条走 parseMultipleUrls（上限 5 条）→ BatchResultScreen 勾选下载（成功项默认勾选），单条仍走原结果页。⑤ **自动整理**：RELATIVE_PATH = `LinkGrab/平台/yyyyMMdd`（autoOrganize 开关，默认开；platformLabel 取最近成功解析的平台）。⑥ 性能：ResultScreen 图片网格稳定 key（URL）、进度写入 120ms 节流。 |
| 2.5.40 | 2026-10-16 | **下载源选择 UI 优化**：① 选中即收起（对齐库的 `collapseOnSelection = true` 语义）；② 展开走 `expandVertically` 弹簧 + 淡入、收起 tween 缩回；③ chevron 从硬切 0°/90° 改为 `animateFloatAsState` 弹簧平滑旋转；④ 打勾弹性出现（scaleIn 欠阻尼 spring，20dp 固定占位避免行高跳动）；⑤ 展开/选择全程 `HapticFeedbackType.ContextClick` 触觉反馈。坑：Row 内调用 `AnimatedVisibility` 会误匹配 `RowScope.AnimatedVisibility` 扩展 → 全限定 `androidx.compose.animation.AnimatedVisibility` 解决 |
| 2.5.39 | 2026-10-16 | **更新设置新增「下载源」选择（Gitee 默认 / GitHub）**：用户要求参考 Nexio 课程表的下载源选择 UI。参考库用的是 `OverlayDropdownMenu`（miuix BasicComponent + OverlayDropdownPopup 深度绑定），LinkGrab **没有 miuix 依赖**（CLAUDE.md 旧技术栈描述过时，实际只有 Compose + iOS26 自研体系），直接搬会拖进整个 UI 库且风格打架——改为用 iOS26 体系**复刻同样交互形态**：点击「下载源」行就地展开（AnimatedVisibility + chevron 90° 旋转），两个选项行（Gitee「国内直连，检查与下载速度更快」/ GitHub「国际源，适合海外网络环境」）当前项蓝字 + Check 打勾。链路：SettingsRepository `download_source`（string，默认 "gitee"）→ MainViewModel `downloadSource` StateFlow + `setDownloadSource` → UpdateChecker `checkForUpdate(source)`：gitee 源仍保留 GitHub 兜底（Gitee 不可达时），选 github 则只查 GitHub。自动/手动检查均按所选源 |
| 2.5.38 | 2026-10-16 | **底栏转场跟随（告别突然消失）**：用户反馈「进入页面的时候底栏不要突然消失」。根因：底栏是 AppNavigation 层 overlay（不在 MainTabs 内），`showBottomBar = currentDestination==MainTabs` 在导航提交瞬间把底栏直接移出组合。修复：`barMounted`（组合开关）与 `barCover`（0就位→1退场 Animatable）分离——push 时底栏随转场**左移 30% 屏宽 + 渐隐**（tween PushMs/iosEasing，与页面同步），动画完成后才摘组合（此时新页已盖满全屏，移除无感）；pop 回主界面时 `barMounted=true` 后从 cover=1（让位+透明）滑回原位。快速连点安全：LaunchedEffect(onMainTabs) 重启时 animateTo 重入，中间态自然恢复。遮挡层（coveringSplash）在底栏之后渲染，引导流程层级不受影响 |
| 2.5.37 | 2026-10-16 | **转场加底层页推进感（30% 视差回归）**：用户反馈 2.5.36 的「旧页原地不动」缺层次，希望后面窗口也有一点推进感。push 时旧页 `slideOutHorizontally` 左移 30% 屏宽（`ParallaxFraction=0.3`）+ 模糊加深同步叠加——位移给「推进」层次、模糊给「退后」纵深（iOS 系统转场语言）；pop 时下层页从让位处 `slideInHorizontally(-30%)` 滑回原位、模糊同步恢复清晰。`exitTransition`/`popEnterTransition` 从 None 改回 `pushExit`/`popEnter`。掉帧顾虑已由既有隔离兜住（转场窗口摘采样层 + WebView 延迟挂载 500ms + 截图替身退出） |
| 2.5.36 | 2026-10-16 | **覆盖式转场重构（iOS push 核心语言）**：用户需求「新页从右滑入，后面页面慢慢套上模糊遮罩」。① 转场语言重写：push = 新页 `slideInHorizontally` 全宽滑入（440ms iOS 减速曲线）、旧页**原地不动**（视差删除）；pop = 上层页滑出（400ms）、下层页原地恢复。② 模糊遮罩：新建 `IOSPageSurface` 外壳——页面通过 `LocalMyRoute`（CompositionLocal）与 `currentRouteState`（全局快照，由 `OnDestinationChangedListener` 在导航提交瞬间写入）判断是否被覆盖，被覆盖时 `Animatable` 驱动 `Modifier.blur(18dp)`（GPU RenderEffect，minSdk 31 可用）+ `drawBehind` 28% 暗化同步加深。③ pop 恢复动画：`rememberSaveable lastTarget` 记住上一次覆盖态——pop 回到本页时初值为「曾被覆盖(1)」，随后目标变 0，从模糊平滑恢复清晰而非硬切。④ 常驻左缘投影 16dp 增强层叠感；transitioning 摘采样层窗口 440+40ms 兜底；GameScreen WebView 挂载延迟 360→500ms 对齐新转场 |
| 2.5.35 | 2026-10-16 | **底栏修缮 + 我的页面视觉统一**：① HyperOS4 底栏按用户截图（HyperOS 文件管理器）改小：容器 64→56dp、整体 `fillMaxWidth(0.86f)`、遮罩 inset 5→4dp、圆角 27→24、图标 24→22。**动画 bug 根因**：遮罩缩放/加深动画跑在 `collectLatest` 的子协程里——collectLatest 收到新值会取消 block 含子协程，快速按压/松开交替时动画被掐死在中间态弹不回来；移到外部 `rememberCoroutineScope` 启动后不受取消影响。② MD3 底栏同款 bug：指示器缩放同样被 collectLatest 掐死（快速连点后药丸缩小不恢复），同法修复。③ 我的页面视觉统一：panel 内 20sp/18sp 大标题全部改为 panel 外 14sp 中性色分组小标题（`SectionLabel`：通知/外观/通用），与更新设置/关于应用二级页同一语言；更新设置与关于应用合并进「通用」分组一个 panel |
| 2.5.34 | 2026-10-16 | **退出游戏转场根治（截图替身）**：2.5.33 的 `onPause()` 不够——WebView 哪怕暂停了也还在布局树里，布局层位移转场每帧 measure 它反而更卡（用户实测「比没修之前还掉帧」）。根治：`exitPage()` 时把 WebView 当前画面 `wv.draw(Canvas)` 成 Bitmap 截图，`ready=false` **立刻把 AndroidView 摘出组合**，转场期间显示 `Image(snapshot, FillBounds)` 静态替身——WebView 彻底退出转场布局，掉帧源消失；游戏画面原地保留不闪。截图失败降级纯背景退出。页面销毁时 `onRelease` 照旧 destroy |
| 2.5.33 | 2026-10-16 | **退出游戏掉帧/闪烁**：根因是 pop 转场期间 WebView 仍在每帧全屏自绘（卡），页面退出组合瞬间游戏画面又突然跳变成纯背景（闪）——同一根源两面。修复：`exitPage()`（返回按钮与 BackHandler 退出分支共用）先 `webView.onPause()` 冻结渲染（画面保留为静态纹理跟着页面滑走，goBack 分支不冻结），再 pop；转场空窗期加居中 `CircularProgressIndicator` 作视觉锚点，WebView 挂载时机 420→360ms（贴住 320ms 转场结束点） |
| 2.5.32 | 2026-10-16 | **游戏页白屏 + 转场掉帧**：① GameScreen 两处修复——**WebView 延迟到转场结束后挂载**（`LaunchedEffect delay(420)`；转场是布局层位移每帧 measure 两页，WebView 每帧全屏自绘，边滑边渲染必卡），转场期间显示 `iosBackground()` 占位；**WebView 透明背景 + `onPageFinished` 淡入**（200ms alpha），白屏闪烁消失。② 转场提速：PushMs 380→320、PopMs 300→260——转场位移是布局层位移（EnterExitTransitionModifierNode.measure，v2.5.23 反编译确认），每帧全页 measure+layout，时长越短掉帧窗口越小，观感也更利落 |
| 2.5.31 | 2026-10-14 | **工具箱接入奶蛙消消乐**：新建 `GameScreen`——全屏 `AndroidView(WebView)` 加载 `https://chenckx0614.github.io/naiwaxxl/`，不跳浏览器。WebView 设置：JS + DOM storage（游戏存档走 localStorage）+ `mediaPlaybackRequiresUserGesture=false`（BGM 自动播）；`BackHandler` 优先 `webView.goBack()`（退网页内弹层/关卡），退无可退再退页面；`onRelease` 里 `stopLoading+destroy` 防泄漏；左上角悬浮 `LiquidGlassBackButton`（backdrop 传 null 降级）。工具箱第 4 行「奶蛙消消乐 / 顺便玩一局，摸鱼必备」，`Screen.Game` 路由 + MainTabs `onNavigateToGame` |
| 2.5.30 | 2026-10-14 | **恢复滑动转场**：2.5.29 改的系统默认 fade 观感差（用户「渐变效果太垃圾」），恢复 push/pop 滑动转场——即最初从库（Nexio/HyperOS Activity 转场观感）迁移的那套：新页从右滑入、旧页 30% 视差让位、pop 比 push 短一截。掉帧顾虑已由 v2.5.23「转场窗口摘采样层」根治，不再是回退理由。库本身是 Activity 架构 + 系统级转场（`enableOnBackInvokedCallback` 预测式返回），Compose 内最接近的观感就是这套 slide+parallax |
| 2.5.29 | 2026-10-14 | **系统转场 + 捐赠支持页**：① 页面转场弃 iOS push/pop 视差 → **系统默认**（对齐 Nexio——库里页面切换也不用自定义转场）：`iosPage` 删四个转场参数，删 `pushEnter/pushExit/popEnter/popExit/iosEnter/iosExit/isFromGuide/Parallax/PopMs`；引导页 `guideExit`/`guideFinishEnter` 保留（Guide composable 单独传参）；`PushMs` 留给 transitioning 摘采样层窗口（380ms > 系统转场时长，恢复录制稍晚无害）。**附带收益**：转场期页面不再位移，v2.5.23 的全屏采样层每帧重录掉帧问题从根上消失。② 新建 `DonateScreen`（关于应用 → 捐赠支持）：结构参考 Nexio `AppreciateAuthorScreen` 的 `DonateQrCard`——正方形卡片 + 赞赏码图（`R.drawable.donate_qrcode`，用户提供的 1080×1080 png，ContentScale.Fit + 圆角 12），说明文字收尾；库的领奖台/捐赠明细需云端数据，暂不做。关于页捐赠行从 Toast 改跳转 |
| 2.5.28 | 2026-10-14 | **修关于页闪退 + 反馈改页内展开**：① **闪退根因**：`painterResource(R.mipmap.ic_launcher)` 崩——`mipmap-anydpi-v26/ic_launcher.xml` 是 adaptive-icon XML，painterResource 不支持该格式，运行时抛异常（进关于页即闪退）。修复：手动 `ContextCompat.getDrawable` → Canvas 渲染成 Bitmap → `Image(bitmap=...)`（AdaptiveIconDrawable 无固有尺寸，intrinsicWidth 可能为 -1，兜底 512；coil3 没有 `rememberDrawablePainter` 不能偷懒）。② 交流与反馈删独立 `FeedbackScreen`（文件/路由/设置页入口全撤），改为关于页卡片 1 内的展开行（点击展开占位文案，chevron 0°→90° 旋转 + expand/shrinkVertically），与更新日志展开交互一致 |
| 2.5.27 | 2026-10-14 | **关于应用页 Nexio 化**：克隆 gitee `com_haooz_account/hyper_schedule` 拿到 `AboutActivity.kt` 源码，按其信息架构重写 `AboutScreen`——① 大图标 88dp（本应用 `R.mipmap.ic_launcher`，圆角 22）+ 应用名 34sp Bold + `v{versionName}-{versionCode}` 居中；② 卡片1：交流与反馈 + 捐赠支持（trailing「请作者喝杯咖啡」，点击 Toast 占位）；**项目仓库一行按需求去掉**；③ 卡片2：更新日志头部行（跳完整日志页）+ 内嵌最近 3 条日志（第一条默认展开、点击收起、chevron 旋转 ±90° + expandVertically/shrinkVertically），数据复用 `UpdateLogScreen.recentLogs`（private → public）。Nexio 的 textureBlur/logo 渐隐滚动未搬——本项目用 IOS26StickyPage 吸顶导航体系，混两种模糊体系无收益 |
| 2.5.26 | 2026-10-14 | **关于应用 + 交流与反馈**：① 新建 `AboutScreen`（我的 → 关于应用）：应用图标（`R.mipmap.ic_launcher`，96dp 圆角 22）+ LinkGrab + 副标题 + 版本号居中展示，面板里「更新日志」「交流与反馈」两行入口，免责声明小字收尾——原设置页底部「关于」面板整体搬入并删除。② 新建 `FeedbackScreen`（交流与反馈）：占位页「敬请期待」，内容后续再补。入口两处：设置页单独一行 + 关于页里一行。`Screen.About`/`Screen.Feedback` 路由，MainTabs 的 `onNavigateToUpdateLog` 被 `onNavigateToAbout`/`onNavigateToFeedback` 取代（更新日志入口现在只从关于页进） |
| 2.5.25 | 2026-10-14 | **通知不消失修复 + 更新设置二级页**：① LiveUpdatesService 通知卡死两处根因——`updateLiveUpdate`/`finishLiveUpdate` 用 `notificationManager.notify()` 发通知（不挂服务前台生命周期，服务 stopForeground 后迟到的 UPDATE 会发出「ongoing 但无人移除」的孤儿通知，永远划不掉）→ 改走幂等 `startForeground()`；finish 的 2 秒延迟停止无 removeCallbacks（finish 后立刻开始新一轮，旧定时器砍掉新通知）→ `mainHandler` + `finishRunnable`，任何新指令先撤定时器，STOP/finish 兜底 `cancel(NOTIFICATION_ID)`。② 「我的」里检查更新挪进新建 `UpdateSettingsScreen` 二级页（当前版本 + 检查更新 + 更多设置：自动检查更新 / 更新提醒开关，DataStore 持久化）；手动检查发现新版本始终弹窗，自动检查弹不弹由「更新提醒」开关决定（`manualUpdateCheck` 标记） |
| 2.5.24 | 2026-10-13 | **预览体验优化**：① 图片预览弃纯黑背景 → `EnableDialogBlurBehind()`（`FLAG_BLUR_BEHIND` + `blurBehindRadius=24dp`），身后的结果页被系统实时模糊，`drawBehind` 压 0.82 暗遮罩；省电模式（跨窗口模糊关闭）回退 dim 0.55 兜底。② 视频播放补齐控制：Media3 `useController=false`（外层 `.clickable` 吃掉全部触摸导致自带控制器永不显示），自绘 `VideoControlsOverlay`——点画面切换显隐、播放中 3 秒自动隐藏（暂停常驻）、暂停/播放 + 快退/快进 10 秒（Replay10/Forward10）+ 可拖动进度条与 m:ss 时间、seek 带 Confirm 触觉。**踩坑**：`EnableDialogBlurBehind` 复制到新文件时漏 `DialogWindowProvider` import，`window` 类型未知 → `blurBehindRadius`/`dimAmount` 报未解析（错误指向字段而非 import，易误判）；预览底栏 `shape`/`effects` lambda 必须 `remember` 固定，否则每重组重录采样层 |
| 2.5.23 | 2026-10-13 | **优化页面转场掉帧**：根因是 `NavHost` 整块被 `.layerBackdrop(liquidBackdrop)` 录进采样层（底栏玻璃要采样它），转场时页面每帧位移 → **全屏采样层每帧重录**，底栏再对它做一次模糊采样，两笔全屏开销叠在一帧里。改为用 `NavController.OnDestinationChangedListener` 标记转场窗口，期间摘掉录制（层里保留上一稳定帧，底栏玻璃照常显示），转场结束恢复。**另注（踩坑）**：`IOSPageSurface` 的左缘层次不能用 `drawBehind` 渐变替代 `shadow(clip=false)` —— shadow 画在盒子**外侧**（停稳时在屏幕外不可见），drawBehind 画在**内侧**（停稳时会在屏幕左边露出深色竖带），已回退 |
| 2.5.22 | 2026-10-13 | **更新弹窗改为 Nexio UpdateDialog 样式**（原来是原生 Material `AlertDialog`）：① 抽出可复用 `IOS26GlassDialog` 玻璃外壳（统一处理 `FLAG_BLUR_BEHIND` 窗口模糊 + 玻璃面板 + 省电模式回退），删除确认弹窗改为复用它，不再各写一遍。② 信息架构照搬 Nexio：标题/摘要随下载状态变化、更新说明仅在待下载时展示（下载/安装时让位进度条）、主按钮「更新/正在下载/安装/重试」与次按钮「稍后/后台下载/关闭」随状态变化、按钮带 Confirm 触觉。③ 进度条改 iOS 细条（圆角蓝条圆头）。④ **任何时候都能关闭**：下载中「后台下载」只收起弹窗、下载继续；系统安装器被取消不会卡死在「安装中」 |
| 2.5.21 | 2026-10-13 | **UI/图标/UX 审计与视觉对齐**：① 新增 `IOS26IconSize` 尺寸档位（Inline 18 / Disclosure 20 / MenuItem 22 / Navigation 24 / Hero 26 / Display 30），各页散写的 14~26dp 全部归入档位。② 修掉三处「用文字当图标」（历史记录 ✓、结果页实况 ▶、播放覆盖层 ▶）→ 改用 `Check` / `MotionPhotosOn` / `PlayArrow` 真图标，消除字体渲染差异与读屏念出「▶」。③ 统一列表箭头色调（历史记录蓝 / 工具箱灰 → `iosDisclosureColor()`）。④ `SmallRoundButton` 触控目标 34dp→44dp（视觉圆仍 34dp，无障碍下限）。⑤ 引导页选中圈对齐历史记录（24dp 圆 + 16dp 勾） |
| 2.5.20 | 2026-10-12 | **从 Nexio/hyper_schedule 迁移 iOS 26 风格交互**：① 设置开关 `IOS26Switch` 改为 miuix 风格（滑块弹簧 stiffness 987、按压/拖拽放大 1.127× 挤缩、轨道颜色弹簧过冲、可横拖翻状态、点击与拖拽端点 ToggleOn/Off 触觉）。② `IOS26Button` 新增可选 `backdrop` 参数（默认 null，旧调用零改动），传入时改用库 vibrancy+blur+lens 折射做液态玻璃。**踩坑**：设置页内按钮若采样「包含自身」的内容采样层 = 自我引用 → backdrop 库递归绘制崩溃（进设置页即闪退）；已撤回设置页的按钮玻璃接法，`IOS26StickyPage.contentBackdrop` 钩子保留但暂不使用，待改成「独立底图采样层（不含按钮）」后再接 |
| 2.5.19 | 2026-10-12 | **删除确认弹窗液态玻璃化**：Dialog 是独立窗口，backdrop 库的 `LayerBackdrop` 录制层与坐标系都跨不过窗口采样不到主窗口内容 → 毛玻璃改走系统级 `FLAG_BLUR_BEHIND` + `blurBehindRadius=24dp`（minSdk 31 全设备可用），面板压半透明玻璃底色（模糊时 0.72 / 关闭时 0.96）+ 顶部高光渐变 + 玻璃描边，取消键改半透明；系统关闭跨窗口模糊（省电模式，`isCrossWindowBlurEnabled=false`）时回退不透明底色 + dim 加深 |
| 2.5.18 | 2026-10-12 | **修右上角「更多」菜单点开后没有毛玻璃模糊**：① 面板底色不透明度过高（收起 0.76/0.84，照搬顶栏按钮）把 blur 采样出的内容盖死 → 展开态降到 0.32/0.44（按展开进度 8 档量化过渡）；`LiquidGlassDropdownMenu` 玻璃 `onDrawSurface` 改为按档量化 alpha 的 `drawRect`。② `IOS26StickyPage` / `ResultScreen` 的毛玻璃录制层只录了内容列表、且停在顶部时不挂载，菜单盖住的头部空白区在层里是透明的 → 改为把页面底色画进录制层（透明区变可糊），且 `ResultScreen` 菜单可见时强制 `rememberBackdropLayerActive(headerScrolled || menuVisible)` 挂载录制层 |
| 2.5.17 | 2026-10-12 | **修小红书解析不出来（三个独立缺陷）**：① Shield 除 TLS 指纹外还比对 UA 与 `sec-ch-ua`（Client Hints），两者版本号不一致即判伪造客户端 302 到登录页；`CronetFetcher.browserHeaders` 补齐 sec-ch-ua 三件套（版本号从 UA 解析保证自洽），UA 版本由 `ApiVersion.getCronetVersion()` 动态取（不再硬编码 151）。② 图片地址是明文 http，`targetSdk=36` 下 Android 默认禁止 → `MediaResult.withSecureUrls()` 统一升级 https。③ 图片地址带时效签名（实测约 1 小时后 403）且是压缩图 → `pickImageUrl` 改取无签名原图 `https://sns-img-{区域}.xhscdn.com/{fileId}`（与 XHS-Downloader 同思路，实测同一张图压缩版 90KB / 原图 1.8MB）。新增 `getPage()` 返回最终 URL，短链展开与主抓取合并成一次 Cronet 请求；移动端 UA 降级为串行兜底（实测它对分享链接稳定失效）。更新日志按主题归并 + 归档折叠 |
| 2.5.16 | 2026-10-12 | 移植 Nexio `LiquidTopBarButton` → `LiquidGlassTopBarButton`（通用图标形态，`LiquidGlassBackButton` 变为其返回形态，默认 42dp、玻璃色 0.76/0.84）；移植 `LiquidGlassDropdownMenu`（42dp 圆↔200dp 面板容器变换 + 跟手滑选）与 `DropdownPanelDragSelect`；`IOS26StickyPage` 新增 `overlay: BoxScope.(Backdrop) -> Unit` 槽位；历史记录与解析结果右上角「选择」改为该菜单 |
| 2.5.15 | 2026-10-12 | 修底栏滑到最左/最右时页面被拽回中间页（`NexioBottomTabs` 回灌环路：`currentIndex` 变更流里多调了一次 `onTabSelected`，且 pager 翻页途中的中间 `currentPage` 被回灌；新增 `pendingTarget` 锁定 + 1.2s 保险丝）；底栏标签排版（图标 24dp 方盒居中 + 文字 12sp 固定行高）；页序改「首页/我的/工具箱」，设置→我的（图标 Person） |
| 2.5.14 | 2026-10-12 | 底栏风格正式更名 iOS27（Nexio→iOS27，接管默认位，非新选项）；学习 Nexio 移植顶栏渐进沉浸（blur+DstIn 溶解+多档遮罩，替代硬边渐隐条）、`LiquidGlassBackButton`（液态返回按钮，新增 LiquidDragTransform 形变算法）、按钮 Nexio 形变+触觉、`IOS26BlurHeader` 重写；主页三 tab 改 `HorizontalPager`（spring 320 相邻/240 跨页，`Screen.Home/Toolbox/Settings` 合并为 `Screen.MainTabs`） |
| 2.5.13 | 2026-10-12 | 修复 iOS27 底栏被手势捕获层撑到屏幕正中、不贴底（BoxWithConstraints 需显式高度）；接管原 iOS27 默认位置（设置页四档：iOS27/HyperOS4/HarmonyOS 7/Material 3，无独立新选项）；MD3 底栏 80dp→68dp 收紧 |
| 2.5.12 | 2026-10-12 | 底栏新增液态玻璃风格（学习自 gitee hyper_schedule，AGPL-3.0）：三层玻璃结构 + 统一手势层（按下胶囊飞入保持按压/拖动 1:1 跟手/松手吸附）；设为默认 |
| 2.5.11 | 2026-10-12 | 底栏新增 Material Design 3 风格（80dp 通栏实底 + 64×32dp 药丸指示器 + ripple） |
| 2.5.10 | 2026-10-11 | 抖音解析调研修复：iteminfo 接口已死（恒定 encrypt_data_miss）移除；slidesinfo 健康留主通道；SSR 分享页需 ttwid 才内嵌 item_list，ttwid 失效时强制刷新重试 |
| 2.5.9 | 2026-10-11 | 小红书新增官方 feed 接口通道：WebView 注入 JS 借页面签名器 window._webmsxyw 调 /api/sns/web/v1/feed（SSR 抠不到 state 时的兜底），image_list 清洗 /sns-webp→/sns-img 原图并截 ! 后缀；实况/视频同样支持 |
| 2.5.8 | 2026-10-10 | 按钮交互对齐 Apple Liquid Glass 规范：按压生长（非缩小）+ 触摸点照亮 + lens 折射；预览页返回/底部操作栏改真玻璃采样（backdrop blur），底部操作合并单块玻璃胶囊 |
| 2.5.7 | 2026-10-10 | 转场修复：详情页恢复 iOS 视差 push/pop（380/300ms）；预览页返回按钮/操作胶囊升级真·iOS 27 液态按压（高光跟手 + 跟手压缩 + 果冻回弹 + 触感反馈），新增 LiquidPreviewButton |
| 2.5.6 | 2026-10-10 | 图片预览返回按钮新增 iOS 27 液态按压反馈：跟手压缩、局部高光折射与弹性回弹 |
| 2.5.5 | 2026-10-10 | 重做图片预览 UI：顶部导航与页码、渐变承托、底部浮动操作栏；保留缩放、翻页、实况播放和下载能力 |
| 2.5.4 | 2026-10-10 | 小红书新增 explore / discovery/item 双页面入口回退并保留 xsec 签名参数；参考仍在维护的 XHS-Downloader、xiaohongshu-cli、ucmao/media-parser SSR 解析方案 |
| 2.5.3 | 2026-10-10 | 优化 iOS 27 底栏流畅度：移除高频拖拽重组路径，触摸移动直接更新绘制状态；降低静态玻璃模糊开销 |
| 2.5.2 | 2026-10-10 | 补齐结果页、历史记录、更新日志、音乐和工具页的淡入缩放过渡动画；主标签页保留方向滑动 |
| 2.5.1 | 2026-10-10 | 动画转场提速与减轻；小红书 Cronet 桌面/移动 UA 并行竞速；平台链接正则预编译复用，降低解析开销 |
| 2.5.0 | 2026-10-09 | 图片预览重做（滑动翻页/双击缩放/捏合拖动/单击隐控）；抖音补 ttwid 会话 Cookie + 分享页双策略兜底；小红书 Cronet 主通道补 a1/webId 会话 + 移动端 UA 重试；图片下载直写原始字节（免解码重压缩）；批量下载改串行队列 + 聚合进度 |
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
