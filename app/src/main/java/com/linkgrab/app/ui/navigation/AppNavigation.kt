package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.linkgrab.app.ui.liquid.LiquidBottomTabs
import com.linkgrab.app.ui.screens.VideoToAudioScreen
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
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
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

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    initialShareText: String? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavRoutes
    val context = LocalContext.current
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {},
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidBackdrop),
            ) {

            composable(Screen.Guide.route) {
                GuideScreen(
                    onFinished = {
                        viewModel.markGuideShown()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Guide.route) { inclusive = true }
                        }
                    }
                )
            }

            // Bottom nav tabs — quick crossfade for instant response
            composable(
                Screen.Home.route,
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(140)) },
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToResult = {
                        navController.navigate(Screen.Result.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    initialShareText = initialShareText,
                )
            }
            composable(
                Screen.Toolbox.route,
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(140)) },
            ) {
                ToolboxScreen(
                    onNavigateToVideoToAudio = { navController.navigate(Screen.VideoToAudio.route) },
                    onNavigateToImageConvert = { navController.navigate(Screen.ImageConvert.route) },
                )
            }

            composable(
                Screen.Settings.route,
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(140)) },
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToUpdateLog = {
                        navController.navigate(Screen.UpdateLog.route)
                    },
                )
            }

            // Detail pages — iOS 26 push/pop: new page slides in from right with
            // deceleration curve, old page parallax-shifts left at 1/4 speed.
            val iosEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
            val slideIn = slideInHorizontally(
                animationSpec = tween(320, easing = iosEasing),
                initialOffsetX = { it },
            )
            val slideOut = slideOutHorizontally(
                animationSpec = tween(320, easing = iosEasing),
                targetOffsetX = { -it / 4 },
            )
            val popSlideIn = slideInHorizontally(
                animationSpec = tween(320, easing = iosEasing),
                initialOffsetX = { -it / 4 },
            )
            val popSlideOut = slideOutHorizontally(
                animationSpec = tween(320, easing = iosEasing),
                targetOffsetX = { it },
            )

            composable(
                Screen.Result.route,
                enterTransition = { slideIn },
                exitTransition = { slideOut },
                popEnterTransition = { popSlideIn },
                popExitTransition = { popSlideOut },
            ) {
                ResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.UpdateLog.route,
                enterTransition = { slideIn },
                exitTransition = { slideOut },
                popEnterTransition = { popSlideIn },
                popExitTransition = { popSlideOut },
            ) {
                UpdateLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.VideoToAudio.route,
                enterTransition = { slideIn },
                exitTransition = { slideOut },
                popEnterTransition = { popSlideIn },
                popExitTransition = { popSlideOut },
            ) {
                VideoToAudioScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.ImageConvert.route,
                enterTransition = { slideIn },
                exitTransition = { slideOut },
                popEnterTransition = { popSlideIn },
                popExitTransition = { popSlideOut },
            ) {
                ImageConvertScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.History.route,
                enterTransition = { slideIn },
                exitTransition = { slideOut },
                popEnterTransition = { popSlideIn },
                popExitTransition = { popSlideOut },
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
                LiquidBottomTabs(
                    selectedTabIndex = {
                        when (currentDestination?.route) {
                            Screen.Toolbox.route -> 1
                            Screen.Settings.route -> 2
                            else -> 0
                        }
                    },
                    onTabSelected = { index ->
                        val route = when (index) {
                            1 -> Screen.Toolbox.route
                            2 -> Screen.Settings.route
                            else -> Screen.Home.route
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    backdrop = liquidBackdrop,
                    tabsCount = 3,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                        .navigationBarsPadding(),
                ) {
                    LiquidTabItem(Icons.Default.Home, "首页", Modifier.weight(1f)) { navController.navigate(Screen.Home.route) { launchSingleTop = true } }
                    LiquidTabItem(Icons.Default.Build, "工具箱", Modifier.weight(1f)) { navController.navigate(Screen.Toolbox.route) { launchSingleTop = true } }
                    LiquidTabItem(Icons.Default.Settings, "设置", Modifier.weight(1f)) { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
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
