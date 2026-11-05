package com.whl.quickjs.wrapper;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes.dex */
public interface JSObjectCreator {
    JSArray newArray(QuickJSContext quickJSContext, long j);

    JSFunction newFunction(QuickJSContext quickJSContext, long j, long j2, int i);

    JSObject newObject(QuickJSContext quickJSContext, long j);
}
