package com.kidsshorts.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kidsshorts.di.AppContainer
import com.kidsshorts.ui.child.ChildFeedScreen
import com.kidsshorts.ui.onboarding.OnboardingScreen
import com.kidsshorts.ui.parent.AddChannelScreen
import com.kidsshorts.ui.parent.BlockedScreen
import com.kidsshorts.ui.parent.ChannelsScreen
import com.kidsshorts.ui.parent.DataScreen
import com.kidsshorts.ui.parent.LanguageScreen
import com.kidsshorts.ui.parent.ParentDashboardScreen
import com.kidsshorts.ui.parent.PinGateScreen
import com.kidsshorts.ui.parent.ReviewScreen
import com.kidsshorts.ui.parent.ScreenTimeScreen
import com.kidsshorts.ui.viewmodel.AppViewModelFactory
import com.kidsshorts.ui.viewmodel.AuthViewModel
import com.kidsshorts.ui.viewmodel.ChildViewModel
import com.kidsshorts.ui.viewmodel.ParentViewModel

@Composable
fun AppNav(container: AppContainer, onCloseApp: () -> Unit) {
    val nav = rememberNavController()
    val factory = remember { AppViewModelFactory(container) }
    val auth: AuthViewModel = viewModel(factory = factory)
    val parent: ParentViewModel = viewModel(factory = factory)
    val start = remember { if (auth.isPinSet()) Routes.CHILD else Routes.ONBOARDING }

    // Any parent screen shown while the parent session is locked bounces to the PIN gate.
    val unlocked by auth.unlocked.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(unlocked, route) {
        if (!unlocked && route != null && route.startsWith("parent")) {
            nav.navigate(Routes.GATE) { popUpTo(Routes.CHILD) }
        }
    }

    fun exitToChild() {
        auth.lock()
        // Fallback guarantees this never becomes a dead end if CHILD isn't on the back stack.
        if (!nav.popBackStack(Routes.CHILD, inclusive = false)) {
            nav.navigate(Routes.CHILD) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(auth) {
                // Child mode sits underneath the parent area so every exit path has somewhere to go.
                nav.navigate(Routes.CHILD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                nav.navigate(Routes.PARENT)
            }
        }
        composable(Routes.CHILD) {
            val child: ChildViewModel = viewModel(factory = factory)
            ChildFeedScreen(child, container.applicationId) { nav.navigate(Routes.GATE) }
        }
        composable(Routes.GATE) {
            PinGateScreen(
                auth = auth,
                onUnlocked = { nav.navigate(Routes.PARENT) { popUpTo(Routes.GATE) { inclusive = true } } },
                onBackToVideos = { exitToChild() },
                onCloseApp = onCloseApp,
            )
        }
        composable(Routes.PARENT) {
            ParentDashboardScreen(parent, onNavigate = { nav.navigate(it) }, onExit = { exitToChild() })
        }
        composable(Routes.CHANNELS) {
            ChannelsScreen(parent, onBack = { nav.popBackStack() }, onAdd = { nav.navigate(Routes.ADD_CHANNEL) })
        }
        composable(Routes.ADD_CHANNEL) { AddChannelScreen(parent) { nav.popBackStack() } }
        composable(Routes.LANGUAGES) { LanguageScreen(parent) { nav.popBackStack() } }
        composable(Routes.REVIEW) { ReviewScreen(parent) { nav.popBackStack() } }
        composable(Routes.BLOCKED) { BlockedScreen(parent) { nav.popBackStack() } }
        composable(Routes.SCREEN_TIME) { ScreenTimeScreen(parent) { nav.popBackStack() } }
        composable(Routes.DATA) {
            DataScreen(parent, auth, onBack = { nav.popBackStack() }, onReset = { resetToOnboarding(nav) })
        }
    }
}

private fun resetToOnboarding(nav: NavHostController) {
    nav.navigate(Routes.ONBOARDING) { popUpTo(nav.graph.id) { inclusive = true } }
}
