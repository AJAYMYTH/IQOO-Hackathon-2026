package com.apexos.repoguardian.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.apexos.repoguardian.ui.splash.SplashScreen
import com.apexos.repoguardian.ui.auth.AuthScreen
import com.apexos.repoguardian.ui.repopicker.RepoPickerScreen
import com.apexos.repoguardian.ui.dashboard.DashboardScreen
import com.apexos.repoguardian.ui.review.ReviewScreen
import com.apexos.repoguardian.ui.prstatus.PrStatusScreen
import com.apexos.repoguardian.ui.cicd.CiCdGeneratorScreen
import com.apexos.repoguardian.ui.settings.SettingsScreen
import com.apexos.repoguardian.ui.modelbrowser.ModelBrowserScreen

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val REPO_PICKER = "repo_picker"
    const val DASHBOARD = "dashboard"
    const val REVIEW = "review/{owner}/{repo}/{sha}"
    const val PR_STATUS = "pr_status/{owner}/{repo}/{prNumber}"
    const val CICD_GENERATOR = "cicd_generator/{owner}/{repo}"
    const val SETTINGS = "settings"
    const val MODEL_BROWSER = "model_browser"

    fun review(owner: String, repo: String, sha: String) = "review/$owner/$repo/$sha"
    fun prStatus(owner: String, repo: String, prNumber: Int) = "pr_status/$owner/$repo/$prNumber"
    fun cicdGenerator(owner: String, repo: String) = "cicd_generator/$owner/$repo"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(navController = navController)
        }
        composable(Routes.AUTH) {
            AuthScreen(navController = navController)
        }
        composable(Routes.REPO_PICKER) {
            RepoPickerScreen(navController = navController)
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(navController = navController)
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("sha") { type = NavType.StringType }
            )
        ) {
            ReviewScreen(navController = navController)
        }
        composable(
            route = Routes.PR_STATUS,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("prNumber") { type = NavType.IntType }
            )
        ) {
            PrStatusScreen(navController = navController)
        }
        composable(
            route = Routes.CICD_GENERATOR,
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType }
            )
        ) {
            CiCdGeneratorScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
        composable(Routes.MODEL_BROWSER) {
            ModelBrowserScreen(navController = navController)
        }
    }
}
