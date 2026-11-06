/*
 * 锁定扫码后「需要二次验证」的判据与登录完成地址的提取。
 *
 * 背景：真机上扫码后一直停在「已扫码 / 手机已确认」，验证窗口不出现。原因有三类：
 *   1) 验证页跳到了 passport/sso/verify 等域名，DOM 是干净的，只能看 URL；
 *   2) 风控信息只在 check_qrconnect 响应里，DOM 上没有；
 *   3) 自建二维码会话只轮询不跳转，confirmed 响应里的登录完成地址没人访问，Cookie 永不落地。
 * 这些判据都是字符串匹配，最容易随响应格式漂移，所以在这里钉死。
 */
package axtv.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrVerificationDetectTest {

    @Test
    @DisplayName("confirmed 响应里的登录完成地址：转义斜杠与 \\u0026 都要还原")
    void extractsRedirectUrl() {
        String poll = "{\"data\":{\"status\":\"confirmed\","
                + "\"redirect_url\":\"https:\\/\\/sso.douyin.com\\/login\\/?a=1\\u0026b=2\"}}";
        assertEquals("https://sso.douyin.com/login/?a=1&b=2", QrLoginBridge.confirmRedirectUrl(poll));
    }

    @Test
    @DisplayName("没有可用的登录完成地址时返回 null，不做猜测性跳转")
    void ignoresNonHttpRedirect() {
        assertNull(QrLoginBridge.confirmRedirectUrl(null));
        assertNull(QrLoginBridge.confirmRedirectUrl("{\"data\":{\"status\":\"scan\"}}"));
        assertNull(QrLoginBridge.confirmRedirectUrl(
                "{\"redirect_url\":\"javascript:alert(1)\"}"));
    }

    @Test
    @DisplayName("URL 判据只认验证相关路径，不误伤 creator 正常页面")
    void detectsVerificationUrl() {
        assertTrue(QrLoginBridge.urlLooksLikeVerification("https://sso.douyin.com/verify/center?x=1"));
        assertTrue(QrLoginBridge.urlLooksLikeVerification("https://passport.douyin.com/captcha/v2"));
        assertTrue(QrLoginBridge.urlLooksLikeVerification("https://www.douyin.com/challenge/slider"));
        assertFalse(QrLoginBridge.urlLooksLikeVerification("https://creator.douyin.com/creator-micro/home"));
        assertFalse(QrLoginBridge.urlLooksLikeVerification("https://www.douyin.com/user/self"));
        assertFalse(QrLoginBridge.urlLooksLikeVerification(null));
    }

    @Test
    @DisplayName("轮询响应里的风控字段：已知键名命中，普通状态不误判")
    void detectsVerificationFieldsInPoll() {
        assertTrue(QrLoginBridge.pollLooksLikeVerification("{\"account_flow\":\"verify\"}"));
        assertTrue(QrLoginBridge.pollLooksLikeVerification("{\"data\":{\"verify_type\":\"sms\"}}"));
        assertTrue(QrLoginBridge.pollLooksLikeVerification("{\"data\":{\"captcha\":{\"type\":1}}}"));
        assertFalse(QrLoginBridge.pollLooksLikeVerification("{\"data\":{\"status\":\"scan\"}}"));
        assertFalse(QrLoginBridge.pollLooksLikeVerification("{\"data\":{\"status\":\"confirmed\"}}"));
        assertFalse(QrLoginBridge.pollLooksLikeVerification(null));
    }

    @Test
    @DisplayName("兼容性提示：快照能识别，脚本只点已知按钮（不删 DOM）")
    void dismissesCompatDialog() {
        assertTrue(QrLoginBridge.SNAPSHOT_SCRIPT.contains("compat:"),
                "快照必须上报兼容性提示，否则页面被警告弹窗挡住时无人处理");
        assertTrue(QrLoginBridge.SNAPSHOT_SCRIPT.contains("不太兼容当前平台"));
        String script = QrLoginBridge.DISMISS_COMPAT_SCRIPT;
        assertTrue(script.contains("我知道了"), "弹窗主按钮是「我知道了」");
        assertTrue(script.contains("不再提醒"), "次要按钮是「不再提醒」");
        assertFalse(script.contains("removeChild"), "只点击，不做破坏性 DOM 删除");
    }
}
