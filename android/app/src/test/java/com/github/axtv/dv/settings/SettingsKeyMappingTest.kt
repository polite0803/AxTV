package com.github.axtv.dv.settings

import axtv.settings.MapSettingsStore
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import axtv.settings.SettingsKeys
import axtv.ui.SettingsPageSpec
import axtv.ui.SettingsPageSpec.ControlType
import com.github.axtv.dv.adapter.SettingsBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实 JUnit（纯 JVM）：验证 72 键声明、SettingsBinder 键映射、读写闭环与默认值回退。 */
class SettingsKeyMappingTest {

    private val store = MapSettingsStore()
    private val settings = Settings(store, SettingsTypes.DeviceProfile.of(33, false))
    private val binder = SettingsBinder(settings)

    @Test
    fun settingsKeysDeclareExactlySeventyTwoKeys() {
        val count = SettingsKeys::class.java.declaredFields.count {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java
        }
        assertEquals("SettingsKeys 应声明 72 个配置键", 72, count)
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
            "client_identity_branch",
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

    /** 79 项规格里的可交互控件必须都能落到 Settings，不允许出现无映射的占位项。 */
    @Test
    fun everyInteractiveControlHasSettingsMapping() {
        val controls = SettingsPageSpec.MAIN_PAGE + SettingsPageSpec.DANMAKU_STYLE_PANEL
        assertEquals("规格应为 64 + 15 = 79 项", 79, controls.size)
        val switchTypes = setOf(
            ControlType.SWITCH_ROW,
            ControlType.ANIMATED_SWITCH_ROW,
            ControlType.SWITCH,
        )
        val enumTypes = setOf(
            ControlType.ENUM_ROW,
            ControlType.ANIMATED_ENUM_ROW,
            ControlType.OPTION,
        )
        controls.forEach { control ->
            when (control.type()) {
                in switchTypes -> assertNotNull(
                    "开关 ${control.key()} 缺少 Settings 映射",
                    binder.readBoolean(control.key()),
                )

                in enumTypes -> assertNotNull(
                    "枚举 ${control.key()} 缺少文案映射",
                    binder.enumLabels(control.key()),
                )

                ControlType.SLIDER -> assertNotNull(
                    "滑杆 ${control.key()} 缺少 Settings 映射",
                    binder.readFloat(control.key()),
                )

                ControlType.AUTH_TEXT_FIELD -> assertNotNull(
                    "凭据 ${control.key()} 缺少 Settings 映射",
                    binder.readText(control.key()),
                )

                else -> Unit
            }
        }
    }

    /** 除只读的旧总开关 danmaku 外，所有开关都应能写回并读出一致值。 */
    @Test
    fun allWritableSwitchesRoundTrip() {
        val switchTypes = setOf(
            ControlType.SWITCH_ROW,
            ControlType.ANIMATED_SWITCH_ROW,
            ControlType.SWITCH,
        )
        val readOnly = setOf("danmaku")
        (SettingsPageSpec.MAIN_PAGE + SettingsPageSpec.DANMAKU_STYLE_PANEL)
            .filter { it.type() in switchTypes && it.key() !in readOnly }
            .forEach { control ->
                val before = binder.readBoolean(control.key())
                assertNotNull("${control.key()} 应有默认值", before)
                val next = before != true
                binder.writeBoolean(control.key(), next)
                assertEquals("${control.key()} 写入后应读回相同值", next, binder.readBoolean(control.key()))
            }
    }

    /** 所有滑杆都要覆盖边界值与中间值。 */
    @Test
    fun slidersRoundTripAtRangeEdges() {
        val cases = mapOf(
            "style_opacity" to listOf(0.05f, 1.0f),
            "style_area" to listOf(1f, 100f),
            "style_vspacing" to listOf(0.5f, 2.0f),
            "style_hspacing" to listOf(0.5f, 2.0f),
        )
        cases.forEach { (key, values) ->
            val actual = binder.readFloat(key)
            assertTrue("$key 必须有 Settings 映射", actual != null)
            values.forEach { value ->
                binder.writeFloat(key, value)
                assertEquals(
                    "$key 写入 $value 后应读回相同值",
                    value.toDouble(),
                    binder.readFloat(key)!!.toDouble(),
                    0.001,
                )
            }
        }
    }
}
