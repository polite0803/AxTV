package com.github.axtv.dv.settings

import axtv.settings.MapSettingsStore
import axtv.settings.Settings
import axtv.settings.SettingsKeys
import com.github.axtv.dv.adapter.SettingsBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 真实 JUnit（纯 JVM）：验证 71 键声明、SettingsBinder 键映射、读写闭环与默认值回退。 */
class SettingsKeyMappingTest {

    private val store = MapSettingsStore()
    private val settings = Settings(store, Settings.DeviceProfile.of(33, false))
    private val binder = SettingsBinder(settings)

    @Test
    fun settingsKeysDeclareExactlySeventyOneKeys() {
        val count = SettingsKeys::class.java.declaredFields.count {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java
        }
        assertEquals("SettingsKeys 应声明 71 个配置键", 71, count)
    }

    @Test
    fun booleanKeysRoundTripThroughBinder() {
        listOf(
            "video_danmaku",
            "live_danmaku",
            "danmaku_emoji",
            "disable_double_back_exit",
            "surface_buffer_align",
        ).forEach { key ->
            val before = binder.readBoolean(key)
            assertNotNull("$key 应有默认值", before)
            val next = before != true
            binder.writeBoolean(key, next)
            assertEquals("$key 写入后应读回相同值", next, binder.readBoolean(key))
        }
    }

    @Test
    fun enumKeysRoundTripThroughBinder() {
        listOf(
            "live_danmaku_style",
            "app_font_scale",
            "default_home",
            "theme_mode",
            "player_surface_type",
        ).forEach { key ->
            val labels = binder.enumLabels(key)
            assertNotNull("$key 应有枚举文案", labels)
            val count = labels!!.size
            for (index in 0 until count) {
                binder.writeEnumIndex(key, index)
                assertEquals("$key 索引 $index 应读回相同值", index, binder.readEnumIndex(key))
            }
        }
    }

    @Test
    fun defaultsAreStableBeforeAnyWrite() {
        val keys = listOf("video_danmaku", "live_danmaku", "theme_mode", "app_font_scale")
        keys.forEach { key ->
            val first = if (binder.readBoolean(key) != null) binder.readBoolean(key) else binder.readEnumIndex(key)
            val second = if (binder.readBoolean(key) != null) binder.readBoolean(key) else binder.readEnumIndex(key)
            assertEquals("$key 未写入时默认值应稳定", first, second)
        }
    }
}
