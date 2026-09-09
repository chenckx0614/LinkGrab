package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
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
            .clickable(onClick = onClick),
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

private fun iosEnter(transition: EnterTransition): EnterSpec =
    { if (isTabSwitch()) tabFadeIn else transition }

private fun iosExit(transition: ExitTransition): ExitSpec =
    { if (isTabSwitch()) tabFadeOut else transition }

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
    val guideShown by viewModel.guideShown.collectAsState()
    val dataLoaded by viewModel.dataLoaded.collectAsState()
    var guideChecked by remember { mutableStateOf(false) }
    val liquidBackdrop = rememberLayerBackdrop {
        drawRect(Color.White.copy(alpha = 0.08f))
        drawContent()
    }

    LaunchedEffect(dataLoaded) {
        if (dataLoaded && !guideChecked) {
            guideChecked = true
            if (!guideShown) {
                navController.navigate(Screen.Guide.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                }
            }
        }
    }

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

            composable(Screen.Guide.route) {
                GuideScreen(
                    viewModel = viewModel,
                    onFinished = {
                        viewModel.markGuideShown()
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
            Box(Modifier.align(Alignment.BottomCenter)) {
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
