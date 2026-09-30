package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("home", "Home", AmIcons.Home, AmIcons.HomeSelected),
    New("new", "New", AmIcons.New, AmIcons.NewSelected),
    Radio("radio", "Radio", AmIcons.Radio, AmIcons.RadioSelected),
    Library("library", "Library", AmIcons.Library, AmIcons.LibrarySelected),
    Search("search", "Search", AmIcons.Search, AmIcons.SearchSelected),
}

/** Home / New / Radio / Library in a floating glass pill, Search as its own round glass button. */
@Composable
fun BottomNavigation(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val am = LocalAm.current
    Row(modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .glass(RoundedCornerShape(32.dp), am.glass, elevation = 10.dp)
                .padding(6.dp),
        ) {
            listOf(Tab.Home, Tab.New, Tab.Radio, Tab.Library).forEach { tab ->
                NavItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f))
            }
        }
        val searchSelected = selected == Tab.Search
        Box(
            Modifier
                .size(64.dp)
                .glass(CircleShape, am.glass, elevation = 10.dp)
                .clickable(remember { MutableInteractionSource() }, null) { onSelect(Tab.Search) },
            contentAlignment = Alignment.Center,
        ) {
            val tint by animateColorAsState(if (searchSelected) AmAccent else am.secondary, tween(200), label = "searchTint")
            Icon(if (searchSelected) Tab.Search.selectedIcon else Tab.Search.icon, "Search", tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val am = LocalAm.current
    val tint by animateColorAsState(if (selected) AmAccent else am.secondary, tween(200), label = "navTint")
    val pill by animateColorAsState(
        if (selected) (if (am.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)) else Color.Transparent,
        tween(220), label = "navPill",
    )
    Column(
        modifier
            .fillMaxHeight()
            .background(pill, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = AmType.Tiny.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = tint)
    }
}
