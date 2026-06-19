package com.linkgrab.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.linkgrab.app.ui.screens.HistoryScreen
import com.linkgrab.app.ui.screens.HomeScreen
import com.linkgrab.app.ui.screens.ResultScreen
import com.linkgrab.app.ui.screens.SettingsScreen
import com.linkgrab.app.ui.screens.UpdateLogScreen
import com.linkgrab.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "首页")
    data object Result : Screen("result", "解析结果")
    data object Settings : Screen("settings", "设置")
    data object UpdateLog : Screen("update_log", "更新日志")
    data object History : Screen("history", "历史记录")
}

// Bottom nav routes - no animation for these
private val bottomNavRoutes = setOf(Screen.Home.route, Screen.Settings.route)

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    initialShareText: String? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(defaultWindowInsetsPadding = false) {
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
}
