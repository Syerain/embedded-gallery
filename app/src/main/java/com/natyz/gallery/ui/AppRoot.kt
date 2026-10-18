package com.natyz.gallery.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.natyz.gallery.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(700)
        splash = false
    }
    if (splash) {
        Splash()
        return
    }

    val nav = rememberNavController()
    // nav transitions follow the settings: system-style fade (scaled by anim
    // speed) or none at all when "use system animation API" is off
    val dur = (260f / ui.animSpeed).toInt().coerceAtLeast(1)
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { if (ui.sysAnim) fadeIn(tween(dur)) else EnterTransition.None },
        exitTransition = { if (ui.sysAnim) fadeOut(tween(dur)) else ExitTransition.None },
        popEnterTransition = { if (ui.sysAnim) fadeIn(tween(dur)) else EnterTransition.None },
        popExitTransition = { if (ui.sysAnim) fadeOut(tween(dur)) else ExitTransition.None }
    ) {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpen = { nav.navigate("gallery/${it.id}") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable(
            "gallery/{gid}",
            arguments = listOf(navArgument("gid") { type = NavType.StringType })
        ) { entry ->
            val gid = entry.arguments?.getString("gid") ?: return@composable
            GalleryScreen(
                vm = vm,
                gid = gid,
                onBack = { nav.popBackStack() },
                onPlay = { idx -> nav.navigate("player/$gid/$idx") }
            )
        }
        composable(
            "player/{gid}/{index}",
            arguments = listOf(
                navArgument("gid") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType }
            )
        ) { entry ->
            val gid = entry.arguments?.getString("gid") ?: return@composable
            val idx = entry.arguments?.getInt("index") ?: 0
            PlayerScreen(vm = vm, gid = gid, startIndex = idx, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

/** Red background, white lowercase "natyz" — Mojang-style splash. */
@Composable
private fun Splash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD50000)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "natyz",
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 2.sp
        )
    }
}
