/*
 * 氛围背景（ambient_backdrop 的消费点）：宿主为首页信息流与详情页。
 *
 * 为什么分层：
 * - API 31+ 才有 RenderEffect.createBlurEffect（GPU 高斯模糊）：封面放大 1.2 倍铺满后模糊，
 *   放大是为了把模糊产生的透明边缘推出画面；SDK 判定写在 graphicsLayer 块内部，
 *   这是 lint 认可的守卫位置（NewApi 为 Error，会拦 release 构建）；
 * - 31 以下不做逐像素模糊（TV SoC 上会掉帧），也不铺图片，退化为「品牌底色纵向渐变」的纯 scrim；
 * - 模糊路径上图片之上再压一层 Bg 渐变 scrim，保证前景文字可读性。
 */
package com.github.axtv.dv.ui.appearance

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import coil.compose.AsyncImage
import com.github.axtv.dv.ui.AxTvColors

private val HAS_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal fun AmbientBackdrop(coverUrl: String, modifier: Modifier = Modifier) {
    // 开关关 / 无封面：完全不绘制，宿主页面保持纯色背景。
    if (coverUrl.isEmpty() || !AxAppearance.ambientEnabled()) return
    Box(modifier = modifier.fillMaxSize()) {
        if (HAS_BLUR) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // 系统 RenderEffect 建好后转成 Compose 侧持有类型（asComposeRenderEffect，API 31+）。
                            renderEffect = RenderEffect.createBlurEffect(
                                90f, 90f, Shader.TileMode.CLAMP,
                            ).asComposeRenderEffect()
                        }
                    },
            )
        }
        // HAS_BLUR=false 时上面分支不执行，这里就是全部：静态渐变遮罩（plain scrim fallback）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AxTvColors.Bg.copy(alpha = if (HAS_BLUR) 0.45f else 0.90f),
                            AxTvColors.Bg.copy(alpha = if (HAS_BLUR) 0.85f else 0.98f),
                        ),
                    ),
                ),
        )
    }
}
