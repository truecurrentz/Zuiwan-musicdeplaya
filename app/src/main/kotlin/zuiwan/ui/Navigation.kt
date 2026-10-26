package zuiwan.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zuiwan.theme.LocalGlass

sealed interface Route {
    val key: String

    data object Home : Route { override val key = "home" }
    data object Explore : Route { override val key = "explore" }
    data class Search(val initialQuery: String = "") : Route {
        override val key = if (initialQuery.isBlank()) "search" else "search:$initialQuery"
    }
    data object Library : Route { override val key = "library" }
    data object Recognize : Route { override val key = "recognize" }

    data class Album(val id: String) : Route { override val key = "album:$id" }
    data class Artist(val id: String) : Route { override val key = "artist:$id" }
    data class Playlist(val id: String, val local: Boolean = false) : Route { override val key = "playlist:$id:$local" }
    data class Browse(val browseId: String, val params: String?, val title: String) : Route {
        override val key = "browse:$browseId:${params.orEmpty()}"
    }
    data object History : Route { override val key = "history" }
    data object Stats : Route { override val key = "stats" }
    data object Equalizer : Route { override val key = "eq" }
    data object Studio : Route { override val key = "studio" }
    data object Visualizer : Route { override val key = "visualizer" }
    data object Settings : Route { override val key = "settings" }
    data object Login : Route { override val key = "login" }
}

enum class MainTab(val route: Route, val label: String, val icon: ImageVector) {
    HOME(Route.Home, "Home", ZIcons.Home),
    SEARCH(Route.Search(), "Search", ZIcons.Search),
    LIBRARY(Route.Library, "Library", ZIcons.Grid),
}

class NavStack {
    val stack = mutableStateListOf<Route>(Route.Home)

    val current: Route
        get() = stack.lastOrNull() ?: Route.Home

    val activeTab: MainTab
        get() {
            for (i in stack.lastIndex downTo 0) {
                when (stack[i]) {
                    Route.Home -> return MainTab.HOME
                    is Route.Search, Route.Explore, Route.Recognize -> return MainTab.SEARCH
                    Route.Library -> return MainTab.LIBRARY
                    else -> {}
                }
            }
            return MainTab.HOME
        }

    fun switchtab(tab: MainTab) {
        if (current.key == tab.route.key) return
        stack.clear()
        stack.add(tab.route)
    }

    fun open(route: Route) {
        if (current.key == route.key) return
        stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@Composable
fun floatingbar(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    connectedAbove: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val dockShape = if (connectedAbove) {
        RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp,
        )
    } else {
        RoundedCornerShape(28.dp)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        glass(
            shape = dockShape,
            strong = true,
            frosted = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) glassTokens.accentSoft else Color.Transparent,
                        animationSpec = tween(220),
                        label = "dockPill",
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) glassTokens.accent else glassTokens.ink,
                        animationSpec = tween(220),
                        label = "dockIcon",
                    )
                    val labelTint by animateColorAsState(
                        targetValue = if (selected) glassTokens.accent else glassTokens.inkSecondary,
                        animationSpec = tween(220),
                        label = "dockLabel",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(if (glassTokens.compact) 46.dp else 56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelect(tab) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(56.dp)
                                .height(if (glassTokens.compact) 26.dp else 30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillColor),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier.size(if (glassTokens.compact) 18.dp else 20.dp),
                            )
                        }
                        if (!glassTokens.compact) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = labelTint,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
