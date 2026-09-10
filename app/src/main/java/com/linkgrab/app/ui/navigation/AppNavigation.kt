package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import com.linkgrab.app.ui.screens.GuideScreen
import com.linkgrab.app.ui.screens.HistoryScreen
import com.linkgrab.app.ui.screens.HomeScreen
import com.linkgrab.app.ui.screens.ImageConvertScreen
import com.linkgrab.app.ui.screens.ResultScreen
import com.linkgrab.app.ui.screens.SettingsScreen
import com.linkgrab.app.ui.screens.ToolboxScreen
import com.linkgrab.app.ui.screens.UpdateLogScreen
import com.linkgrab.app.ui.liquid.HarmonyOS7BottomTabs
import com.linkgrab.app.ui.liquid.HarmonyOS7TabItem
import com.linkgrab.app.ui.liquid.HyperOS4BottomTabs
import com.linkgrab.app.ui.liquid.HyperOS4TabItem
import com.linkgrab.app.ui.liquid.LiquidBottomTabs
import com.linkgrab.app.ui.screens.VideoToAudioScreen
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.linkgrab.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String) {
    data object Guide : Screen("guide", "引导")
    data object Home : Screen("home", "首页")
    data object Toolbox : Screen("toolbox", "工具箱")
    data object Result : Screen("result", "解析结果")
    data object Settings : Screen("settings", "设置")
    data object UpdateLog : Screen("update_log", "更新日志")
    data object History : Screen("history", "历史记录")
    data object VideoToAudio : Screen("video_to_audio", "视频转音频")
    data object ImageConvert : Screen("image_convert", "图片转格式")
}

/**
 * A single tab inside [LiquidBottomTabs]. The tint is uniform here — the
 * selected state is rendered by the bar itself: an invisible accent-tinted
 * duplicate of this content is recorded into a backdrop layer and revealed
 * through the selection capsule's refraction.
 */
@Composable
private fun LiquidTabItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            // 长按也当作点击：iOS 27 的拖拽手势只在胶囊上，长按未选中区域时
            // 不会收到 onClick，走 long click 兜底切换。
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iosLabel(),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = iosLabel(),
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = (-0.1).sp,
        )
    }
}

private val bottomNavRoutes = setOf(Screen.Home.route, Screen.Toolbox.route, Screen.Settings.route)

// MARK: - Page transitions (iOS 26 push / pop)

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

private const val TransitionMs = 340

/** iOS system deceleration curve — fast start, long smooth tail. */
private val iosEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Tab-to-tab switches use a quick crossfade instead of a slide. */
private val tabFadeIn: EnterTransition = fadeIn(tween(150))
private val tabFadeOut: ExitTransition = fadeOut(tween(130))

/** 30% parallax offset for the page that stays behind. */
private const val Parallax = 0.30f

/**
 * 引导页离场：淡出收尾，不做横向推移——引导页更像"一层"而不是"栈里的一页"。
 */
private val guideExit: ExitTransition = fadeOut(tween(220, easing = iosEasing))

/** 引导结束进入主界面：淡入 + 轻微放大，避免从右侧硬推进来。 */
private val guideFinishEnter: EnterTransition =
    fadeIn(tween(340, easing = iosEasing)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(340, easing = iosEasing))

private val pushEnter: EnterTransition = slideInHorizontally(
    animationSpec = tween(TransitionMs, easing = iosEasing),
    initialOffsetX = { it },
)

private val pushExit: ExitTransition = slideOutHorizontally(
    animationSpec = tween(TransitionMs, easing = iosEasing),
    targetOffsetX = { (-it * Parallax).toInt() },
)

private val popEnter: EnterTransition = slideInHorizontally(
    animationSpec = tween(TransitionMs, easing = iosEasing),
    initialOffsetX = { (-it * Parallax).toInt() },
)

private val popExit: ExitTransition = slideOutHorizontally(
    animationSpec = tween(TransitionMs, easing = iosEasing),
    targetOffsetX = { it },
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in bottomNavRoutes &&
        targetState.destination.route in bottomNavRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isFromGuide(): Boolean =
    initialState.destination.route == Screen.Guide.route

private fun iosEnter(transition: EnterTransition): EnterSpec =
    {
        when {
            isTabSwitch() -> tabFadeIn
            isFromGuide() -> guideFinishEnter
            else -> transition
        }
    }

private fun iosExit(transition: ExitTransition): ExitSpec =
    {
        when {
            isTabSwitch() -> tabFadeOut
            // 去引导页时首页直接让位，不要在后面横向滑走。
            targetState.destination.route == Screen.Guide.route -> ExitTransition.None
            else -> transition
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

    val showBottomBar = currentDestination?.route in bottomNavRoutes
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val context = LocalContext.current

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

    // 预测式返回已关闭，系统返回手势会走 onBackPressed。这里接管它，
    // 让手势返回执行应用内的 pop 转场（而不是直接退出应用）。
    // 只有非底部标签页才拦截，首页上仍然保留"返回退出"的系统行为。
    BackHandler(
        enabled = currentDestination?.route != null &&
            currentDestination?.route !in bottomNavRoutes &&
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

    // Bottom-tab navigation: keep a single instance per tab and restore its
    // state. Without popUpTo/saveState, repeated tab taps keep pushing new
    // destinations onto the back stack, which interrupted transitions and
    // could leave a stale half-faded (white) page on the screen.
    val navigateToTab: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidBackdrop),
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
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Guide.route) { inclusive = true }
                        }
                    }
                )
            }

            // Bottom nav tabs — quick crossfade when switching between tabs,
            // iOS parallax push/pop when opening or leaving a detail page.
            composable(
                Screen.Home.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    initialShareText = initialShareText,
                )
            }
            composable(
                Screen.Toolbox.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                ToolboxScreen(
                    onNavigateToVideoToAudio = { navController.navigate(Screen.VideoToAudio.route) },
                    onNavigateToImageConvert = { navController.navigate(Screen.ImageConvert.route) },
                )
            }

            composable(
                Screen.Settings.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToUpdateLog = {
                        navController.navigate(Screen.UpdateLog.route)
                    },
                )
            }

            // Detail pages — transitions come from the shared specs above.
            composable(
                Screen.Result.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                ResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.UpdateLog.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                UpdateLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.VideoToAudio.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                VideoToAudioScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.ImageConvert.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
                ImageConvertScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.History.route,
                enterTransition = iosEnter(pushEnter),
                exitTransition = iosExit(pushExit),
                popEnterTransition = iosEnter(popEnter),
                popExitTransition = iosExit(popExit),
            ) {
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
        if (showBottomBar) {
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
                            scaleX = barScale.value
                            scaleY = barScale.value
                            alpha = barAlpha.value
                        }
                ) {
                val selectedIndex = when (currentDestination?.route) {
                    Screen.Toolbox.route -> 1
                    Screen.Settings.route -> 2
                    else -> 0
                }
                val onTabSelected: (Int) -> Unit = { index ->
                    val route = when (index) {
                        1 -> Screen.Toolbox.route
                        2 -> Screen.Settings.route
                        else -> Screen.Home.route
                    }
                    navigateToTab(route)
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
                        modifier = bottomModifier,
                    ) { selectIndex, reportPress ->
                        HyperOS4TabItem(
                            Icons.Default.Home,
                            "首页",
                            selected = selectedIndex == 0,
                            onPress = { reportPress(0, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(0) }
                        HyperOS4TabItem(
                            Icons.Default.Build,
                            "工具箱",
                            selected = selectedIndex == 1,
                            onPress = { reportPress(1, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(1) }
                        HyperOS4TabItem(
                            Icons.Default.Settings,
                            "设置",
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
                            Icons.Default.Build,
                            "工具箱",
                            selected = selectedIndex == 1,
                            onPress = { reportPress(1, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(1) }
                        HarmonyOS7TabItem(
                            Icons.Default.Settings,
                            "设置",
                            selected = selectedIndex == 2,
                            onPress = { reportPress(2, it) },
                            modifier = Modifier.weight(1f),
                        ) { selectIndex(2) }
                    }
                    else -> {
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = onTabSelected,
                            backdrop = liquidBackdrop,
                            tabsCount = 3,
                            modifier = bottomModifier,
                        ) { selectIndex ->
                            LiquidTabItem(Icons.Default.Home, "首页", Modifier.weight(1f)) { selectIndex(0) }
                            LiquidTabItem(Icons.Default.Build, "工具箱", Modifier.weight(1f)) { selectIndex(1) }
                            LiquidTabItem(Icons.Default.Settings, "设置", Modifier.weight(1f)) { selectIndex(2) }
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
    updateResult?.let { result ->
        if (result is com.linkgrab.app.update.UpdateResult.UpdateAvailable) {
            var showDialog by remember { mutableStateOf(true) }
            if (showDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showDialog = false
                        viewModel.dismissUpdate()
                    },
                    title = { Text("发现新版本") },
                    text = {
                        Column {
                            Text("v${result.currentVersion} → v${result.latestVersion}")
                            if (result.releaseNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(result.releaseNotes)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl)))
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                            }
                            showDialog = false
                            viewModel.dismissUpdate()
                        }) { Text("去更新") }
                    },
                    dismissButton = {
                        Button(onClick = {
                            showDialog = false
                            viewModel.dismissUpdate()
                        }) { Text("稍后") }
                    },
                )
            }
        }
    }
}
