/*
 * 三个样板页的最小导航壳（只做结构 + 焦点，不接业务数据）。
 * 规格出处：refactor/app/NOTES.md（入口/路由）、refactor/ui/NOTES.md（页面绘制边界）。
 */
package com.github.axtv.dv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.home.HomeFeedScreen
import com.github.axtv.dv.ui.player.PlayerOverlayScreen
import com.github.axtv.dv.ui.settings.SettingsScreen

private enum class DemoScreen(val label: String) {
    HOME("首页"),
    SETTINGS("设置"),
    PLAYER("播放器"),
}

@Composable
fun AxTvApp() {
    var screen by rememberSaveable { mutableStateOf(DemoScreen.HOME) }
    // 焦点记忆：离开首页再回来时恢复到上次停留的信息流条目。
    var homeFocusIndex by rememberSaveable { mutableStateOf(0) }

    AxTvTheme {
        Column(modifier = Modifier.fillMaxSize().background(AxTvColors.Bg)) {
            DemoTabBar(current = screen, onSelect = { screen = it })
            when (screen) {
                DemoScreen.HOME -> HomeFeedScreen(
                    rememberedFocusIndex = homeFocusIndex,
                    onFocusIndexChange = { homeFocusIndex = it },
                    modifier = Modifier.weight(1f),
                )

                DemoScreen.SETTINGS -> SettingsScreen(modifier = Modifier.weight(1f))
                DemoScreen.PLAYER -> PlayerOverlayScreen(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DemoTabBar(current: DemoScreen, onSelect: (DemoScreen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface)
            .padding(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AxTV",
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(24.dp))
        DemoScreen.entries.forEach { item ->
            DemoTabItem(
                label = item.label,
                selected = item == current,
                onClick = { onSelect(item) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Compose for TV 基线 · 骨架",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

@Composable
private fun DemoTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
