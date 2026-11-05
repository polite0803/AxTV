/*
 * 「已看」环形缓冲的裁剪语义（feed_history_capacity 的消费点）。
 * 语义对齐原版 src/app/p000/hu2.java:297-313：remove(id) → add(id) → take(capacity)。
 */
package com.github.axtv.dv.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewedVideoTrackerTest {

    @Test
    fun `重看同一条只保留最新位置`() {
        val result = ViewedVideoTracker.trimmed(listOf("a", "b", "c"), "a", 100)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `重看靠后的条目会把它挪到队尾`() {
        val result = ViewedVideoTracker.trimmed(listOf("a", "b", "c"), "b", 100)
        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `超出容量从队头丢弃`() {
        val result = ViewedVideoTracker.trimmed(listOf("a", "b", "c"), "d", 3)
        assertEquals(listOf("d", "a", "b"), result)
    }

    /** 50 与 1000 的差别必须能观察到：同一条最早记录在两种容量下的存活位置不同。 */
    @Test
    fun `容量决定保留多少条`() {
        val many = (1..1200).map { "id$it" }
        assertEquals(50, ViewedVideoTracker.trimmed(many, "new", 50).size)
        assertEquals(1000, ViewedVideoTracker.trimmed(many, "new", 1000).size)
        // 容量 50 只留到 id49，id500 已被忘掉；容量 1000 时它还留着。
        assertEquals(false, ViewedVideoTracker.trimmed(many, "new", 50).contains("id500"))
        assertEquals(true, ViewedVideoTracker.trimmed(many, "new", 1000).contains("id500"))
    }
}
