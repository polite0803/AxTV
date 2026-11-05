/* 搜索页：SearchUiSpec（4 项，来自 SmokeTest L273-286）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import axtv.ui.SearchUiSpec;

import org.junit.jupiter.api.Test;

class SearchUiSpecTest {

    @Test
    void threeSpecialErrorCodes() {
        assertEquals("搜索参数校验失败", SearchUiSpec.errorLabel("", "params_check", null));
        assertEquals("搜索需要登录态，请更新 Cookie", SearchUiSpec.errorLabel("", "web_need_login", null));
        assertEquals("搜索被风控校验拦截，请稍后重试", SearchUiSpec.errorLabel("", "verify_check", null));
    }

    @Test
    void numericStatusCodeUsesDefault() {
        assertEquals("搜索错误: 2154", SearchUiSpec.errorLabel("", "2154", null));
    }

    @Test
    void channelPrefixConcatenation() {
        assertEquals("用户搜索失败: x", SearchUiSpec.errorLabel("用户", null, "x"));
        assertEquals("直播搜索异常: y", SearchUiSpec.channelErrorLabel(false, "y"));
    }

    @Test
    void threeEmptyStateLabels() {
        assertEquals("未找到相关视频", SearchUiSpec.emptyVideoLabel());
        assertEquals("未找到相关用户", SearchUiSpec.emptyLabel(true));
        assertEquals("未找到相关直播", SearchUiSpec.emptyLabel(false));
    }
}
