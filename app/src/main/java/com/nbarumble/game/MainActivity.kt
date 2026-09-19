package com.nbarumble.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nbarumble.game.ui.game.GameRoute
import com.nbarumble.game.ui.game.GameViewModel
import com.nbarumble.game.ui.home.HomeRoute
import com.nbarumble.game.ui.home.HomeViewModel
import com.nbarumble.game.ui.lobby.LobbyRoute
import com.nbarumble.game.ui.lobby.LobbyViewModel
import com.nbarumble.game.ui.theme.NbaRumbleTheme
import com.nbarumble.game.ui.vmFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NbaRumbleTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as NbaRumbleApp
    NavHost(
        navController = navController,
        startDestination = HomeViewModel.ROUTE_HOME,
        modifier = modifier
    ) {
        composable(HomeViewModel.ROUTE_HOME) {
            val vm: HomeViewModel = viewModel(
                factory = vmFactory { HomeViewModel(app) }
            )
            HomeRoute(vm) { route -> navController.navigate(route) }
        }

        composable(
            route = "${HomeViewModel.ROUTE_LOBBY}/{code}/{isHost}",
            arguments = listOf(
                navArgument("code") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
            val vm: LobbyViewModel = viewModel(
                key = "lobby-$code",
                factory = vmFactory { LobbyViewModel(app, code, isHost) }
            )
            LobbyRoute(
                vm = vm,
                onStart = { navController.navigate("${HomeViewModel.ROUTE_GAME}/$code") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${HomeViewModel.ROUTE_GAME}/{code}",
            arguments = listOf(
                navArgument("code") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            val vm: GameViewModel = viewModel(
                key = "game-$code",
                factory = vmFactory { GameViewModel(app, code) }
            )
            GameRoute(
                code = code,
                vm = vm,
                onExit = { navController.navigateHome() }
            )
        }
    }
}

private fun NavHostController.navigateHome() {
    navigate(HomeViewModel.ROUTE_HOME) {
        popUpTo(HomeViewModel.ROUTE_HOME) { inclusive = false }
        launchSingleTop = true
    }
}