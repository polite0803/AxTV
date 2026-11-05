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
 *
 * 「Surface 缓冲对齐」（设置项 surface_buffer_align）也在这里：
 * 证据 src/app/p000/ro5.java:35865-35885（m9952c：`fixedSize ? holder.setFixedSize((w+63)&~63, h)
 * : holder.setSizeFromLayout()`），开关由 src/app/p000/fl4.java:604-605 写进全局状态
 * src/app/p000/zz4.java，消费点 src/app/p000/ko5.java:239（onVideoSizeChanged）。
 * 打开时缓冲区按解码尺寸固定（宽度 64 对齐，核心层 VideoScaleMatrix.alignedSurfaceWidth），
 * 关闭时回到跟随布局；TextureView 没有 SurfaceHolder，原版同样只处理 SurfaceView。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.ui.player

import android.graphics.Color as AndroidColor
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import axtv.player.VideoScaleMatrix
import axtv.player.VideoSize
import axtv.settings.Settings

@Composable
fun PlayerSurface(
    session: PlayerSession,
    surfaceType: Settings.SurfaceType,
    surfaceBufferAlign: Boolean,
    modifier: Modifier = Modifier,
) {
    // 读 videoSize 让尺寸变化触发 update，缓冲区大小才可能跟上解码尺寸。
    val videoSize = session.videoSize
    val lastApplied = remember { mutableStateOf<Triple<Int, Int, Boolean>?>(null) }

    when (surfaceType) {
        Settings.SurfaceType.TEXTURE_VIEW -> {
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
                    if (videoSize.aspectRatio() > 0f) {
                        frame.setAspectRatio(videoSize.aspectRatio())
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
                update = { view ->
                    applySurfaceBufferSize(view, videoSize, surfaceBufferAlign, lastApplied)
                },
            )
        }
    }
}

/** 去重键 (w, h, fixedSize)，与原版 gb5 一致（ro5.java:35876）；同键不重复下发。 */
private fun applySurfaceBufferSize(
    playerView: PlayerView,
    size: VideoSize,
    align: Boolean,
    lastApplied: MutableState<Triple<Int, Int, Boolean>?>,
) {
    if (align && !size.isValid) {
        // 打开对齐但还没拿到解码尺寸：等 onVideoSizeChanged，不能先把布局尺寸写死。
        return
    }
    val surfaceView = playerView.videoSurfaceView as? SurfaceView ?: return
    val holder = surfaceView.holder ?: return
    val key = Triple(size.width(), size.height(), align)
    if (lastApplied.value == key) {
        return
    }
    lastApplied.value = key
    if (align) {
        holder.setFixedSize(VideoScaleMatrix.alignedSurfaceWidth(size.width()), size.height())
    } else {
        holder.setSizeFromLayout()
    }
}
