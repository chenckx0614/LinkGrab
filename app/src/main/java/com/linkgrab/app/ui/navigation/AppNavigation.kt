package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import com.linkgrab.app.ui.screens.AboutScreen
import com.linkgrab.app.ui.screens.BatchResultScreen
import com.linkgrab.app.ui.screens.BottomBarStyleScreen
import com.linkgrab.app.ui.screens.DonateScreen
import com.linkgrab.app.ui.screens.DownloadsScreen
import com.linkgrab.app.ui.screens.GameScreen
import com.linkgrab.app.ui.screens.GuideScreen
import com.linkgrab.app.ui.screens.HistoryScreen
import com.linkgrab.app.ui.screens.HomeScreen
import com.linkgrab.app.ui.screens.ImageConvertScreen
import com.linkgrab.app.ui.screens.MusicScreen
import com.linkgrab.app.ui.screens.ResultScreen
import com.linkgrab.app.ui.screens.SettingsScreen
import com.linkgrab.app.ui.screens.ToolboxScreen
import com.linkgrab.app.ui.screens.UpdateLogScreen
import com.linkgrab.app.ui.screens.UpdateSettingsScreen
import com.linkgrab.app.ui.liquid.HarmonyOS7BottomTabs
import com.linkgrab.app.ui.liquid.HarmonyOS7TabItem
import com.linkgrab.app.ui.liquid.IOS26UpdateDialog
import com.linkgrab.app.ui.liquid.HyperOS4BottomTabs
import com.linkgrab.app.ui.liquid.HyperOS4TabItem
import com.linkgrab.app.ui.liquid.Md3BottomTabs
import com.linkgrab.app.ui.liquid.Md3TabItem
import com.linkgrab.app.ui.liquid.NexioBottomTabs
import com.linkgrab.app.ui.liquid.NexioBottomTab
import com.linkgrab.app.ui.screens.VideoToAudioScreen
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.linkgrab.app.update.UpdateDownloadStatus
import com.linkgrab.app.update.UpdateResult
import com.linkgrab.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String) {
    data object Guide : Screen("guide", "引导")
    // 三个主标签页共用一个宿主：HorizontalPager 1:1 跟手滑动 + 弹簧吸附
    //（学习自 Nexio 课程表的主 tab 切换），不再各占一个导航目的地。
    data object MainTabs : Screen("main_tabs", "主页")
    data object Result : Screen("result", "解析结果")
    data object UpdateLog : Screen("update_log", "更新日志")
    data object UpdateSettings : Screen("update_settings", "更新设置")
    data object BottomBarStyle : Screen("bottom_bar_style", "底栏样式")
    data object Downloads : Screen("downloads", "下载管理")
    data object BatchResult : Screen("batch_result", "批量解析")
    data object About : Screen("about", "关于应用")
    data object Donate : Screen("donate", "捐赠支持")
    data object Game : Screen("game", "奶蛙消消乐")
    data object History : Screen("history", "历史记录")
    data object VideoToAudio : Screen("video_to_audio", "视频转音频")
    data object ImageConvert : Screen("image_convert", "图片转格式")
    data object Music : Screen("music", "音乐解析下载")
}

// MARK: - Page transitions (iOS 26 push / pop)

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * 转场窗口时长标记：v2.5.23 引入——转场期间摘掉全屏 layerBackdrop 录制，
 * 结束后恢复。
 */
private const val PushMs = 440
private const val PopMs = 400

/** iOS system deceleration curve — fast start, long smooth tail. */
private val iosEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * 页面自身路由：页面据此与「当前目的地」比对，判断自己是否被上层页面覆盖。
 * 被覆盖的页面套上模糊遮罩——iOS push 的核心语言：新页滑入，旧页原地
 * 渐糊渐暗，等新页盖上来。
 */
private val LocalMyRoute = staticCompositionLocalOf<String?> { null }

/**
 * 当前目的地路由（全局快照状态）。由 AppNavigation 里的
 * OnDestinationChangedListener 在导航提交的瞬间写入——与转场开始同步，
 * 被覆盖页由此立刻拿到 target=1 开始套模糊。
 */
private val currentRouteState = mutableStateOf<String?>(null)

/** 被覆盖页的模糊半径上限与暗化强度。 */
private val CoverBlurRadius = 18.dp
private const val CoverDimAlpha = 0.28f

/**
 * 引导页离场：淡出收尾，不做横向推移——引导页更像"一层"而不是"栈里的一页"。
 */
private val guideExit: ExitTransition = fadeOut(tween(220, easing = iosEasing))

/** 引导结束进入主界面：淡入 + 轻微放大，避免从右侧硬推进来。 */
private val guideFinishEnter: EnterTransition =
    fadeIn(tween(340, easing = iosEasing)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(340, easing = iosEasing))

/**
 * push / pop 转场（iOS 覆盖式，v2.5.36 重构、v2.5.37 加推进感）：
 * - push：新页从右滑入盖住旧页；旧页被推着左移约 1/3 屏宽，同时由
 *   [IOSPageSurface] 套上模糊遮罩（blur + 暗化随滑入同步加深）——
 *   位移给「推进」的层次感，模糊给「退后」的纵深感。
 * - pop：上层页带着模糊滑出右侧，下层页从让位处滑回原位、模糊同步恢复清晰。
 *
 * 模糊走 GPU RenderEffect（Modifier.blur，minSdk 31），半径动画只更新
 * 参数，代价可控；位移是布局层转场，转场窗口内采样层已摘除（见
 * transitioning），WebView 已延迟挂载（GameScreen），掉帧源已隔离。
 */
private val pushEnter: EnterTransition = slideInHorizontally(
    animationSpec = tween(PushMs, easing = iosEasing),
    initialOffsetX = { it },
)

private val popExit: ExitTransition = slideOutHorizontally(
    animationSpec = tween(PopMs, easing = iosEasing),
    targetOffsetX = { it },
)

/**
 * 底层页的「推进感」：push 时旧页被推着左移约 1/3 屏宽（iOS 系统转场的
 * 层次语言——上层盖上来、下层让位退后），pop 时再从让位处滑回原位。
 * 位移与 [IOSPageSurface] 的模糊遮罩叠加：边推边糊，层次感才成立。
 */
private const val ParallaxFraction = 0.3f

private val pushExit: ExitTransition = slideOutHorizontally(
    animationSpec = tween(PushMs, easing = iosEasing),
    targetOffsetX = { -(it * 3 / 10) },
)

private val popEnter: EnterTransition = slideInHorizontally(
    animationSpec = tween(PopMs, easing = iosEasing),
    initialOffsetX = { -(it * 3 / 10) },
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isFromGuide(): Boolean =
    initialState.destination.route == Screen.Guide.route

private fun iosEnter(transition: EnterTransition): EnterSpec =
    {
        when {
            isFromGuide() -> guideFinishEnter
            else -> transition
        }
    }

private fun iosExit(transition: ExitTransition): ExitSpec =
    {
        when {
            // 去引导页时首页直接让位，不要在后面横向滑走。
            targetState.destination.route == Screen.Guide.route -> ExitTransition.None
            else -> transition
        }
    }

/**
 * 每个普通页面的外壳：左边缘投影 + **覆盖式模糊遮罩**。
 *
 * 页面通过 [LocalMyRoute] 与 [currentRouteState] 判断自己是不是当前页：
 * - 是当前页 → 清晰（cover=0）；
 * - 不是（被上层页面盖住）→ 目标 cover=1，blur + 暗化随转场同步加深。
 *   位移（30% 视差）由转场参数负责，模糊由本外壳负责，两者叠加成
 *   iOS 式的「退后一层」观感。
 *
 * 用 rememberSaveable 记住上一次的覆盖状态：pop 回到本页时，页面重新
 * 进组合的初值是「曾被覆盖(1)」，随后目标变为 0 —— 于是它从模糊平滑
 * 恢复清晰，而不是瞬间硬切。push 时旧页的模糊动画在页面被移除前就已
 * 把 lastTarget 写成 1，下次回到它时初值正确。
 *
 * 阴影是常驻的（不做动画），开销只是一层 elevation 绘制。
 */
@Composable
private fun IOSPageSurface(content: @Composable () -> Unit) {
    val myRoute = LocalMyRoute.current
    val currentRoute = currentRouteState.value
    val target = if (currentRoute == null || myRoute == currentRoute) 0f else 1f
    var lastTarget by rememberSaveable { mutableFloatStateOf(target) }
    val cover = remember { Animatable(lastTarget) }
    LaunchedEffect(target) {
        lastTarget = target
        if (cover.value != target) {
            cover.animateTo(target, tween(PushMs, easing = iosEasing))
        }
    }
    val coverValue = cover.value

    Box(
        Modifier
            .fillMaxSize()
            .shadow(
                elevation = 16.dp,
                shape = RectangleShape,
                clip = false,
            )
            .blur(CoverBlurRadius * coverValue)
            .drawBehind {
                if (coverValue > 0.001f) {
                    drawRect(Color.Black.copy(alpha = CoverDimAlpha * coverValue))
                }
            }
    ) {
        content()
    }
}

/**
 * 统一的页面注册：所有普通页面共用同一套 push/pop 转场，
 * 并自动套上 [IOSPageSurface]。避免在每个 composable 里重复抄四行。
 */
private fun NavGraphBuilder.iosPage(
    route: String,
    content: @Composable () -> Unit,
) = composable(
    route = route,
    // iOS 覆盖式：新页从右滑入（push）/ 旧页带模糊滑出（pop），
    // 底层页被推着左移让位（30% 视差）+ 渐糊，层次感由位移与模糊叠加。
    enterTransition = iosEnter(pushEnter),
    exitTransition = iosExit(pushExit),
    popEnterTransition = iosEnter(popEnter),
    popExitTransition = iosExit(popExit),
) {
    CompositionLocalProvider(LocalMyRoute provides route) {
        IOSPageSurface { content() }
    }
}

// MARK: - 主标签页 HorizontalPager（页面切换动画学习自 Nexio 课程表）

/** 点底栏 tab 时的翻页弹簧（相邻页，略偏软）。 */
private val MainTabPagerAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
)

/** 跨页（首页↔设置，距离 2 页）更缓，避免长距离仍按同刚度显得急。 */
private val MainTabPagerCrossPageAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 240f,
)

/** 动画切到目标主 tab；正常结束后若未精确落页则强制吸附。 */
private suspend fun PagerState.animateMainTabTo(target: Int) {
    val distance = abs(target - currentPage)
    val spec = if (distance > 1) MainTabPagerCrossPageAnimSpec else MainTabPagerAnimSpec
    try {
        animateScrollToPage(target, animationSpec = spec)
    } catch (e: CancellationException) {
        throw e
    }
    if (currentPage != target || abs(currentPageOffsetFraction) > 0.001f) {
        scrollToPage(target)
    }
}

/**
 * 三个主标签页的 pager 宿主：页面随手势 1:1 横移，松手按弹簧 settle；
 * 点底栏 tab 走 [animateMainTabTo]。替换了旧的「fade + 小幅位移」导航转场。
 */
@Composable
private fun MainTabs(
    viewModel: MainViewModel,
    pagerState: PagerState,
    initialShareText: String?,
    onNavigateToHistory: () -> Unit,
    onNavigateToVideoToAudio: () -> Unit,
    onNavigateToImageConvert: () -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToUpdateSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGame: () -> Unit,
    onNavigateToBottomBarStyle: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
    // 注意：这里不要再套 IOSPageSurface。它的 16dp 投影是用来表达「层叠页面」
    // 上下关系的，而 pager 内三个页面是左右并排的兄弟关系——套上之后，
    // 后面那页的左边缘投影会在横滑时糊到前一页上。
    // 层级阴影由 NavGraphBuilder.iosPage 在整个 MainTabs 目的地上统一施加。
    // 页序与底栏一致：首页 / 我的 / 工具箱
    when (page) {
        0 -> HomeScreen(
            viewModel = viewModel,
            onNavigateToHistory = onNavigateToHistory,
            initialShareText = initialShareText,
        )
        1 -> SettingsScreen(
            viewModel = viewModel,
            onNavigateToUpdateSettings = onNavigateToUpdateSettings,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToBottomBarStyle = onNavigateToBottomBarStyle,
        )
        else -> ToolboxScreen(
            onNavigateToVideoToAudio = onNavigateToVideoToAudio,
            onNavigateToImageConvert = onNavigateToImageConvert,
            onNavigateToMusic = onNavigateToMusic,
            onNavigateToGame = onNavigateToGame,
        )
    }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    initialShareText: String? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val onMainTabs = currentDestination?.route == Screen.MainTabs.route

    // 底栏退场/回归（v2.5.38）：barCover 0(就位)→1(退场)。与页面转场同一
    // 时点（导航提交）开始、同曲线同时长——push 时跟着底层页被推走渐隐，
    // pop 时从让位处滑回。退场动画完成后才摘除组合，告别"突然消失"。
    var barMounted by remember { mutableStateOf(onMainTabs) }
    val barCover = remember { Animatable(0f) }
    LaunchedEffect(onMainTabs) {
        if (onMainTabs) {
            barMounted = true
            barCover.animateTo(0f, tween(PopMs, easing = iosEasing))
        } else if (barMounted) {
            barCover.animateTo(1f, tween(PushMs, easing = iosEasing))
            barMounted = false
        }
    }
    val screenWpx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val context = LocalContext.current
    // 主 tab pager：提升到这里是为了让底栏（AppNavigation 层）也能驱动翻页。
    val mainPagerState = rememberPagerState(initialPage = 0) { 3 }
    val pagerScope = rememberCoroutineScope()

    // 「解析成功跳结果页」是一次性事件，不能由持久状态推导。
    // 统一在这里监听，首页和历史页发起的解析都会走到这里。
    LaunchedEffect(Unit) {
        viewModel.navigateToResult.collect {
            navController.navigate(Screen.Result.route) {
                launchSingleTop = true
                restoreState = false
            }
        }
    }
    // 批量解析（多条链接）→ 批量结果页
    LaunchedEffect(Unit) {
        viewModel.navigateToBatch.collect {
            navController.navigate(Screen.BatchResult.route) {
                launchSingleTop = true
            }
        }
    }

    // 预测式返回已关闭，系统返回手势会走 onBackPressed。这里接管它，
    // 让手势返回执行应用内的 pop 转场（而不是直接退出应用）。
    // 只有详情页才拦截；主标签页上保留"返回退出"的系统行为。
    BackHandler(
        enabled = currentDestination?.route != null &&
            currentDestination?.route != Screen.MainTabs.route &&
            currentDestination?.route != Screen.Guide.route,
    ) {
        navController.popBackStack()
    }
    val dataLoaded by viewModel.dataLoaded.collectAsState()
    // 是否正等着跳引导页：设置读出来的那一刻决定，跳到引导页后即清除。
    var pendingGuide by remember { mutableStateOf(false) }
    // 从引导页过来时，底栏的出发位置（就是引导页里那根预览条的位置）。
    var barEntryFrom by remember { mutableStateOf<Rect?>(null) }
    val liquidBackdrop = rememberLayerBackdrop {
        drawRect(Color.White.copy(alpha = 0.08f))
        drawContent()
    }

    // 转场期间暂停录制底栏的采样层。
    // 整个 NavHost 都被录进 liquidBackdrop（底栏玻璃要采样它做模糊），而转场时
    // 页面每帧都在位移 —— 于是这块**全屏**采样层每帧重录一次，底栏还要再对它做
    // 一次模糊采样，两笔全屏开销叠在一帧里，转场必掉帧。
    // 这里在页面切换期间摘掉录制：层里保留上一帧稳定态的画面，底栏玻璃继续显示
    // （略微滞后于滑动的页面，但那本来就是一团模糊），转场结束再恢复录制。
    var transitioning by remember { mutableStateOf(false) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentRouteState.value = destination.route // 覆盖式模糊遮罩依赖这个时点
            transitioning = true
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(transitioning) {
        if (transitioning) {
            // 取最长的转场时长兜底，宁可多录一帧也别在动画中途反复开关
            delay(PushMs.toLong() + 40)
            transitioning = false
        }
    }

    LaunchedEffect(dataLoaded) {
        if (dataLoaded && !viewModel.guideShown.value) {
            pendingGuide = true
            navController.navigate(Screen.Guide.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }

    // 设置还没读出来、或正在去引导页的路上，用一层与应用背景同色的遮挡盖住
    // NavHost。这样首页一帧都不会露出来（以前就是这里闪一下）。
    val coveringSplash = !dataLoaded ||
        (pendingGuide && currentDestination?.route != Screen.Guide.route)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {},
    ) { _ ->
        // An opaque base layer: during a transition both pages are partially
        // off-screen, and without it the window background (white) shows
        // through as a flash between pages.
        Box(Modifier.fillMaxSize().background(iosBackground())) {
            NavHost(
                navController = navController,
                startDestination = Screen.MainTabs.route,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (transitioning) Modifier else Modifier.layerBackdrop(liquidBackdrop)),
            ) {

            composable(
                Screen.Guide.route,
                // 进场直接替换（不做淡入）：这一跳被遮挡层盖着，不能让首页
                // 在后面透出来；离场才用淡出，给"进入主界面"一个柔和收尾。
                enterTransition = { EnterTransition.None },
                exitTransition = { guideExit },
            ) {
                GuideScreen(
                    viewModel = viewModel,
                    onFinished = { previewRect ->
                        viewModel.markGuideShown()
                        pendingGuide = false
                        // 记下预览条的位置，底栏会从那里"落"到屏幕底部。
                        barEntryFrom = previewRect
                        navController.navigate(Screen.MainTabs.route) {
                            popUpTo(Screen.Guide.route) { inclusive = true }
                        }
                    }
                )
            }

            // 三个主标签页共用一个 pager 宿主：随手势 1:1 滑动 + 弹簧吸附；
            // 打开 / 离开详情页仍走 iOS 视差 push/pop（见 iosPage）。
            iosPage(Screen.MainTabs.route) {
                MainTabs(
                    viewModel = viewModel,
                    pagerState = mainPagerState,
                    initialShareText = initialShareText,
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onNavigateToVideoToAudio = {
                        navController.navigate(Screen.VideoToAudio.route)
                    },
                    onNavigateToImageConvert = {
                        navController.navigate(Screen.ImageConvert.route)
                    },
                    onNavigateToMusic = {
                        navController.navigate(Screen.Music.route)
                    },
                    onNavigateToGame = {
                        navController.navigate(Screen.Game.route)
                    },
                    onNavigateToUpdateSettings = {
                        navController.navigate(Screen.UpdateSettings.route)
                    },
                    onNavigateToDownloads = {
                        navController.navigate(Screen.Downloads.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToBottomBarStyle = {
                        navController.navigate(Screen.BottomBarStyle.route)
                    },
                )
            }

            // Detail pages — transitions come from the shared specs above.
            iosPage(Screen.Result.route) {
                ResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.UpdateLog.route) {
                UpdateLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.UpdateSettings.route) {
                UpdateSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.BottomBarStyle.route) {
                BottomBarStyleScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.Downloads.route) {
                DownloadsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.BatchResult.route) {
                BatchResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.About.route) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToUpdateLog = { navController.navigate(Screen.UpdateLog.route) },
                    onNavigateToDonate = { navController.navigate(Screen.Donate.route) },
                )
            }
            iosPage(Screen.Donate.route) {
                DonateScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.Game.route) {
                GameScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            iosPage(Screen.VideoToAudio.route) {
                VideoToAudioScreen(onBack = { navController.popBackStack() })
            }
            iosPage(Screen.ImageConvert.route) {
                ImageConvertScreen(onBack = { navController.popBackStack() })
            }
            iosPage(Screen.Music.route) {
                MusicScreen(onBack = { navController.popBackStack() })
            }
            iosPage(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onParseUrl = { url ->
                        viewModel.parseUrl(url)
                        navController.popBackStack()
                    },
                )
            }
        }
        if (barMounted) {
            // 底栏退场/回归动画（v2.5.38）：push 时底栏不再瞬间消失，而是
            // 跟底层页一起被推走（左移 30% 屏宽）并渐隐——与页面转场同一
            // 时点、同一曲线；pop 回主界面时从让位处滑回原位。退场动画
            // 完成后才真正移除组合（此时新页已盖满全屏，移除无感）。
            // 底栏"落位"动画：从引导页里那根预览条的位置，缓降到屏幕底部。
            // 外层 Box 不做任何位移，用来测它就位后的真实位置。
            var barHomeRect by remember { mutableStateOf<Rect?>(null) }
            val barShiftX = remember { Animatable(0f) }
            val barShiftY = remember { Animatable(0f) }
            val barScale = remember { Animatable(1f) }
            // 有入场来源时先透明，等拿到目标位置再开始，避免闪一帧。
            val barAlpha = remember { Animatable(if (barEntryFrom != null) 0f else 1f) }

            LaunchedEffect(barEntryFrom) {
                val from = barEntryFrom ?: return@LaunchedEffect
                // 等底栏完成首次布局，才能算位移量。
                val home = snapshotFlow { barHomeRect }.filterNotNull().first()
                barShiftX.snapTo(from.center.x - home.center.x)
                barShiftY.snapTo(from.center.y - home.center.y)
                barScale.snapTo((from.width / home.width).coerceIn(0.55f, 1.1f))
                barAlpha.snapTo(0.25f)
                launch { barAlpha.animateTo(1f, tween(260, easing = iosEasing)) }
                launch { barScale.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 190f)) }
                launch { barShiftX.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 190f)) }
                // 竖直方向更慢更软：像被"放下去"一样沉到底部，带一点点回落。
                barShiftY.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 115f))
                barEntryFrom = null
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        barHomeRect = Rect(
                            coords.positionInRoot(),
                            Size(coords.size.width.toFloat(), coords.size.height.toFloat()),
                        )
                    }
            ) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                barShiftX.value.roundToInt(),
                                barShiftY.value.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            translationX = -barCover.value * 0.3f * screenWpx
                            scaleX = barScale.value
                            scaleY = barScale.value
                            alpha = barAlpha.value * (1f - barCover.value)
                        }
                ) {
                // 选中项由 pager 当前页决定（手指拖 pager 时也会同步到这一帧）。
                val selectedIndex = mainPagerState.currentPage
                val onTabSelected: (Int) -> Unit = { index ->
                    pagerScope.launch { mainPagerState.animateMainTabTo(index) }
                }
                val bottomModifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding()

                when (bottomBarStyle) {
                    1 -> HyperOS4BottomTabs(
                        selectedTabIndex = { selectedIndex },
                        onTabSelected = onTabSelected,
                        backdrop = liquidBackdrop,
                        tabsCount = 3,
                        // 参考图（HyperOS 文件管理器）：胶囊比通栏窄一截，更小巧
                        modifier = bottomModifier.fillMaxWidth(0.86f),
                    ) { selectIndex, reportPress ->
                        HyperOS4TabItem(
                            Icons.Default.Home,
                            "首页",
                            selected = selectedIndex == 0,
                            onPress = { reportPress(0, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(0) }
                        HyperOS4TabItem(
                            Icons.Default.Person,
                            "我的",
                            selected = selectedIndex == 1,
                            onPress = { reportPress(1, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(1) }
                        HyperOS4TabItem(
                            Icons.Default.Build,
                            "工具箱",
                            selected = selectedIndex == 2,
                            onPress = { reportPress(2, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(2) }
                    }
                    2 -> HarmonyOS7BottomTabs(
                        selectedTabIndex = { selectedIndex },
                        onTabSelected = onTabSelected,
                        backdrop = liquidBackdrop,
                        tabsCount = 3,
                        modifier = bottomModifier,
                    ) { selectIndex, reportPress ->
                        HarmonyOS7TabItem(
                            Icons.Default.Home,
                            "首页",
                            selected = selectedIndex == 0,
                            onPress = { reportPress(0, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(0) }
                        HarmonyOS7TabItem(
                            Icons.Default.Person,
                            "我的",
                            selected = selectedIndex == 1,
                            onPress = { reportPress(1, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(1) }
                        HarmonyOS7TabItem(
                            Icons.Default.Build,
                            "工具箱",
                            selected = selectedIndex == 2,
                            onPress = { reportPress(2, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(2) }
                    }
                    3 -> Md3BottomTabs(
                        selectedTabIndex = { selectedIndex },
                        onTabSelected = onTabSelected,
                        backdrop = liquidBackdrop,
                        tabsCount = 3,
                        // MD3 规范：贴边通栏实底，不走悬浮胶囊的外边距
                        modifier = Modifier.navigationBarsPadding(),
                    ) { selectIndex ->
                        Md3TabItem(
                            Icons.Default.Home,
                            "首页",
                            selected = selectedIndex == 0,
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(0) }
                        Md3TabItem(
                            Icons.Default.Person,
                            "我的",
                            selected = selectedIndex == 1,
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(1) }
                        Md3TabItem(
                            Icons.Default.Build,
                            "工具箱",
                            selected = selectedIndex == 2,
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(2) }
                    }
                    else -> {
                        // 默认（原 iOS27 的位置）：Nexio 液态玻璃胶囊。
                        NexioBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = onTabSelected,
                            backdrop = liquidBackdrop,
                            tabsCount = 3,
                            // Nexio 风格：较窄的悬浮胶囊（参照其 0.63f 宽度）
                            modifier = bottomModifier.fillMaxWidth(0.66f),
                        ) {
                            NexioBottomTab(Icons.Default.Home, "首页", Modifier.weight(1f))
                            NexioBottomTab(Icons.Default.Person, "我的", Modifier.weight(1f))
                            NexioBottomTab(Icons.Default.Build, "工具箱", Modifier.weight(1f))
                        }
                    }
                }
                } // 落位动画层
            }
            // 遮挡层（见 coveringSplash）：铺在 NavHost 之上，盖住还没轮到出场的内容。
            if (coveringSplash) {
                Box(Modifier.fillMaxSize().background(iosBackground()))
            }
        }
    }
    }

    val updateResult by viewModel.updateResult.collectAsState()
    val updateDownloadStatus by viewModel.updateDownloadStatus.collectAsState()
    val manualUpdateCheck by viewModel.manualUpdateCheck.collectAsState()
    val updateReminderEnabled by viewModel.updateReminder.collectAsState()
    updateResult?.let { result ->
        // 弹窗时机：手动检查始终弹；自动检查发现新版本时只在「更新提醒」开着时弹。
        // 关掉提醒后结果仍会写进更新设置页的面板里，只是不主动打扰。
        if (result is UpdateResult.UpdateAvailable && (manualUpdateCheck || updateReminderEnabled)) {
            // 更新弹窗：样式与信息架构移植自 Nexio 课程表的 UpdateDialog，
            // 用本项目的 iOS 26 玻璃外壳渲染，取代原来的 Material AlertDialog。
            IOS26UpdateDialog(
                currentVersion = result.currentVersion,
                latestVersion = result.latestVersion,
                releaseNotes = result.releaseNotes,
                downloadStatus = updateDownloadStatus,
                hasDirectDownload = result.apkDownloadUrl.isNotBlank(),
                onPrimaryAction = {
                    when (updateDownloadStatus) {
                        is UpdateDownloadStatus.Idle -> {
                            if (result.apkDownloadUrl.isNotBlank()) {
                                viewModel.downloadUpdate(result.apkDownloadUrl, result.latestVersion)
                            } else {
                                // 没有直链时退回浏览器：这属于「离开弹窗」，顺带收起
                                viewModel.openUpdateInBrowser(result.htmlUrl)
                                viewModel.dismissUpdate()
                            }
                        }
                        is UpdateDownloadStatus.Downloaded -> {
                            val uri = (updateDownloadStatus as UpdateDownloadStatus.Downloaded).uri
                            viewModel.installUpdate(uri)
                        }
                        is UpdateDownloadStatus.Error -> {
                            viewModel.resetUpdateDownload()
                            viewModel.downloadUpdate(result.apkDownloadUrl, result.latestVersion)
                        }
                        else -> Unit
                    }
                },
                onDismiss = {
                    // 与 Nexio 一致：下载中关闭只是收起弹窗，下载继续（后台下载）。
                    // 其余状态关闭时顺带清掉下载进度，避免下次打开残留中间态。
                    viewModel.dismissUpdate()
                    if (updateDownloadStatus !is UpdateDownloadStatus.Downloading) {
                        viewModel.resetUpdateDownload()
                    }
                },
            )
        }
    }
}
