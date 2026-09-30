package com.abtinf.glassmusic.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.ui.glass.LiquidBottomTab
import com.abtinf.glassmusic.ui.glass.LiquidBottomTabLayer
import com.abtinf.glassmusic.ui.glass.LiquidBottomTabs
import com.abtinf.glassmusic.ui.glass.LocalLiquidBottomTabLayer
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm
import com.kyant.backdrop.Backdrop

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", AmIcons.HomeSelected),
    Library("library", "Library", AmIcons.Library),
    Repo("repo", "Repo", AmIcons.New),
    Search("search", "Search", AmIcons.Search),
}

/**
 * Liquid-glass tab bar: one floating pill whose selection lens can be tapped or dragged between tabs.
 * The selected tab is tinted with the accent colour through the lens (see [LiquidBottomTabs]).
 */
@Composable
fun AppTabBar(selected: Tab, onSelect: (Tab) -> Unit, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val am = LocalAm.current
    // The selection lambda must be stable and read state, otherwise the lens never notices a plain tap.
    val current = androidx.compose.runtime.rememberUpdatedState(selected)
    val selectedIndex = androidx.compose.runtime.remember { { current.value.ordinal } }
    LiquidBottomTabs(
        selectedTabIndex = selectedIndex,
        onTabSelected = { onSelect(Tab.entries[it]) },
        backdrop = backdrop,
        tabsCount = Tab.entries.size,
        modifier = modifier,
    ) {
        Tab.entries.forEach { tab ->
            LiquidBottomTab(onClick = { onSelect(tab) }) {
                if (LocalLiquidBottomTabLayer.current != LiquidBottomTabLayer.Overlay) {
                    Icon(tab.icon, tab.label, tint = am.text, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(1.dp))
                    Text(tab.label, style = AmType.Tiny.copy(fontSize = 11.sp), color = am.text, maxLines = 1)
                }
            }
        }
    }
}
