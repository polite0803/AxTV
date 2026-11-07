/*
 * 扫码登录注入脚本（从 QrLoginBridge 拆出，脚本原文逐字保留）。
 * 拆分原因: QrLoginBridge 超 800 行；脚本常量与流程状态机无耦合。
 */
package axtv.panel;

/** QrLoginBridge 注入页面的全部 JS 脚本与自建会话常量。 */
final class QrLoginScripts {

    private QrLoginScripts() {
    }

    /** 页面注入脚本（原 sy3，逐字保留：钩住 XHR/fetch 记录二维码与轮询响应）。 */
    public static final String HOOK_SCRIPT = """
            (function(){
              if (window.__dyHook) return;
              window.__dyHook = true;
              window.__dyQr = null;
              window.__dyPoll = null;
              window.__dyPollAt = 0;
              function save(url, text) {
                try {
                  // 自建会话已经拿到码时，页面自己的响应不再覆盖：两套 token 交错会让界面
                  // 显示一张没人轮询的旧码。自建会话还没出码前仍接受页面响应，避免两边都空。
                  if (window.__dySelfQr) return;
                  if (url.indexOf('/passport/web/get_qrcode/') >= 0) window.__dyQr = text;
                  else if (url.indexOf('/passport/web/check_qrconnect/') >= 0) {
                    window.__dyPoll = text;
                    window.__dyPollAt = Date.now();
                  }
                } catch (e) {}
              }
              var open = XMLHttpRequest.prototype.open;
              var send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url) {
                try { this.__dyUrl = String(url || ''); } catch (e) {}
                return open.apply(this, arguments);
              };
              XMLHttpRequest.prototype.send = function() {
                var xhr = this;
                try {
                  xhr.addEventListener('load', function() {
                    var text = '';
                    try { text = xhr.responseText; } catch (e) {
                      try { text = JSON.stringify(xhr.response); } catch (e2) { text = ''; }
                    }
                    save(xhr.__dyUrl, text);
                  });
                } catch (e) {}
                return send.apply(this, arguments);
              };
              if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function(input) {
                  var url = '';
                  try { url = typeof input === 'string' ? input : ((input && input.url) || ''); } catch (e) {}
                  return origFetch.apply(this, arguments).then(function(res) {
                    try { res.clone().text().then(function(t) { save(url, t); }); } catch (e) {}
                    return res;
                  });
                };
              }
            })()
            """;

    /**
     * 读快照脚本：钩子抓到的二维码/轮询响应，外加页面 DOM 探针。
     *
     * <p>二次验证可能以三种形式出现，探针都要能看见：
     * <ul>
     *   <li>顶层 DOM：{@code #uc-second-verify} 或正文含「身份验证 / 安全验证 / 滑块 …」；</li>
     *   <li>同源 iframe：验证组件常被挂进 iframe，父文档 textContent 里一个字都没有；</li>
     *   <li>整页跳转到验证页：此时 DOM 可能干净，但 {@code location.href} 会带 verify/captcha。</li>
     * </ul>
     * {@code codeInput} 只在屏幕上真的渲染了 placeholder 含「验证码」的输入框时为 true
     *（隐藏 tab 里的 input 也有非零 rect，必须用 elementFromPoint 验一次），
     * 命中才由应用侧数字键盘代填。
     */
    public static final String SNAPSHOT_SCRIPT = """
            (function(){
              try {
                var modal = document.querySelector('.douyin_login_new_class');
                var text = modal ? (modal.textContent || '') : '';
                var open = false;
                if (modal) {
                  var cs = getComputedStyle(modal);
                  var mr = modal.getBoundingClientRect();
                  open = cs.display !== 'none' && cs.visibility !== 'hidden' && mr.width > 0 && mr.height > 0;
                }
                var pageText = '';
                try { pageText = document.documentElement ? (document.documentElement.textContent || '') : ''; } catch (e0) {}
                var frameText = '';
                try {
                  var frames = document.querySelectorAll('iframe');
                  for (var fi = 0; fi < frames.length; fi++) {
                    try {
                      var fdoc = frames[fi].contentDocument;
                      if (fdoc && fdoc.body) frameText += ' ' + (fdoc.body.textContent || '');
                    } catch (e8) {}
                  }
                } catch (e7) {}
                var allText = pageText + ' ' + frameText;
                function has(s) { return allText.indexOf(s) >= 0; }
                var codeInput = false;
                try {
                  var ins = document.querySelectorAll('input');
                  for (var ci = 0; ci < ins.length; ci++) {
                    var cit = ins[ci];
                    if ((cit.getAttribute('placeholder') || '').indexOf('验证码') < 0) continue;
                    var cr = cit.getBoundingClientRect();
                    if (cr.width < 20 || cr.height < 12) continue;
                    var ccs = getComputedStyle(cit);
                    if (ccs.display === 'none' || ccs.visibility === 'hidden' || ccs.opacity === '0') continue;
                    var chit = document.elementFromPoint(cr.x + cr.width / 2, cr.y + cr.height / 2);
                    if (chit && (chit === cit || cit.contains(chit))) { codeInput = true; break; }
                  }
                } catch (e9) {}
                // 原版 ez3.m3955b 的关键兜底：页面自己的 XHR 钩子可能拿不到码/拿到坏响应，
                // 但登录弹窗里那张 data:image 二维码是可以直接从 DOM 取的。老内核上这条路径出码最快。
                var domQr = null;
                try {
                  var qrHost = modal || document;
                  var imgs = [].slice.call(qrHost.querySelectorAll('img')).filter(function(img) {
                    var w = img.naturalWidth, h = img.naturalHeight;
                    return img.src && img.src.indexOf('data:image') === 0 &&
                           w >= 200 && Math.abs(w - h) <= w * 0.1 && img.src.length > 5000;
                  });
                  imgs.sort(function(a, b) {
                    return b.naturalWidth * b.naturalHeight - a.naturalWidth * a.naturalHeight;
                  });
                  domQr = imgs.length ? imgs[0].src : null;
                } catch (e10) {}
                return JSON.stringify({
                  qr: window.__dyQr || null,
                  domQr: domQr,
                  poll: window.__dyPoll || null,
                  pollAt: window.__dyPollAt || 0,
                  url: location.href,
                  selfQr: !!window.__dySelfQr,
                  open: open,
                  verify: !!(document.getElementById('uc-second-verify') ||
                             has('身份验证') || has('安全验证') || has('二次验证') || has('人机验证') ||
                             has('请完成验证') || has('拖动滑块') || has('滑块验证') ||
                             has('短信验证') || has('获取验证码')),
                  compat: has('不太兼容当前平台') || has('建议尽快升级') ||
                          (has('Edge 浏览器') && has('Chrome 浏览器')),
                  codeInput: codeInput,
                  scanned: text.indexOf('已扫码') >= 0 || text.indexOf('请在手机端确认') >= 0 ||
                           text.indexOf('需在手机上进行确认') >= 0 || text.indexOf('扫描成功') >= 0 ||
                           text.indexOf('扫码成功') >= 0,
                  expired: text.indexOf('二维码已过期') >= 0 || text.indexOf('二维码失效') >= 0 ||
                           text.indexOf('已失效') >= 0
                });
              } catch (e) { return JSON.stringify({error:String(e)}); }
            })()
            """;

    /**
     * 点掉抖音创作者中心的「你的浏览器可能不太兼容当前平台…」提示。
     *
     * <p>那张弹窗里只有 Edge / Chrome 的下载引导图标，电视上点了没反应，而且它盖在登录入口上：
     * 不点掉的话用户看到的就是一个点不动的窗口，登录/验证流程也进不去。
     * 只点「我知道了 / 不再提醒 / 继续访问」这类按钮，不做删除 DOM 的破坏性操作。
     */
    public static final String DISMISS_COMPAT_SCRIPT = """
            (function(){
              try {
                var texts = ['我知道了', '知道了', '不再提醒', '继续访问', '仍要访问'];
                var els = document.querySelectorAll('button,div,span,a');
                for (var i = 0; i < els.length; i++) {
                  var el = els[i];
                  var t = (el.textContent || '').trim();
                  if (texts.indexOf(t) < 0) continue;
                  var r = el.getBoundingClientRect();
                  if (r.width < 30 || r.height < 16) continue;
                  var hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
                  if (!hit || !(hit === el || el.contains(hit))) continue;
                  try { el.click(); } catch (e1) {}
                  // 有些按钮只对 pointer 序列响应，补一套合成指针事件（与验证按钮同款处理）。
                  try {
                    ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(function(type) {
                      var ev = type.indexOf('pointer') === 0
                        ? new PointerEvent(type, { bubbles: true, cancelable: true, isPrimary: true })
                        : new MouseEvent(type, { bubbles: true, cancelable: true });
                      el.dispatchEvent(ev);
                    });
                  } catch (e2) {}
                  return 'clicked:' + t;
                }
                return 'not-found';
              } catch (e) { return 'err:' + e; }
            })()
            """;

    /**
     * 打开登录页里的登录入口（仅在页面还没有登录弹窗时）。
     *
     * <p>二维码可能是「自建会话」出的（页面自己的登录弹窗从未打开），此时页面根本不在登录流程里，
     * 手机上确认后网页也不可能弹出验证窗口。用户手动打开登录页、或手机已确认却拿不到登录态时，
     * 先把页面的登录弹窗点出来，验证/确认流程才有地方渲染。
     * 只在弹窗不可见、且找不到可见的「登录」入口时才返回提示字符串，不做任何猜测性点击。
     */
    public static final String OPEN_LOGIN_MODAL_SCRIPT = """
            (function(){
              try {
                var modal = document.querySelector('.douyin_login_new_class');
                if (modal) {
                  var cs = getComputedStyle(modal);
                  var mr = modal.getBoundingClientRect();
                  if (cs.display !== 'none' && cs.visibility !== 'hidden' && mr.width > 0 && mr.height > 0) {
                    // 弹窗已开但没抓到二维码：新版登录弹窗默认停在「验证码登录」tab，
                    // 不切到「扫码登录」就永远不请求 get_qrcode（旧版默认就是二维码 tab，
                    // 这是页面改版后"弹窗开了却没码"的根因）。
                    if (!window.__dyQr) {
                      var tabs = modal.querySelectorAll('div,span,button,a');
                      for (var ti = 0; ti < tabs.length; ti++) {
                        var tab = tabs[ti];
                        var tt = (tab.textContent || '').trim();
                        if (tt !== '扫码登录' && tt !== '二维码登录') continue;
                        var tr = tab.getBoundingClientRect();
                        if (tr.width < 20 || tr.height < 12) continue;
                        var thit = document.elementFromPoint(tr.x + tr.width / 2, tr.y + tr.height / 2);
                        if (!thit || !(thit === tab || tab.contains(thit))) continue;
                        try { tab.click(); } catch (e2) {}
                        return 'tab:' + tt;
                      }
                    }
                    return 'already-open';
                  }
                }
                var texts = ['登录', '立即登录', '扫码登录', '登录/注册', '登录注册'];
                var els = document.querySelectorAll('button,a,div,span');
                for (var i = 0; i < els.length; i++) {
                  var el = els[i];
                  var t = (el.textContent || '').trim();
                  // 入口文案各版本不一致（登录 / 立即登录 / 扫码登录…），短文案里含"登录"即可。
                  var matched = texts.indexOf(t) >= 0 || (t.indexOf('登录') >= 0 && t.length <= 8);
                  if (!matched) continue;
                  var r = el.getBoundingClientRect();
                  if (r.width < 40 || r.height < 20) continue;
                  var hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
                  if (!hit || !(hit === el || el.contains(hit))) continue;
                  try { el.click(); } catch (e1) {}
                  return 'clicked:' + t;
                }
                return 'no-entry';
              } catch (e) { return 'err:' + e; }
            })()
            """;

    /**
     * 把验证码写进页面上那个真正渲染的输入框（原版 {@code eg1} 的注入 JS，逐字保留）。
     *
     * <p>必须走原生 value setter：直接赋值 {@code el.value = ...} 不会触发 React 受控组件的
     * onChange，页面拿不到值。{@code __CODE__} 由 {@link #requestCodeSubmission} 用
     * 已过滤成纯数字的验证码替换，不存在注入面。
     */
    static final String CODE_FILL_SCRIPT = """
            (function(code){
              try {
                var ins = document.querySelectorAll('input');
                var el = null;
                for (var i = 0; i < ins.length; i++) {
                  var it = ins[i];
                  if ((it.getAttribute('placeholder') || '').indexOf('验证码') < 0) continue;
                  var r = it.getBoundingClientRect();
                  if (r.width < 20 || r.height < 12) continue;
                  var hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
                  if (hit && (hit === it || it.contains(hit))) { el = it; break; }
                }
                if (!el) return 'no-input';
                el.focus();
                var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                setter.call(el, code);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'ok';
              } catch (e) { return 'err:' + e; }
            })("__CODE__")
            """;

    /** 点掉离验证码输入框最近的那个「验证/登录/确认/确定/提交/下一步」按钮（原版 {@code eg1}，逐字保留）。 */
    static final String CODE_CLICK_SCRIPT = """
            (function(){
              try {
                var input = null;
                var ins = document.querySelectorAll('input');
                for (var i = 0; i < ins.length; i++) {
                  var it = ins[i];
                  if ((it.getAttribute('placeholder') || '').indexOf('验证码') < 0) continue;
                  var r = it.getBoundingClientRect();
                  if (r.width < 20 || r.height < 12) continue;
                  var hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
                  if (hit && (hit === it || it.contains(hit))) { input = r; break; }
                }
                if (!input) return JSON.stringify({ clicked: false, reason: 'no-input' });
                var texts = ['验证', '登录', '确认', '确定', '提交', '下一步'];
                var best = null;
                var sawDisabled = false;
                var els = document.querySelectorAll('button,div,span,a');
                for (var j = 0; j < els.length; j++) {
                  var e = els[j];
                  var t = (e.textContent || '').trim();
                  if (texts.indexOf(t) < 0) continue;
                  var r2 = e.getBoundingClientRect();
                  if (r2.width < 40 || r2.height < 20) continue;
                  var hit2 = document.elementFromPoint(r2.x + r2.width / 2, r2.y + r2.height / 2);
                  if (!hit2 || !(hit2 === e || e.contains(hit2))) continue;
                  if (r2.y < input.y) continue;
                  var btn = e.closest ? e.closest('button,[role=button]') : null;
                  if ((btn && btn.disabled) || e.getAttribute('aria-disabled') === 'true') {
                    sawDisabled = true;
                    continue;
                  }
                  var dy = r2.y - input.y;
                  var rank = (t === '验证' ? 0 : 1);
                  if (!best || dy < best.dy - 5 || (Math.abs(dy - best.dy) <= 5 && rank < best.rank)) {
                    best = { e: e, dy: dy, rank: rank };
                  }
                }
                if (!best) return JSON.stringify({ clicked: false, reason: sawDisabled ? 'disabled' : 'no-button' });
                var el = best.e;
                try { el.click(); } catch (e1) {}
                // 有些按钮只对 pointer 序列响应，再补一套合成指针事件（原版注释）
                try {
                  ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(function(type) {
                    var ev = type.indexOf('pointer') === 0
                      ? new PointerEvent(type, { bubbles: true, cancelable: true, isPrimary: true })
                      : new MouseEvent(type, { bubbles: true, cancelable: true });
                    el.dispatchEvent(ev);
                  });
                } catch (e2) {}
                return JSON.stringify({ clicked: true, text: (el.textContent || '').trim() });
              } catch (e) { return JSON.stringify({ clicked: false, reason: String(e) }); }
            })()
            """;

    /**
     * 出码参数：真实登录页请求 get_qrcode / check_qrconnect 时用的那一套（aid=2906 + request_host）。
     * 缺 request_host 或 aid 换成 6383 都会被风控判成 4031，逐字保留。
     */
    static final String SELF_QR_PARAMS =
            "passport_jssdk_version=3.4.17&passport_jssdk_type=normal&is_from_ttaccountsdk=1"
                    + "&aid=2906&language=zh&account_app_language=zh-CN&ts=1793880000"
                    + "&next=https%3A%2F%2Fcreator.douyin.com%2F&need_short_url=true&need_logo=false"
                    + "&is_new_login=1&is_from_iesaccountsaas=1&p_ui=2.4.19&account_sdk_source=web"
                    + "&p_js_v=3.4.17&p_js_t=pro&p_zt=3.3.34&p_ver=1.1.4-beta.5&p_ver_real=0"
                    + "&request_host=https%253A%252F%252Fcreator.douyin.com&p_bd=1.0.1.16";

    /**
     * 页面自身不出码时的自建会话脚本。
     *
     * <p>电视盒子的 WebView 内核常比手机落后好几代，登录页的 UMD 包跑不起来时，页面既不会请求
     * get_qrcode、也不会轮询，界面只能等到「二维码生成超时」。本脚本只用 ES5 + XMLHttpRequest
     * （不依赖 fetch/Promise/箭头函数），在同一页面内自己出码并轮询，结果仍写回
     * {@code __dyQr/__dyPoll}，主流程无需区分来源。
     *
     * <p>已实测：同源请求出码与轮询都不需要 sign/a_bogus，只要 aid=2906 与 request_host 正确。
     */
    public static final String SELF_QR_SCRIPT =
            "(function(){\n"
                    + "  if (window.__dySelf) return;\n"
                    + "  window.__dySelf = true;\n"
                    + "  var P = '" + SELF_QR_PARAMS + "';\n"
                    + "  var token = null;\n"
                    + "  var confirmedAt = 0;\n"
                    + "  var busy = false;\n"
                    + "  function request(method, url, body, done) {\n"
                    + "    try {\n"
                    + "      var xhr = new XMLHttpRequest();\n"
                    + "      xhr.open(method, url, true);\n"
                    + "      xhr.withCredentials = true;\n"
                    + "      if (body) xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');\n"
                    + "      xhr.onreadystatechange = function(){\n"
                    + "        if (xhr.readyState !== 4) return;\n"
                    + "        try { done(xhr.responseText); } catch (e) {}\n"
                    + "      };\n"
                    + "      xhr.send(body || null);\n"
                    + "    } catch (e) {}\n"
                    + "  }\n"
                    + "  function getQr() {\n"
                    + "    request('GET', '/passport/web/get_qrcode/?' + P, null, function(text){\n"
                    + "      var t = null;\n"
                    + "      try { t = JSON.parse(text).data.token; } catch (e) {}\n"
                    + "      if (!t) return;\n"
                    + "      window.__dyQr = text;\n"
                    + "      window.__dySelfQr = true;\n"
                    + "      token = t;\n"
                    + "    });\n"
                    + "  }\n"
                    + "  function poll() {\n"
                    + "    if (busy || confirmedAt) return;\n"
                    + "    if (!token) { getQr(); return; }\n"
                    + "    busy = true;\n"
                    + "    request('POST', '/passport/web/check_qrconnect/?' + P, 'token=' + encodeURIComponent(token), function(text){\n"
                    + "      busy = false;\n"
                    + "      window.__dyPoll = text;\n"
                    + "      window.__dyPollAt = new Date().getTime();\n"
                    + "      var d = null;\n"
                    + "      try { d = JSON.parse(text).data; } catch (e) { return; }\n"
                    + "      if (!d) return;\n"
                    + "      if (d.status === 'confirmed') {\n"
                    + "        confirmedAt = new Date().getTime();\n"
                    + "        window.location.href = d.redirect_url ? d.redirect_url : '/';\n"
                    + "      } else if (d.status === 'expired' || d.status === 'refused') {\n"
                    + "        token = null;\n"
                    + "      }\n"
                    + "    });\n"
                    + "  }\n"
                    + "  getQr();\n"
                    + "  setInterval(poll, 2000);\n"
                    + "})()\n";

    /** 自建会话的出码参数（测试用；生产代码直接引用常量）。 */
    public static String selfQrParams() {
        return SELF_QR_PARAMS;
    }

    /** 自建会话脚本（测试用；生产代码直接引用常量）。 */
    public static String selfQrScript() {
        return SELF_QR_SCRIPT;
    }
}
