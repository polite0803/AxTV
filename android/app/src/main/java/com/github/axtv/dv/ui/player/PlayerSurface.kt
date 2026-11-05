/*
 * 画面层：把 ExoPlayer 的输出接到 Compose 的 AndroidView。
 *
 * 渲染路径（来自设置「播放器画面类型」）：
 * - SURFACE_VIEW     → Media3 PlayerView（内部 SurfaceView，低端盒合成开销小）
 * - TEXTURE_VIEW     → AspectRatioFrameLayout + TextureView（可做转场/动画，合成走 GPU）
 * - GL_SURFACE_VIEW  → 暂回退 SURFACE_VIEW（GL 直通管线属另一条线，见 refactor/player/NOTES.md）
 *
 * 说明：media3-ui 1.5.1 的 PlayerView 没有公共 setSurfaceType()，surface_type 只能由 XML 属性决定；
 * res/ 目录（双星号） 不在本次改动范围，故 TextureView 路径直接交给 ExoPlayer.setVideoTextureView()。
 */
package com.github.axtv.dv.ui.player

import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import axtv.settings.Settings

@Composable
fun PlayerSurface(
    session: PlayerSession,
    surfaceType: Settings.SurfaceType,
    modifier: Modifier = Modifier,
) {
    when (surfaceType) {
        Settings.SurfaceType.TEXTURE_VIEW -> {
            // 读 videoSize 让宽高比随视频变化刷新（AndroidView.update 会随重组重跑）。
            val aspectRatio = session.videoSize.aspectRatio()
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    AspectRatioFrameLayout(context).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(AndroidColor.BLACK)
                        val textureView = TextureView(context)
                        addView(
                            textureView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        session.attachSurface(textureView)
                    }
                },
                update = { frame ->
                    if (aspectRatio > 0f) {
                        frame.setAspectRatio(aspectRatio)
                    }
                },
            )
        }

        // SURFACE_VIEW 与暂未实现的 GL_SURFACE_VIEW 都走 PlayerView。
        else -> {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    PlayerView(context).apply {
                        // 控制器由 Compose 浮层承担，避免两套进度条/焦点抢按键。
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(AndroidColor.BLACK)
                        keepScreenOn = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        session.attachSurface(this)
                    }
                },
            )
        }
    }
}
