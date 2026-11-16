package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Dashboard : AppRoute

    @Serializable
    data object Inspect : AppRoute

    @Serializable
    data object History : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class Results(val recordId: String) : AppRoute

    @Serializable
    data class Advisory(val recordId: String) : AppRoute
}

enum class TopLevelDestination(
    val route: AppRoute,
    val label: String,
    val testTag: String
) {
    DASHBOARD(AppRoute.Dashboard, "Home", "nav_dashboard"),
    INSPECT(AppRoute.Inspect, "Inspect", "nav_inspect"),
    HISTORY(AppRoute.History, "History", "nav_history"),
    SETTINGS(AppRoute.Settings, "Settings", "nav_settings")
}
