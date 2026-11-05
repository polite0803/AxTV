/*
 * 「正在播放」小组件规格单测。
 *
 * 覆盖：有播放态 → CONTENT；无播放态 → IDLE；播放/暂停文案；暂停快照超时清理。
 * 说明：未运行 Gradle（构建目录被另一条 AGP 升级占用），本文件仅静态编写。
 */
package com.github.axtv.dv.widget

import axtv.widget.WidgetLoadState
import axtv.widget.WidgetRenderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingWidgetSpecTest {

    private fun input(
        playbackNull: Boolean,
        playing: Boolean = false,
        widthDp: Float = 736f,
        locked: Boolean = false,
        state: WidgetLoadState = WidgetLoadState.READY,
    ): WidgetRenderSpec.ViewInput = WidgetRenderSpec.ViewInput(
        locked,                 // locked
        true,                   // historyEnabled（NOW_PLAYING 不看该分支）
        true,                   // accountMatches
        state,
        if (playbackNull) 0 else 1,
        playbackNull,           // firstNull
        playbackNull,           // playbackNull
        playing,
        widthDp,
        192f,
        0L,
        "本机播放",
    )

    @Test
    fun `无播放态时进入 idle`() {
        val resolution = WidgetRenderSpec.resolve(
            WidgetRenderSpec.WidgetId.NOW_PLAYING,
            input(playbackNull = true),
        )
        assertEquals(WidgetRenderSpec.Screen.IDLE, resolution.screen())
    }

    @Test
    fun `有播放态且宽屏时进入内容态`() {
        val resolution = WidgetRenderSpec.resolve(
            WidgetRenderSpec.WidgetId.NOW_PLAYING,
            input(playbackNull = false, playing = true, widthDp = 736f),
        )
        assertEquals(WidgetRenderSpec.Screen.CONTENT, resolution.screen())
    }

    @Test
    fun `有播放态且窄屏时仍进入内容态`() {
        val resolution = WidgetRenderSpec.resolve(
            WidgetRenderSpec.WidgetId.NOW_PLAYING,
            input(playbackNull = false, playing = true, widthDp = 320f),
        )
        assertEquals(WidgetRenderSpec.Screen.CONTENT, resolution.screen())
    }

    @Test
    fun `播放与暂停文案不同`() {
        val playing = WidgetRenderSpec.nowPlayingStatusText(true, "")
        val paused = WidgetRenderSpec.nowPlayingStatusText(false, "")
        assertTrue(playing.startsWith(WidgetRenderSpec.Strings.PLAYING))
        assertTrue(paused.startsWith(WidgetRenderSpec.Strings.PAUSED))
        assertNotEquals(playing, paused)
        assertNotEquals(
            WidgetRenderSpec.nowPlayingToggleText(true),
            WidgetRenderSpec.nowPlayingToggleText(false),
        )
    }

    @Test
    fun `暂停快照超过 TTL 被清理`() {
        val paused = WidgetPlaybackItem("u", "", "", false, false, "本机播放", "")
        val publishedAt = 1_000L
        assertTrue(isPlaybackStale(paused, publishedAt, publishedAt + PLAYBACK_STALE_MS + 1))
        assertFalse(isPlaybackStale(paused, publishedAt, publishedAt + PLAYBACK_STALE_MS - 1))
    }

    @Test
    fun `播放中的快照不因 TTL 被清理`() {
        val playing = WidgetPlaybackItem("u", "", "", true, false, "本机播放", "")
        val publishedAt = 1_000L
        assertFalse(isPlaybackStale(playing, publishedAt, publishedAt + PLAYBACK_STALE_MS * 10))
    }

    @Test
    fun `空快照不被判为过期`() {
        assertFalse(isPlaybackStale(null, 0L, Long.MAX_VALUE))
    }
}
