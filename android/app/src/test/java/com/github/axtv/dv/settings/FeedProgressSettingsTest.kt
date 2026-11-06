package com.github.axtv.dv.settings

import axtv.settings.MapSettingsStore
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推荐流水线进度三个持久化键的取值规则（纯 JVM）。
 *
 * 这组键决定首页/精选每次请求的 refresh_index 与回传的已展示 id：
 * 下限 1 是硬约束（0 会让服务端按非法序号请求），已展示 id 用逗号分隔，
 * 而 aweme id 只含数字，不会出现分隔符冲突。
 */
class FeedProgressSettingsTest {

    private val store = MapSettingsStore()
    private val settings = Settings(store, SettingsTypes.DeviceProfile.of(33, false))

    @Test
    fun feedRefreshIndexDefaultsToOneAndFloorsBelow() {
        assertEquals("未写过进度时首屏序号应为 1", 1, settings.feedRefreshIndex())

        settings.setFeedRefreshIndex(0)
        assertEquals("进度被写坏（0/负数）也必须回到 1", 1, settings.feedRefreshIndex())

        settings.setFeedRefreshIndex(-7)
        assertEquals(1, settings.feedRefreshIndex())

        settings.setFeedRefreshIndex(5)
        assertEquals(5, settings.feedRefreshIndex())
    }

    @Test
    fun featuredRefreshIndexUsesSameFloor() {
        assertEquals(1, settings.featuredRefreshIndex())

        settings.setFeaturedRefreshIndex(0)
        assertEquals(1, settings.featuredRefreshIndex())

        settings.setFeaturedRefreshIndex(4)
        assertEquals(4, settings.featuredRefreshIndex())
    }

    @Test
    fun feedPresentedIdsRoundTripAndDropBlankEntries() {
        assertTrue("默认没有已展示 id", settings.feedPresentedIds().isEmpty())

        settings.setFeedPresentedIds(listOf("111", "222", "333"))
        assertEquals(listOf("111", "222", "333"), settings.feedPresentedIds())

        settings.setFeedPresentedIds(listOf("111", "", "222"))
        assertEquals("空白 id 不应落进回传列表", listOf("111", "222"), settings.feedPresentedIds())

        settings.setFeedPresentedIds(emptyList())
        assertTrue("写空应读回空，而不是残留上一批", settings.feedPresentedIds().isEmpty())
    }
}
