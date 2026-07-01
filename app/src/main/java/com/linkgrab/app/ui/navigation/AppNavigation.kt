package com.linkgrab.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.linkgrab.app.ui.screens.GuideScreen
import com.linkgrab.app.ui.screens.HistoryScreen
import com.linkgrab.app.ui.screens.HomeScreen
import com.linkgrab.app.ui.screens.ResultScreen
import com.linkgrab.app.ui.screens.SettingsScreen
import com.linkgrab.app.ui.screens.ToolboxScreen
import com.linkgrab.app.ui.screens.UpdateLogScreen
import com.linkgrab.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String) {
    data object Guide : Screen("guide", "引导")
    data object Home : Screen("home", "首页")
    data object Toolbox : Screen("toolbox", "工具箱")
    data object Result : Screen("result", "解析结果")
    data object Settings : Screen("settings", "设置")
    data object UpdateLog : Screen("update_log", "更新日志")
    data object History : Screen("history", "历史记录")
}

// Bottom nav routes - no animation for these
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
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(defaultWindowInsetsPadding = false, showDivider = false) {
                    NavigationBarItem(
                        icon = MiuixIcons.Image,
                        label = "首页",
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Screen.Home.route
                        } == true,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = MiuixIcons.GridView,
                        label = "工具箱",
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Screen.Toolbox.route
                        } == true,
                        onClick = {
                            navController.navigate(Screen.Toolbox.route) {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        },
                    )
                    NavigationBarItem(
                        icon = MiuixIcons.Settings,
                        label = "设置",
                        selected = currentDestination?.hierarchy?.any {
                            it.route == Screen.Settings.route
                        } == true,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(Screen.Home.route)
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
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
                enterTransition = { fadeIn(tween(0)) },
                exitTransition = { fadeOut(tween(0)) },
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
                enterTransition = { fadeIn(tween(0)) },
                exitTransition = { fadeOut(tween(0)) },
            ) {
                ToolboxScreen()
            }

            composable(
                Screen.Settings.route,
                enterTransition = { fadeIn(tween(0)) },
                exitTransition = { fadeOut(tween(0)) },
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
