/*
 * 连播队列构造（AwemePlayback.awemePlaylistProvider）的纯 JVM 单测。
 *
 * 0.1.4 修掉的回归：startIndex 之前的条目曾被绕回队尾，连播会在已加载列表里循环；
 * 这里钉死「只向后连播」语义，同时覆盖越界钳制、空列表与字段兜底。
 */
package com.github.axtv.dv.ui.nav

import axtv.model.Aweme
import axtv.model.Author
import axtv.model.PlayProgress
import axtv.model.Statistics
import axtv.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class AwemePlaybackTest {

    /** 12 段全量位构造（record 无 Kotlin 命名参数），只关心 id 与文案。 */
    private fun aweme(id: String, desc: String = ""): Aweme =
        Aweme(id, desc, Video(), Author(), Statistics(), 0L, 0, PlayProgress(), 0L, 0L, "", null)

    @Test
    fun `起播条目起只向后连播，之前的条目不进队`() {
        val awemes = (1..5).map { aweme("id$it", "视频$it") }
        val entries = awemePlaylistProvider(awemes, startIndex = 2).items()
        assertEquals(listOf("id3", "id4", "id5"), entries.map { it.id })
    }

    @Test
    fun `startIndex 为 0 时全列表按原序入队`() {
        val awemes = (1..3).map { aweme("id$it") }
        val entries = awemePlaylistProvider(awemes, startIndex = 0).items()
        assertEquals(listOf("id1", "id2", "id3"), entries.map { it.id })
    }

    @Test
    fun `startIndex 越界钳到最后一项`() {
        val awemes = listOf(aweme("a"), aweme("b"))
        val entries = awemePlaylistProvider(awemes, startIndex = 99).items()
        assertEquals(listOf("b"), entries.map { it.id })
    }

    @Test
    fun `空列表给空队列`() {
        val entries = awemePlaylistProvider(emptyList(), startIndex = 0).items()
        assertEquals(emptyList<String>(), entries.map { it.id })
    }

    @Test
    fun `缺 id 与文案时按序号与无标题兜底`() {
        val entries = awemePlaylistProvider(listOf(aweme("", ""), aweme("", ""))).items()
        assertEquals(listOf("aweme_0", "aweme_1"), entries.map { it.id })
        assertEquals(listOf("无标题视频", "无标题视频"), entries.map { it.title })
    }

    @Test
    fun `条目带服务端 id 与文案`() {
        val entries = awemePlaylistProvider(listOf(aweme("id1", "标题一"))).items()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("id1", entry.awemeId)
        assertEquals("标题一", entry.title)
    }
}
