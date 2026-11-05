package com.whl.quickjs.wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes.dex */
public interface JSObject {
    void decrementRefCount();

    Boolean getBoolean(String str);

    @Deprecated
    Boolean getBooleanProperty(String str);

    byte[] getBytes(String str);

    QuickJSContext getContext();

    Double getDouble(String str);

    @Deprecated
    Double getDoubleProperty(String str);

    @Deprecated
    Integer getIntProperty(String str);

    Integer getInteger(String str);

    JSArray getJSArray(String str);

    @Deprecated
    JSArray getJSArrayProperty(String str);

    JSFunction getJSFunction(String str);

    @Deprecated
    JSFunction getJSFunctionProperty(String str);

    JSObject getJSObject(String str);

    @Deprecated
    JSObject getJSObjectProperty(String str);

    Long getLong(String str);

    JSArray getNames();

    @Deprecated
    JSArray getOwnPropertyNames();

    long getPointer();

    Object getProperty(String str);

    int getRefCount();

    Throwable getStackTrace();

    String getString(String str);

    @Deprecated
    String getStringProperty(String str);

    void hold();

    boolean isAlive();

    boolean isRefCountZero();

    void release();

    void setProperty(String str, double d);

    void setProperty(String str, int i);

    void setProperty(String str, long j);

    void setProperty(String str, JSCallFunction jSCallFunction);

    void setProperty(String str, JSObject jSObject);

    void setProperty(String str, Class<?> cls);

    void setProperty(String str, String str2);

    void setProperty(String str, boolean z);

    void setProperty(String str, byte[] bArr);

    void setStackTrace(Throwable th);

    String stringify();

    ArrayList<Object> toArray();

    ArrayList<Object> toArray(MapFilter mapFilter);

    ArrayList<Object> toArray(MapFilter mapFilter, Object obj, MapCreator mapCreator);

    HashMap<String, Object> toMap();

    HashMap<String, Object> toMap(MapFilter mapFilter);

    Map<String, Object> toMap(MapFilter mapFilter, Object obj, MapCreator mapCreator);
}
