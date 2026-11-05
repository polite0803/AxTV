/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetMediaActionReceiver.java (41 行)
 * 原职责: 接收 ACTION_MEDIA 广播，读 EXTRA_KEY_CODE，>=0 时把按键 post 到当前 Activity 的 decorView
 * 重构说明: 逐字还原 action / extra key / 默认值 -1 / >=0 判定；
 *           「post 到 decorView」用 WidgetEnvironment.MediaSink 接口隔离（原依赖 kx2 全局 Activity 引用）。
 */
package axtv.widget;

/** 媒体键广播接收逻辑。 */
public final class WidgetMediaActionReceiver {

    /** 广播 action（:13）。 */
    public static final String ACTION_MEDIA = "com.github.axtv.dv.widget.MEDIA";
    /** extra key（:14）。 */
    public static final String EXTRA_KEY_CODE = "key_code";
    /** getIntExtra 默认值（:22）。 */
    public static final int KEY_CODE_DEFAULT = -1;

    private final WidgetEnvironment env;

    public WidgetMediaActionReceiver(WidgetEnvironment env) {
        this.env = env;
    }

    /** 对应 onReceive：keyCode>=0 才投递。 */
    public void onReceive(int keyCode) {
        if (keyCode >= 0) {
            env.mediaSink.dispatchMediaKey(keyCode);
        }
    }

    /** 便捷入口：直接消费一个 MEDIA_KEY 动作。 */
    public void onReceive(WidgetAction action) {
        if (action != null && action.type() == WidgetAction.Type.MEDIA_KEY) {
            onReceive(action.keyCode());
        }
    }
}
