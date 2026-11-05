package com.whl.quickjs.wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes2.dex */
public class QuickJSArray extends QuickJSObject implements JSArray {
    public QuickJSArray(QuickJSContext quickJSContext, long j) {
        super(quickJSContext, j);
    }

    @Override // com.whl.quickjs.wrapper.JSArray
    public Object get(int i) {
        checkRefCountIsZero();
        return getContext().get(this, i);
    }

    @Override // com.whl.quickjs.wrapper.JSArray
    public int length() {
        checkRefCountIsZero();
        return getContext().length(this);
    }

    @Override // com.whl.quickjs.wrapper.JSArray
    public void set(Object obj, int i) {
        checkRefCountIsZero();
        getContext().set(this, obj, i);
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray(MapFilter mapFilter, Object obj, MapCreator mapCreator) {
        ArrayList<Object> arrayList = new ArrayList<>(length());
        HashMap<Long, Object> map = new HashMap<>();
        convertToMap(this, arrayList, map, mapFilter, obj, mapCreator);
        map.clear();
        return arrayList;
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public HashMap<String, Object> toMap(MapFilter mapFilter) {
        return (HashMap) toMap(mapFilter, null, null);
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public HashMap<String, Object> toMap() {
        return toMap(null);
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public Map<String, Object> toMap(MapFilter mapFilter, Object obj, MapCreator mapCreator) {
        throw new UnsupportedOperationException("Array types are not yet supported for conversion to map. You should use toArray.");
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray(MapFilter mapFilter) {
        // 反编译把第三参（MapCreator）错标为 p000.hn3；默认实现传 null。
        return toArray(mapFilter, null, null);
    }

    @Override // com.whl.quickjs.wrapper.QuickJSObject, com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray() {
        return toArray(null);
    }
}
