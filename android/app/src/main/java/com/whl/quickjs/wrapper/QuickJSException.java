package com.whl.quickjs.wrapper;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes2.dex */
public class QuickJSException extends RuntimeException {
    private final boolean jsError;

    public QuickJSException(String str, boolean z) {
        super(str);
        this.jsError = z;
    }

    public boolean isJSError() {
        return this.jsError;
    }

    public QuickJSException(String str) {
        this(str, false);
    }
}
