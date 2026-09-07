package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
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
import com.linkgrab.app.ui.screens.VideoToAudioScreen
import com.linkgrab.app.ui.theme.NexioPurple
import com.linkgrab.app.ui.theme.NexioPurpleLight
import com.linkgrab.app.ui.theme.nexioInk
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

/** Custom Apple-style floating tab item. The route logic stays in the caller. */
@Composable
private fun NavigationBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val visibleLabel = when (icon) {
        Icons.Default.Home -> "首页"
        Icons.Default.Build -> "工具箱"
        Icons.Default.Settings -> "设置"
        else -> label
    }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 720f),
        label = "tabPressScale",
    )
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) NexioPurple else nexioInk().copy(alpha = 0.62f),
        animationSpec = tween(180),
        label = "tabTint",
    )
    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(34.dp))
            .background(Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = visibleLabel, tint = tint, modifier = Modifier.size(28.dp))
        androidx.compose.material3.Text(visibleLabel, color = tint, fontSize = 13.sp, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

// Bottom nav routes - no animation for these
private val bottomNavRoutes = setOf(Screen.Home.route, Screen.Toolbox.route, Screen.Settings.route)

@Composable
private fun LiquidGlassBottomBar(
    selectedIndex: Int,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    interaction: MutableInteractionSource,
    content: @Composable RowScope.() -> Unit,
) {
    val barPressed by interaction.collectIsPressedAsState()
    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(92.dp)
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(48.dp) },
                effects = {
                    vibrancy()
                    blur(13.dp.toPx())
                    lens(28.dp.toPx(), 28.dp.toPx())
                },
                layerBlock = {
                    val scale = if (barPressed) 0.965f else 1f
                    scaleX = scale
                    scaleY = scale
                },
                highlight = { com.kyant.backdrop.highlight.Highlight.Default },
                shadow = { Shadow(radius = 20.dp, alpha = 0.28f) },
                innerShadow = { InnerShadow(radius = 9.dp, alpha = 0.22f) },
                    onDrawSurface = {
                        drawRect(Color.Transparent)
                        drawRect(Color.White.copy(alpha = 0.035f))
                    },
            )
            .padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        val itemWidth = (maxWidth - 10.dp) / 3f
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
            label = "liquidTabIndicatorOffset",
        )
        Box(
            modifier = Modifier
                .width(itemWidth)
                .fillMaxHeight()
                .offset(x = indicatorOffset)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(43.dp) },
                    effects = {
                        vibrancy()
                        blur(9.dp.toPx())
                        lens(20.dp.toPx(), 26.dp.toPx(), chromaticAberration = true)
                    },
                    shadow = { Shadow(radius = 10.dp, alpha = 0.22f) },
                    innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.24f) },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.24f))
                        drawRect(NexioPurpleLight.copy(alpha = 0.18f))
                    },
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
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
    val context = LocalContext.current
    val guideShown by viewModel.guideShown.collectAsState()
    val dataLoaded by viewModel.dataLoaded.collectAsState()
    var guideChecked by remember { mutableStateOf(false) }
    val liquidBackdrop = rememberLayerBackdrop {
        drawRect(Color.White.copy(alpha = 0.10f))
        drawContent()
    }
    val tabInteraction = remember { MutableInteractionSource() }

    // Only check after DataStore has loaded
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

            // Bottom nav tabs - no animation
            composable(
                Screen.Home.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
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
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                ToolboxScreen(
                    onNavigateToVideoToAudio = { navController.navigate(Screen.VideoToAudio.route) },
                    onNavigateToImageConvert = { navController.navigate(Screen.ImageConvert.route) },
                )
            }

            composable(
                Screen.Settings.route,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToUpdateLog = {
                        navController.navigate(Screen.UpdateLog.route)
                    },
                )
            }

            // Other pages - slide animation
            composable(
                Screen.Result.route,
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                ResultScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.UpdateLog.route,
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                UpdateLogScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.VideoToAudio.route,
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                VideoToAudioScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.ImageConvert.route,
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
            ) {
                ImageConvertScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Screen.History.route,
                enterTransition = { slideInHorizontally(tween(300)) { it } },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } },
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
                LiquidGlassBottomBar(
                    selectedIndex = when (currentDestination?.route) {
                        Screen.Toolbox.route -> 1
                        Screen.Settings.route -> 2
                        else -> 0
                    },
                    backdrop = liquidBackdrop,
                    interaction = tabInteraction,
                ) {
                    NavigationBarItem(Icons.Default.Home, "首页", currentDestination?.route == Screen.Home.route, tabInteraction) { navController.navigate(Screen.Home.route) { launchSingleTop = true } }
                    NavigationBarItem(Icons.Default.Build, "工具箱", currentDestination?.route == Screen.Toolbox.route, tabInteraction) { navController.navigate(Screen.Toolbox.route) { launchSingleTop = true } }
                    NavigationBarItem(Icons.Default.Settings, "设置", currentDestination?.route == Screen.Settings.route, tabInteraction) { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
                }
            }
        }
    }
    }

    // 启动时更新弹窗（全局，不依赖设置页）
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
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result.downloadUrl)))
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
