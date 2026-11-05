package com.whl.quickjs.android;

import android.util.Log;
import com.whl.quickjs.wrapper.QuickJSContext;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes.dex */
public final class QuickJSLoader {

    /* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
    /* JADX INFO: loaded from: classes2.dex */
    public static final class LogcatConsole implements QuickJSContext.Console {
        private final String tag;

        public LogcatConsole(String str) {
            this.tag = str;
        }

        @Override // com.whl.quickjs.wrapper.QuickJSContext.Console
        public void error(String str) {
            Log.e(this.tag, str);
        }

        @Override // com.whl.quickjs.wrapper.QuickJSContext.Console
        public void info(String str) {
            Log.i(this.tag, str);
        }

        @Override // com.whl.quickjs.wrapper.QuickJSContext.Console
        public void log(String str) {
            Log.d(this.tag, str);
        }

        @Override // com.whl.quickjs.wrapper.QuickJSContext.Console
        public void warn(String str) {
            Log.w(this.tag, str);
        }
    }

    public static void init() {
        System.loadLibrary("quickjs-android-wrapper");
    }

    @Deprecated
    public static void initConsoleLog(QuickJSContext quickJSContext) {
        initConsoleLog(quickJSContext, new LogcatConsole("quickjs"));
    }

    public static native void startRedirectingStdoutStderr(String str);

    @Deprecated
    public static void initConsoleLog(QuickJSContext quickJSContext, QuickJSContext.Console console) {
        quickJSContext.setConsole(console);
    }

    @Deprecated
    public static void initConsoleLog(QuickJSContext quickJSContext, String str) {
        initConsoleLog(quickJSContext, new LogcatConsole(str));
    }
}
