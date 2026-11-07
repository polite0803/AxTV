/*
 * 顶栏品牌标识：中文「爱看」艺术字。
 *
 * 原来是直排英文「AxTV」，与页内标题重复、在中文界面里也显生硬。
 * 这里用粗黑体 + 青色到白色渐变 + 底部一道渐隐光条（呼应播放进度），
 * 纯 Compose 绘制：不引入位图、不依赖外部字体，任意分辨率都清晰。
 */
package com.github.axtv.dv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 用 BasicText 而不是 tv-material3 的 Text：后者的 color 参数会覆盖
        // TextStyle.brush，渐变会退化成纯色。
        BasicText(
            text = "爱看",
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                brush = Brush.horizontalGradient(
                    listOf(AxTvColors.Accent, Color(0xFFEAFBFF), AxTvColors.AccentWarm),
                ),
            ),
        )
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(3.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(AxTvColors.Accent, AxTvColors.Accent.copy(alpha = 0f)),
                    ),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}
