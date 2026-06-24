package com.example.petmap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.petmap.ui.detail.DetailScreen
import com.example.petmap.ui.favorite.FavoriteScreen
import com.example.petmap.ui.list.ListScreen
import com.example.petmap.ui.map.MapScreen

@Composable
fun PetMapNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAP,
        modifier = modifier,
    ) {
        val openDetail: (String) -> Unit = { id -> navController.navigate(Routes.detail(id)) }

        composable(Routes.MAP) {
            MapScreen(onPlaceClick = openDetail)
        }
        composable(Routes.LIST) {
            ListScreen(onPlaceClick = openDetail)
        }
        composable(Routes.FAVORITE) {
            FavoriteScreen(onPlaceClick = openDetail)
        }
        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.DETAIL_ARG_ID) { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
