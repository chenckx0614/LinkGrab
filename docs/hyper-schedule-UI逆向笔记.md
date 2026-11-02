# hyper_schedule（Nexio 课程表）UI 逆向笔记

> 源码：https://gitee.com/com_haooz_account/hyper_schedule
> 分析版本：master（2026-11，Compose Multiplatform 1.12.0 / miuix-kmp 0.9.4）
> 本笔记只讲"控件怎么调、UI 怎么搭"，不涉及业务逻辑。

---

## 0. 一句话结论

这是一个 **KMP（Android + iOS + 鸿蒙 + 桌面）** 项目，UI 完全建立在两套开源库上：

- `top.yukonga.miuix.kmp` —— miuix/HyperOS 风格 Compose 组件库（Apache-2.0，**可以直接依赖**）
- `com.kyant.backdrop` + `com.kyant.capsule` —— 液态玻璃采样/模糊/折射 + 连续曲率圆角（fork 自 kyant0，Apache-2.0）

项目自己在上面叠了一层：**主题双层包裹 → 自研路由 → 玻璃脚手架 → 自研液态玻璃原子控件**。
真正值得抄的是"玻璃怎么分层采样"和"吸顶栏三件套"，控件本体直接引依赖即可。

| 模块 | 文件数 | 行数 | 作用 |
|---|---|---|---|
| `miuix/` | 130 | 31.9k | fork 的 miuix-kmp，加了玻璃/材质注入点 |
| `backdrop/` | 69 | 6.5k | fork 的 backdrop + capsule（squircle） |
| `ui-shared/` | 234 | 77.2k | 所有页面、路由、主题、自研玻璃控件 |
| `core/` | 136 | 27.7k | 数据/SQLite/教务导入 |
| `androidApp/` | 57 | 17.0k | 宿主、Activity、桌面小部件 |

---

## 1. 主题：5 层包裹，顺序不能变

`ui-shared/.../ui/theme/Theme.kt` → `CourseScheduleTheme()`

```kotlin
MiuixTheme(
    controller = controller,          // ThemeController，必须复用同一实例
    content = {
        // ⚠ MaterialTheme 会自带 LocalIndication = ripple() 和 LocalTextStyle = bodyLarge，
        //   把 miuix 的这两个顶掉。所以先捕获，进了 MaterialTheme 再写回去。
        val miuixIndication = LocalIndication.current
        val outerTextStyle = LocalTextStyle.current

        CompositionLocalProvider(
            LocalScreenCornerRadius provides screenCornerRadius,   // 物理屏幕圆角
            LocalHapticFeedback provides gatedHaptic,              // 全局触感开关
            LocalGlassBlurDp provides AppMaterialSettings.glassBlurDp(),
            LocalMaterialNoneEnabled provides AppMaterialSettings.isNoneTier(),
            LocalEdgeLightResolver provides edgeLightResolver,
            LocalMiuixTextMotion provides AppTextMotion,           // 换字动效
        ) {
            MaterialTheme(colorScheme = materialColors) {
                CompositionLocalProvider(
                    LocalIndication provides miuixIndication,
                    LocalTextStyle provides outerTextStyle,
                ) { content() }
            }
        }
    },
)
```

**三个坑（都是真实踩过的）**

1. `MiuixTheme` 的 `ThemeController` 要 `remember { }` 住，切主题只改 `controller.colorSchemeMode = mode`，重建实例会掉一帧。
2. 同时用 Material3 就必须做上面那套 Indication/TextStyle 存取，否则所有 `clickable` 变成水波纹、`Text` 行高丢失。
3. 给 miuix 注入的 lambda（如 `LocalEdgeLightResolver`）身份必须随档位变化，它被当 remember key 用；身份不变会一直复用旧描边。

**配色/字排字段**（`MiuixTheme.colorScheme` / `.textStyles`）

- `Colors`：`primary / onPrimary / primaryVariant / secondary / secondaryContainer / background / onBackground / onBackgroundVariant / surface / onSurface / surfaceVariant / onSurfaceSecondary / onSurfaceVariantSummary / onSurfaceVariantActions / disabledOnSurface / surfaceContainer / windowDimming / sliderKeyPoint …`（共 60+，含 disabled 全系）
- `TextStyles`：`main / paragraph / body1 / body2 / button / footnote1 / footnote2 / headline1 / headline2 / subtitle / title1…title4`

---

## 2. miuix 控件全景（可直接依赖的 API）

依赖坐标（Android）：

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
// 注意：0.9.4 是用 Compose 1.12.0 编译的，CMP 版本要跟着换，否则签名不匹配
```

### 2.1 基础控件签名（节选，参数名照抄即可）

```kotlin
Card(modifier, cornerRadius: Dp = CardDefaults.CornerRadius,
     insideMargin: PaddingValues = CardDefaults.InsideMargin,
     colors: CardColors = CardDefaults.defaultColors(), content: @Composable ColumnScope.() -> Unit)

Button(onClick, modifier, enabled = true, cornerRadius = ButtonDefaults.CornerRadius,
       minWidth, minHeight, colors: ButtonColors = ButtonDefaults.buttonColors(), content)

TextButton(text: String, onClick, modifier, enabled, cornerRadius, ..., colors)

IconButton(onClick, modifier, enabled = true, holdDownState: Boolean = false,
           backgroundColor: Color = Color.Unspecified, cornerRadius, minHeight)

Surface(modifier, shape = SurfaceDefaults.Shape, color, contentColor, border, shadowElevation, content)
Surface(onClick, modifier, enabled, shape, color, contentColor, border, content)   // 可点击版

Scaffold(modifier, topBar, bottomBar, floatingActionButton, floatingActionButtonPosition, content)

SmallTitle(text: String, modifier, textColor = Color(0xFF8F9CAE),
           insideMargin, hasWallpaper: Boolean = false)     // hasWallpaper 自动加描边

TabRow(tabs: List<String>, selectedTabIndex, onTabSelected, modifier, colors, minWidth, maxWidth)
TabRowWithContour(...)                                       // 带外轮廓的版本

TopAppBar(title, modifier, color, titleColor, largeTitle, largeTitleColor, ...)
SmallTopAppBar(title, modifier, color, titleColor, subtitle, ...)

SearchBar(inputField: @Composable () -> Unit, onExpandedChange, modifier, insideMargin,
          expanded = false, actionIcon: ImageVector?, onActionClick, ...)

Switch(checked, onCheckedChange, modifier, colors, enabled)
Checkbox(state: ToggleableState, onClick, modifier, colors, enabled)
RadioButton(selected, onClick, modifier, colors, enabled)

Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors)
RangeSlider(value: ClosedFloatingPointRange<Float>, onValueChange, ...)
VerticalSlider(...)

LinearProgressIndicator(modifier, progress: Float?, colors, height)
CircularProgressIndicator(modifier, progress: Float?, colors, strokeWidth)
InfiniteProgressIndicator(modifier, color, size, strokeWidth)

NumberPicker(value: Int, onValueChange, modifier, enabled, range, label, visibleItemCount = 5,
             wrapAround: Boolean = false, itemHeight, textStyle)
```

### 2.2 Preference 系列（设置页专用）

```kotlin
ArrowPreference(title, modifier, titleColor, summary, summaryColor,
                startAction, endActions: @Composable RowScope.() -> Unit, bottomAction,
                insideMargin, onClick, holdDownState: Boolean = false, enabled)

SwitchPreference(checked, onCheckedChange: (Boolean) -> Unit, title, modifier,
                 summary, enabled)

CheckboxPreference(title, checked, onCheckedChange, modifier, summary,
                   checkboxLocation = CheckboxLocation.End)      // 勾选块放右

RadioButtonPreference(title, selected, onClick, modifier, summary, colors)

SliderPreference(value, onValueChange, modifier, title, summary, ...)
RangeSliderPreference(value, onValueChange, ...)

OverlayDropdownPreference(items/entry/entries, title, ...)       // 弹层下拉
OverlaySpinnerPreference(items/entry/entries, title, ...)        // 滚轮弹层
```

项目里实际用量：`ArrowPreference` 91 处、`SwitchPreference` 20 处、`CheckboxPreference` 3 处；
`Slider(Preference)` / `RadioButtonPreference` / `TabRow` / `Snackbar` / `Divider` **一处都没用**——分隔靠 `Arrangement.spacedBy(12.dp)`，提示用 toast，三选一用"缩略图 + Switch"。

### 2.3 其他成套的

`FloatingActionButton` / `FloatingToolbar` / `NavigationBar` / `FloatingNavigationBar`(`+Item`) /
`NavigationRail`(`+Item`) / `PullToRefresh` / `VerticalScrollBar` / `HorizontalScrollBar` /
`SnackbarHost` / `Badge` / `BadgedBox` / `TooltipBox` / `RichTooltipBox` / `ColorPicker` 全家桶（HSV / OkHSV / OkLab / OkLch）/ `ListPopup`。

弹层类（`overlay/`）：`OverlayDialog`、`OverlayBottomSheet`、`BlurBottomSheet`、`ResizableBlurBottomSheet`、`OverlayListPopup`。

---

## 3. 页面骨架：`DocumentPageScaffold`

全项目二级页统一用它，**玻璃采样层只包内容，顶栏按钮在采样层外面**（否则自己采样自己）。

```kotlin
val backgroundColor = MiuixTheme.colorScheme.surface
val liquidGlassBackdrop = rememberLayerBackdrop()      // com.kyant.backdrop
val scrollBehavior = rememberSharedScrollBehavior()
val pageOverScrollState = remember { OverScrollState() }

CompositionLocalProvider(LocalOverScrollState provides pageOverScrollState) {
    Scaffold(
        modifier = Modifier
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
        topBar = {
            ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
                topInsetAdjustment = nativeBarInset,
                noTopStatusBar = hostVerticalBarEdge(),
            ) {
                CollapsibleTopAppBar(
                    title = title, largeTitle = title,
                    scrollBehavior = scrollBehavior,
                    contentPadding = {},
                    startAction = { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = onBack, backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.ChevronBackward, iconSize = 25.dp,
                            backdropAlpha = backdropAlpha, shadowAlpha = shadowAlpha,
                        )
                    },
                )
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop, recordKey = recordKey)) {
                content(scrollBehavior, liquidGlassBackdrop)
            }
            overlay?.invoke(this, liquidGlassBackdrop)     // 浮层
        }
    }
}
```

---

## 4. 滚动吸顶：三件套，少一件就失效

```kotlin
val scrollBehavior = rememberSharedScrollBehavior()      // ① 状态

LazyColumn(
    modifier = Modifier
        .collapsibleTopInset(scrollBehavior)              // ② 布局期读高度，不进组合
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .scrollEndHaptic(HapticFeedbackType.TextHandleMove)
        .overScrollVertical(),                            // 放最后 = 最贴近列表
    contentPadding = PaddingValues(
        top = paddingValues.calculateTopPadding()
              + CollapsibleTopAppBarDefaults.CollapsedHeight,   // 52.dp 基准
    ),
)
```

**不要**写 `Modifier.padding(top = scrollBehavior.currentHeightPx)`——组合期读高度会逐帧重组整页。

`ProgressiveBlurTopBar` 的模糊半径**不由调用方传**，内部按材质档决定（AGSL 两趟 / 假渐进 / 渐变遮罩），调用方只控 `blurAlpha`、`tintIntensity`、`maskAlpha`：

```kotlin
ProgressiveBlurTopBar(
    backdrop, modifier, height,
    tintIntensity = 0.2f, tintColor, blurAlpha = 1f,
    useFakeProgressiveBlur, useGradientMask,
    maskAlpha: () -> Float,              // 材质整体淡入淡出
    edgeFadeStart = 0.88f,
    resampleKey, sampleTrack: () -> Float,
    topInsetAdjustment, noTopStatusBar,
)
```

`maskAlpha` 的典型实现是一个 0/1 弹簧：

```kotlin
fun rememberTopBarMaterialAlpha(materialVisible: Boolean): () -> Float {
    val alpha = remember { Animatable(if (materialVisible) 1f else 0f) }
    LaunchedEffect(materialVisible) {
        alpha.animateTo(
            if (materialVisible) 1f else 0f,
            if (materialVisible) folmeSpring(damping = 1.0f, response = 0.6f)
            else folmeSpring(damping = 1.0f, response = 0.4f),
        )
    }
    return remember(alpha) { { alpha.value } }
}
```

---

## 5. 液态玻璃：三段式模型

```
① 录制层   Modifier.layerBackdrop(bg, recordKey)   把子树录进 GraphicsLayer（离屏缓存）
② 采样层   Modifier.drawBackdrop(backdrop, shape, effects)  按卡片世界坐标取像素 + 模糊/折射
③ 着色层   onDrawSurface / highlight / shadow / innerShadow / edgeLight
```

`drawBackdrop` 内部绘制顺序固定：
`onDrawBehind → 采样(含 RenderEffect) → onDrawSurface → drawContent() → onDrawFront`，整段被 `clipPath` 裁进 shape。

### 5.1 最小可用玻璃卡片

```kotlin
@Composable
fun LiquidCard(blurDp: Float = 8f, corner: Dp = 20.dp, tint: Color = Color.White.copy(alpha = .12f)) {
    val bg = rememberLayerBackdrop()                    // 只录背景，绝不能录卡片自己
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(bg, recordKey = wallpaperKey)) {
            BackgroundImage()
        }
        val shape = remember(corner) { ContinuousRoundedRectangle(corner) }
        val blurPx = with(LocalDensity.current) { remember(blurDp) { blurDp.dp.toPx() } }

        // ⚠ 三个 lambda 都必须 remember：内联 lambda 每次重组换引用，
        //   DrawBackdropElement 会判不等并重建 RenderEffect
        val effects: BackdropEffectScope.() -> Unit = remember(blurPx) {
            { vibrancy(); blur(blurPx); lens(6.dp.toPx(), 36.dp.toPx()) }
        }
        val surface: DrawScope.() -> Unit = remember(tint) { { drawRect(tint) } }

        Box(
            Modifier
                .drawBackdrop(
                    backdrop = bg,
                    shape = { shape },
                    effects = effects,
                    highlight = null,        // 默认是 Highlight.Default，不要就显式 null
                    shadow = null,
                    onDrawSurface = surface,
                )
                .edgeLight(shape = shape),   // 描边高光，独立 modifier，不占 RenderEffect
        )
    }
}
```

项目里的**性能短路**（很重要）：

```kotlin
val needsSampling = blurRadius > 0f || refraction != CardRefractionLevel.OFF
modifier.then(
    if (needsSampling) Modifier.drawBackdrop(backdrop, shape = { shape }, effects, highlight = null, onDrawSurface = surface)
    else Modifier.background(flatSurfaceColor, shape)     // 模糊 0 就不要采样
)
```

### 5.2 关键签名

| API | 签名要点 |
|---|---|
| `rememberLayerBackdrop` | `(graphicsLayer = rememberGraphicsLayer(), recordScale = 1f, onDraw = { drawContent() })` |
| `Modifier.layerBackdrop` | `(backdrop, recordKey: Any? = null, mustRecord: (() -> Boolean)? = null, recordScale)`；`recordKey = null` 表示**每帧重录** |
| `Modifier.drawBackdrop` | `(backdrop, shape: () -> Shape, effects, highlight = { Highlight.Default }, shadow = { Shadow.Default }, innerShadow = null, layerBlock, exportedBackdrop, viewport, onDrawBehind, onDrawBackdrop, onDrawSurface, onDrawFront)` |
| `Modifier.drawPlainBackdrop` | 同上但去掉 highlight/shadow/innerShadow，最省 |
| `blur(radius, edgeTreatment = TileMode.Clamp)` | 半径同时抬高内部 `padding`（采样区外扩、离屏缓冲变大） |
| `lens(refractionHeight, refractionAmount, depthEffect = false, chromaticAberration = false)` | **仅 API33+**，Shape 不是圆角矩形类时静默跳过 |
| `vibrancy()` / `colorControls(b, c, s)` / `opacity(a)` | `vibrancy` = saturation 1.5 |
| `Highlight(width = .5.dp, blurRadius = width/2, alpha = 1f, style = Default)` | Default：白 50%、Plus 混合、45° |
| `Shadow(radius = 24.dp, offset = DpOffset(0, radius/6), color = Black α.1)` | |
| `InnerShadow(radius = 24.dp, offset = DpOffset(0, radius), color = Black α.15)` | |
| `EdgeLight(width = 1.dp, blurRadius = 2.dp, intensity = 1f, style = Uniform())` | `Uniform / Directional(45f) / Glow(10f)`，Plus 混合 |
| `Modifier.edgeLight(shape, edgeLight = EdgeLight.Default, outlineRevision)` | 独立 pass，不吃 RenderEffect |
| `rememberCombinedBackdrop(b1, b2, …)` | 多层叠画（内容 + 顶栏 + 底栏） |
| `SharedBlurBackdrop(source)` | 预烘焙模糊（`BLUR_BAKE_SCALE = 0.5f`），采样侧跳过 `blur()` |

### 5.3 性能档位（`ui/utils/AppMaterialSettings.kt`）

```kotlin
BEST / BALANCED / PERFORMANCE / NONE

chromeLensEnabled()      = level == BEST              // 折射只在最好的档
glassEffectsEnabled()    = level != NONE              // 总闸
glassBlurDp()            = NONE→0f, BEST→4f, else→8f // BEST 有折射所以模糊给少
progressiveBlurUseFake() = level == PERFORMANCE       // 真 AGSL 渐进模糊 → 等值 blur + alpha 淡出
resolveEdgeLight(...)    = PERFORMANCE/NONE: blurRadius=0、SrcOver、同色提亮 0.2
normalize(level)         = !isRenderEffectSupported() ? NONE : level
```

成本排序：`lens`（额外一次 AGSL 全屏 pass）> `blur`（离屏缓冲，半径决定尺寸）> `recordScale`（blit 面积 ∝ rs²）> `highlight/shadow`（独立 GraphicsLayer）。
省帧关键：`recordKey`（静态背景整段跳过录制）、`mustRecord`（draw 期判断滚动中才重录）、`viewport`（视口外不采样）、`needsSampling` 短路。

### 5.4 降级链（API < 31）

```
isRenderEffectSupported()  = SDK_INT >= 31
isRuntimeShaderSupported() = SDK_INT >= 33
```

逐层静默降级，不抛异常：`blur()` 首行 return → `renderEffect == null && padding == 0` → 走**直采路径**（不分配 GraphicsLayer，直接把录制层画上去）= 清晰壁纸 + 半透明着色。
项目策略更狠：能力不足直接把材质锁死为 NONE，卡片退化成 `Modifier.background(实色, shape)`，连录制层都不挂。

---

## 6. 连续曲率圆角（squircle）

```kotlin
val shape   = ContinuousRoundedRectangle(20.dp)                     // 四角同半径
val capsule = ContinuousCapsule()                                   // 胶囊
val mixed   = ContinuousRoundedRectangle(topStart = 20.dp, topEnd = 20.dp,
                                         bottomEnd = 4.dp, bottomStart = 4.dp)
val inner   = shape.concentricInset(6.dp)                           // 同心内缩：内圆角 = 外圆角 - padding
```

- `continuity` 默认 `G2Continuity()`：角部插一段三次贝塞尔做曲率连续，出参是 `Outline.Generic(Path)`（不是 `Outline.Rounded`）。
- 想退回普通圆弧传 `G1Continuity()`，直角传 `G0Continuity()`。
- 便利 modifier：`continuousBackground / continuousClip / continuousSurface / continuousBorder`。
- 坑：`Outline.Generic` 会让 `drawBackdrop` 走 `clipPath` 分支，没命中圆角快路径缓存 → **shape 一定 remember 住**。

---

## 7. 动效参数（可直接抄的数值）

```kotlin
// 项目自带的 folme 风格弹簧（stiffness = (2π/response)²）
folmeSpring(damping = 1.0f, response = 0.6f)    // 出现
folmeSpring(damping = 1.0f, response = 0.4f)    // 消失
folmeSpring(damping = 0.9f, response = 0.38f)   // BottomSheet

// 面板展开/收起/回弹
spring(dampingRatio = 0.77f, stiffness = 220f, visibilityThreshold = 0.0001f)
spring(dampingRatio = 0.77f, stiffness = 420f, visibilityThreshold = 0.0001f)
spring(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
spring(dampingRatio = 0.52f, stiffness = 220f, visibilityThreshold = 0.0001f)   // 过冲回弹

// 按压反馈：150ms 同时驱动 alpha 与 scale
pressAlpha: 0f→1f (tween 150)
pressScale: 1f→0.92f (tween 150)

// 列表项入场（必须先 snap 再 animate，否则从初始值补间）
scale 0.8f→1f (tween 350) / alpha 0f→1f (tween 300)

// 自定义缓动
OobeQuartOutEasing       = 1 - (1-t)^4.5
OobeQuartOutSoftStart    = 前 48% 用三次曲线软启动，再接 ^4.5
SecondaryPageExitEasing  = CubicBezier(0.36, 0.18, 0.3, 0.85)
```

导航转场常量：`NAV_ENTER_DURATION = 600`、`NAV_EXIT_DURATION = 320`、`NAV_PARALLAX = 0.24f`、`NAV_BG_DIM = 0.42f`。

---

## 8. 值得单独看的自研件

| 文件 | 干什么 | 亮点 |
|---|---|---|
| `components/LiquidFusion.kt` | 融球参数的唯一来源 | 把"鼓包 → 拉颈 → 并体"三阶段的 11 个量写成 data class，形状与折射共用同一实例，否则颈边留缝 |
| `components/FusionPairShape.kt` | 双胶囊距离场焊接 | smooth-min 场融合，`weldRadiusDp` 控制焊缝宽度 |
| `components/LiquidBottomTabs.kt` + `LiquidBottomBarShape.kt` | 底栏玻璃 + 颈融合 | |
| `effects/liquidglass/DampedDragAnimation.kt` | 1:1 跟手拖拽 | 拖时直跟手势位移，松手把速度传给 spring（`velocity` 可读），scale 随 pressProgress 联动 |
| `basic/OverlayDropdownMenu.kt` | 玻璃下拉菜单 | 原位生长 + 跟手拖选，替换了 miuix 的 OverlayDropdownPreference |
| `components/DocumentPageScaffold.kt` | 二级页外壳 | 见第 3 节 |
| `effects/background/BgEffect*.kt` | 壁纸模糊背景 | RenderEffect 单独判能力 |

---

## 9. 许可证边界（重要）

- 仓库根 `LICENSE` = **AGPL-3.0**。`ui-shared/`（所有页面、脚手架、自研玻璃控件）都受这个约束——**抄进闭源/非 AGPL 项目会有传染风险**。
- `miuix/` 文件头写的是 **Apache-2.0**（fork 自 miuix-ui），`backdrop/` fork 自 `io.github.kyant0:backdrop`（上游 Apache-2.0）。
- 实践建议：**直接依赖官方 artifact**（`top.yukonga.miuix.kmp:*`、`io.github.kyant0:backdrop`），不要抄他们的页面代码；API 用法本身不受版权保护。

---

## 10. 对 LinkGrab 的 4 条落地建议

1. **玻璃分三层，别让采样层包含自己。** LinkGrab 的 `ResultScreen` 底栏踩过"采样整个 NavHost 循环引用崩溃"，正是这个模型的反面：背景录一层（只含内容/壁纸），底栏/顶栏用 `drawBackdrop` 采它，浮层放在采样层之外。
2. **给录制层加 `recordKey`。** 现在若每帧重录，滚动/静止都在做全树 `recordLayer`。静态内容用 `recordKey` 覆盖所有影响背景的输入（图片、缩放、偏移、亮度），滚动中才用 `mustRecord` 在 draw 期判断。
3. **`effects` / `onDrawSurface` / `shape` 三个 lambda 必须 `remember`。** 内联写会每次重组重建 RenderEffect，玻璃区域无意义重绘——手机上就是"看着卡"。
4. **吸顶栏改用布局期读高度。** 现在若是组合期读滚动高度来算 padding，整页会逐帧重组；`collapsibleTopInset` 那种布局期 modifier 是正解。另外 `blur 0 + 无折射` 时直接 `background()` 短路，别白采样。

---

## 附：仓库里还有的参考物

- `docs/compose/spec/schedule-page-perf-f.md` —— 一份真实的性能修复报告（含"玻璃跳帧必须用 draw 阶段 mustRecord，不能把滚动值放组合期 recordKey"这类经验）。
- `ohos/`、`harmonyApp/`、`iosApp/` —— 同一套 UI 的鸿蒙/iOS 落地，看 KMP 分层怎么切。
- `androidApp/src/main/java/.../MainActivity.kt` —— 宿主入口、自研路由栈与外层转场。
