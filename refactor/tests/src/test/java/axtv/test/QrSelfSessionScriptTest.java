/*
 * 锁定电视端扫码的兜底通路：登录页自身不出码时用的自建会话脚本。
 *
 * 背景：电视盒子 WebView 内核普遍落后，creator.douyin.com 的登录页 UMD 包跑不起来时，
 * 页面既不发 get_qrcode 也不轮询，用户只能看到「二维码生成超时」。
 * 该脚本在页面内用 ES5 + XMLHttpRequest 自己出码/轮询，因此有两条硬约束：
 *   1) 参数必须与真实登录页一致（aid=2906 + request_host），否则风控返回 4031；
 *   2) 语法必须是旧内核能解析的 ES5（不能出现箭头函数、let/const、可选链、模板串）。
 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.panel.QrLoginBridge;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrSelfSessionScriptTest {

    @Test
    @DisplayName("出码参数与真实登录页一致：aid=2906 且带 request_host / is_new_login")
    void paramsMatchLoginPage() {
        String params = QrLoginBridge.selfQrParams();
        assertTrue(params.contains("&aid=2906&"), "aid 必须是 2906（6383 会被风控判 4031）：" + params);
        assertTrue(params.contains("request_host=https%253A%252F%252Fcreator.douyin.com"),
                "缺少 request_host 会被判 4031");
        assertTrue(params.contains("is_new_login=1"), "登录页固定带 is_new_login=1");
        assertFalse(params.contains("aid=6383"), "www 站的 6383 在 creator 域会被风控拒绝");
    }

    @Test
    @DisplayName("自建会话脚本只用 ES5：无箭头函数 / let / const / 可选链 / 模板串")
    void scriptStaysEs5() {
        String script = QrLoginBridge.selfQrScript();
        assertFalse(Pattern.compile("=>").matcher(script).find(), "箭头函数在旧内核里直接语法错误");
        assertFalse(Pattern.compile("\\blet\\s").matcher(script).find(), "let 不被 ES5 内核支持");
        assertFalse(Pattern.compile("\\bconst\\s").matcher(script).find(), "const 不被 ES5 内核支持");
        assertFalse(script.contains("?."), "可选链会让整段脚本不执行");
        assertFalse(script.contains("`"), "模板字符串不被 ES5 内核支持");
    }

    @Test
    @DisplayName("自建会话确实出码并轮询，且结果写回主流程读取的槽位")
    void scriptWiresQrAndPoll() {
        String script = QrLoginBridge.selfQrScript();
        assertTrue(script.contains("/passport/web/get_qrcode/"), "必须自己请求出码");
        assertTrue(script.contains("/passport/web/check_qrconnect/"), "必须自己轮询扫码状态");
        assertTrue(script.contains("window.__dyQr = text"), "二维码要写回主流程读的 __dyQr");
        assertTrue(script.contains("window.__dyPoll = text"), "轮询响应要写回 __dyPoll");
        // 自建会话出码后要立标记，钩子据此停止用页面响应覆盖，避免两套 token 交错。
        assertTrue(script.contains("window.__dySelfQr = true"), "出码后必须标记已由自建会话接管");
        assertTrue(QrLoginBridge.HOOK_SCRIPT.contains("if (window.__dySelfQr) return;"),
                "钩子必须让位给已出码的自建会话");
        assertTrue(script.contains("redirect_url"), "确认后要跟随服务端下发的跳转以落登录态");
        // 幂等：重复注入（重载页面后再次调用）不能叠加出两套轮询。
        assertTrue(script.contains("if (window.__dySelf) return;"), "重复注入必须直接返回");
    }
}
