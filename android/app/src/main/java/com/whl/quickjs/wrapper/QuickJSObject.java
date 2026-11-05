package com.whl.quickjs.wrapper;

import com.whl.quickjs.wrapper.QuickJSObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes2.dex */
public class QuickJSObject implements JSObject {
    private final QuickJSContext context;
    private final long pointer;
    private int refCount;
    private Throwable stackTrace;

    public QuickJSObject(QuickJSContext quickJSContext, long j) {
        this.context = quickJSContext;
        this.pointer = j;
        this.refCount++;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Object lambda$setProperty$0(Method method, Object obj, Object[] objArr) {
        try {
            return method.invoke(obj, objArr);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        } catch (InvocationTargetException e2) {
            e2.printStackTrace();
            return null;
        }
    }

    private void setPropertyObject(String str, Object obj) {
        checkRefCountIsZero();
        this.context.setProperty(this, str, obj);
    }

    public final void checkRefCountIsZero() {
        if (isRefCountZero()) {
            throw new QuickJSException("The call threw an exception, the reference count of the current object has already reached zero.");
        }
    }

    /* JADX WARN: Code duplicated, block: B:27:0x008c  */
    /* JADX WARN: Code duplicated, block: B:29:0x0090  */
    /* JADX WARN: Code duplicated, block: B:30:0x0097  */
    /* JADX WARN: Code duplicated, block: B:32:0x009b  */
    /* JADX WARN: Code duplicated, block: B:34:0x00b2  */
    /* JADX WARN: Code duplicated, block: B:35:0x00b9  */
    /* JADX WARN: Code duplicated, block: B:37:0x00bd  */
    /* JADX WARN: Code duplicated, block: B:39:0x00c7 A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:40:0x00c9  */
    /* JADX WARN: Code duplicated, block: B:42:0x00de  */
    /* JADX WARN: Code duplicated, block: B:43:0x00e5  */
    /* JADX WARN: Code duplicated, block: B:45:0x00e9  */
    /* JADX WARN: Code duplicated, block: B:47:0x00f5  */
    /* JADX WARN: Code duplicated, block: B:49:0x00f9  */
    /* JADX WARN: Code duplicated, block: B:50:0x0100  */
    /* JADX WARN: Code duplicated, block: B:52:0x0104  */
    /* JADX WARN: Code duplicated, block: B:66:0x010a A[SYNTHETIC] */
    public void convertToMap(Object obj, Object obj2, HashMap<Long, Object> map, MapFilter mapFilter, Object obj3, MapCreator mapCreator) {
        QuickJSObject quickJSObject;
        String str;
        Object property;
        Map<String, Object> map2;
        ArrayList arrayList;
        HashMap<Long, Object> map3 = map;
        JSObject jSObject = (JSObject) obj;
        map3.put(Long.valueOf(jSObject.getPointer()), obj2);
        boolean z = obj instanceof JSArray;
        JSArray names = z ? (JSArray) obj : jSObject.getNames();
        int length = names.length();
        int i = 0;
        while (i < length) {
            if (z) {
                str = null;
                property = names.get(i);
                quickJSObject = this;
            } else {
                String str2 = (String) names.get(i);
                quickJSObject = this;
                if (mapFilter != null && mapFilter.shouldSkipKey(str2, quickJSObject.pointer, obj3)) {
                    // 反编译丢失 continue，导致 i 双递增且 str/property 可能未初始化。
                    i++;
                    continue;
                }
                str = str2;
                property = jSObject.getProperty(str2);
            }
            boolean z2 = property instanceof JSObject;
            if (z2) {
                long pointer = ((JSObject) property).getPointer();
                if (map3.containsKey(Long.valueOf(pointer))) {
                    Object obj4 = map3.get(Long.valueOf(pointer));
                    if (obj2 instanceof Map) {
                        ((Map) obj2).put(str, obj4);
                    } else if (obj2 instanceof ArrayList) {
                        ((ArrayList) obj2).add(obj4);
                    }
                } else if (property instanceof JSFunction) {
                    ((JSFunction) property).release();
                } else if (property instanceof JSArray) {
                    JSArray jSArray = (JSArray) property;
                    arrayList = new ArrayList(jSArray.length());
                    quickJSObject.convertToMap(property, arrayList, map3, mapFilter, obj3, mapCreator);
                    if (obj2 instanceof Map) {
                        ((Map) obj2).put(str, arrayList);
                    } else if (obj2 instanceof ArrayList) {
                        ((ArrayList) obj2).add(arrayList);
                    }
                    jSArray.release();
                } else if (z2) {
                    map2 = mapCreator.get();
                    convertToMap(property, map2, map, mapFilter, obj3, mapCreator);
                    if (obj2 instanceof Map) {
                        ((Map) obj2).put(str, map2);
                    } else if (obj2 instanceof ArrayList) {
                        ((ArrayList) obj2).add(map2);
                    }
                    ((JSObject) property).release();
                } else if (obj2 instanceof Map) {
                    ((Map) obj2).put(str, property);
                } else if (obj2 instanceof ArrayList) {
                    ((ArrayList) obj2).add(property);
                }
            } else if (property instanceof JSFunction) {
                ((JSFunction) property).release();
            } else if (property instanceof JSArray) {
                JSArray jSArray2 = (JSArray) property;
                arrayList = new ArrayList(jSArray2.length());
                quickJSObject.convertToMap(property, arrayList, map3, mapFilter, obj3, mapCreator);
                if (obj2 instanceof Map) {
                    ((Map) obj2).put(str, arrayList);
                } else if (obj2 instanceof ArrayList) {
                    ((ArrayList) obj2).add(arrayList);
                }
                jSArray2.release();
            } else if (z2) {
                map2 = mapCreator.get();
                convertToMap(property, map2, map, mapFilter, obj3, mapCreator);
                if (obj2 instanceof Map) {
                    ((Map) obj2).put(str, map2);
                } else if (obj2 instanceof ArrayList) {
                    ((ArrayList) obj2).add(map2);
                }
                ((JSObject) property).release();
            } else if (obj2 instanceof Map) {
                ((Map) obj2).put(str, property);
            } else if (obj2 instanceof ArrayList) {
                ((ArrayList) obj2).add(property);
            }
            i++;
            map3 = map;
        }
        if (z) {
            return;
        }
        names.release();
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void decrementRefCount() {
        checkRefCountIsZero();
        this.refCount--;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Boolean getBoolean(String str) {
        Object property = getProperty(str);
        if (property instanceof Boolean) {
            return (Boolean) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Boolean getBooleanProperty(String str) {
        return getBoolean(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public byte[] getBytes(String str) {
        Object property = getProperty(str);
        if (property instanceof byte[]) {
            return (byte[]) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public QuickJSContext getContext() {
        return this.context;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Double getDouble(String str) {
        Object property = getProperty(str);
        if (property instanceof Double) {
            return (Double) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Double getDoubleProperty(String str) {
        return getDouble(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Integer getIntProperty(String str) {
        return getInteger(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Integer getInteger(String str) {
        Object property = getProperty(str);
        if (property instanceof Integer) {
            return (Integer) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSArray getJSArray(String str) {
        Object property = getProperty(str);
        if (property instanceof JSArray) {
            return (JSArray) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSArray getJSArrayProperty(String str) {
        return getJSArray(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSFunction getJSFunction(String str) {
        Object property = getProperty(str);
        if (property instanceof JSFunction) {
            return (JSFunction) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSFunction getJSFunctionProperty(String str) {
        return getJSFunction(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSObject getJSObject(String str) {
        Object property = getProperty(str);
        if (property instanceof JSObject) {
            return (JSObject) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSObject getJSObjectProperty(String str) {
        return getJSObject(str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Long getLong(String str) {
        Object property = getProperty(str);
        if (property instanceof Long) {
            return (Long) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSArray getNames() {
        checkRefCountIsZero();
        return (JSArray) this.context.getOwnPropertyNames(this);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public JSArray getOwnPropertyNames() {
        return getNames();
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public long getPointer() {
        return this.pointer;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Object getProperty(String str) {
        checkRefCountIsZero();
        return this.context.getProperty(this, str);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public int getRefCount() {
        return this.refCount;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Throwable getStackTrace() {
        return this.stackTrace;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public String getString(String str) {
        Object property = getProperty(str);
        if (property instanceof String) {
            return (String) property;
        }
        return null;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public String getStringProperty(String str) {
        return getString(str);
    }

    public int hashCode() {
        return Arrays.hashCode(new long[]{this.pointer});
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void hold() {
        checkRefCountIsZero();
        this.refCount++;
        this.context.hold(this);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public boolean isAlive() {
        return !isRefCountZero();
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public boolean isRefCountZero() {
        return this.refCount == 0;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void release() {
        if (isRefCountZero()) {
            return;
        }
        this.refCount--;
        this.context.freeValue(this);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, Class<?> cls) {
        Object objNewInstance;
        try {
            objNewInstance = cls.newInstance();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            objNewInstance = null;
        } catch (InstantiationException e2) {
            e2.printStackTrace();
            objNewInstance = null;
        }
        if (objNewInstance == null) {
            throw new IllegalArgumentException("The JavaObj cannot be null. An error occurred in newInstance!");
        }
        // 匿名内部类只能捕获 effectively final 变量；objNewInstance 在多个 catch 中赋值，故另存一份。
        final Object instance = objNewInstance;
        JSObject jSObjectCreateNewJSObject = this.context.createNewJSObject();
        for (final Method method : cls.getMethods()) {
            if (method.isAnnotationPresent(JSMethod.class)) {
                jSObjectCreateNewJSObject.setProperty(method.getName(), new JSCallFunction() { // from class: lz3
                    @Override // com.whl.quickjs.wrapper.JSCallFunction
                    public final Object call(Object[] objArr) {
                        return QuickJSObject.lambda$setProperty$0(method, instance, objArr);
                    }
                });
            }
        }
        setProperty(str, jSObjectCreateNewJSObject);
        jSObjectCreateNewJSObject.release();
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setStackTrace(Throwable th) {
        this.stackTrace = th;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public String stringify() {
        checkRefCountIsZero();
        return this.context.stringify(this);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray(MapFilter mapFilter, Object obj, MapCreator mapCreator) {
        throw new UnsupportedOperationException("Object types are not yet supported for conversion to array. You should use toMap.");
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public Map<String, Object> toMap(MapFilter mapFilter, Object obj, MapCreator mapCreator) {
        Map<String, Object> map = mapCreator.get();
        HashMap<Long, Object> map2 = new HashMap<>();
        convertToMap(this, map, map2, mapFilter, obj, mapCreator);
        map2.clear();
        return map;
    }

    public String toString() {
        checkRefCountIsZero();
        JSFunction jSFunction = getJSFunction("toString");
        String str = (String) jSFunction.call(new Object[0]);
        jSFunction.release();
        return str;
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray() {
        return toArray(null);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public ArrayList<Object> toArray(MapFilter mapFilter) {
        return toArray(mapFilter, null, null);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public HashMap<String, Object> toMap(MapFilter mapFilter) {
        // 反编译把第三参（MapCreator）错标为 p000.hn3；默认实现传 null，与 SDK 默认路径一致。
        return (HashMap) toMap(mapFilter, null, null);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public HashMap<String, Object> toMap() {
        return toMap(null);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, int i) {
        setPropertyObject(str, Integer.valueOf(i));
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, long j) {
        setPropertyObject(str, Long.valueOf(j));
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, JSObject jSObject) {
        setPropertyObject(str, jSObject);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, boolean z) {
        setPropertyObject(str, Boolean.valueOf(z));
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, double d) {
        setPropertyObject(str, Double.valueOf(d));
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, byte[] bArr) {
        setPropertyObject(str, bArr);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, JSCallFunction jSCallFunction) {
        setPropertyObject(str, jSCallFunction);
    }

    @Override // com.whl.quickjs.wrapper.JSObject
    public void setProperty(String str, String str2) {
        setPropertyObject(str, str2);
    }
}
