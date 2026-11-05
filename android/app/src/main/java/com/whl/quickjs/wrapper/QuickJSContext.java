package com.whl.quickjs.wrapper;

import com.whl.quickjs.wrapper.QuickJSContext;
import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes.dex */
public class QuickJSContext implements Closeable {
    private static final String UNKNOWN_FILE = "unknown.js";
    private final long context;
    private final JSObjectCreator creator;
    private final long currentThreadId;
    private JSObject globalObject;
    private LeakDetectionListener leakDetectionListener;
    private ModuleLoader moduleLoader;
    private final long runtime;
    private boolean destroyed = false;
    private final HashMap<Integer, JSCallFunction> callFunctionMap = new HashMap<>();
    private final List<JSObject> objectRecords = new ArrayList();
    private boolean enableStackTrace = false;

    /* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
    /* JADX INFO: loaded from: classes2.dex */
    public static abstract class BytecodeModuleLoader extends ModuleLoader {
        @Override // com.whl.quickjs.wrapper.ModuleLoader
        public String getModuleStringCode(String str) {
            return null;
        }

        @Override // com.whl.quickjs.wrapper.ModuleLoader
        public boolean isBytecodeMode() {
            return true;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
    /* JADX INFO: loaded from: classes2.dex */
    public interface Console {
        void error(String str);

        void info(String str);

        void log(String str);

        void warn(String str);
    }

    /* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
    /* JADX INFO: loaded from: classes2.dex */
    public static abstract class DefaultModuleLoader extends ModuleLoader {
        @Override // com.whl.quickjs.wrapper.ModuleLoader
        public byte[] getModuleBytecode(String str) {
            return null;
        }

        @Override // com.whl.quickjs.wrapper.ModuleLoader
        public boolean isBytecodeMode() {
            return false;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
    /* JADX INFO: loaded from: classes2.dex */
    public interface LeakDetectionListener {
        void notifyLeakDetected(JSObject jSObject, String str);
    }

    private QuickJSContext(final JSObjectCreator jSObjectCreator) {
        try {
            this.creator = new JSObjectCreator() { // from class: com.whl.quickjs.wrapper.QuickJSContext.2
                @Override // com.whl.quickjs.wrapper.JSObjectCreator
                public JSArray newArray(QuickJSContext quickJSContext, long j) {
                    JSArray jSArrayNewArray = jSObjectCreator.newArray(quickJSContext, j);
                    if (QuickJSContext.this.enableStackTrace) {
                        jSArrayNewArray.setStackTrace(new Throwable());
                    }
                    QuickJSContext.this.objectRecords.add(jSArrayNewArray);
                    return jSArrayNewArray;
                }

                @Override // com.whl.quickjs.wrapper.JSObjectCreator
                public JSFunction newFunction(QuickJSContext quickJSContext, long j, long j2, int i) {
                    JSFunction jSFunctionNewFunction = jSObjectCreator.newFunction(quickJSContext, j, j2, i);
                    if (QuickJSContext.this.enableStackTrace) {
                        jSFunctionNewFunction.setStackTrace(new Throwable());
                    }
                    QuickJSContext.this.objectRecords.add(jSFunctionNewFunction);
                    return jSFunctionNewFunction;
                }

                @Override // com.whl.quickjs.wrapper.JSObjectCreator
                public JSObject newObject(QuickJSContext quickJSContext, long j) {
                    JSObject jSObjectNewObject = jSObjectCreator.newObject(quickJSContext, j);
                    if (QuickJSContext.this.enableStackTrace) {
                        jSObjectNewObject.setStackTrace(new Throwable());
                    }
                    QuickJSContext.this.objectRecords.add(jSObjectNewObject);
                    return jSObjectNewObject;
                }
            };
            long jCreateRuntime = createRuntime();
            this.runtime = jCreateRuntime;
            this.context = createContext(jCreateRuntime);
            this.currentThreadId = Thread.currentThread().getId();
        } catch (UnsatisfiedLinkError unused) {
            throw new QuickJSException("The so library must be initialized before createContext! QuickJSLoader.init should be called on the Android platform. In the JVM, you need to manually call System.loadLibrary");
        }
    }

    private native Object call(long j, long j2, long j3, int i, Object[] objArr);

    private void checkDestroyed() {
        if (this.destroyed) {
            throw new QuickJSException("Can not called this after QuickJSContext was destroyed!");
        }
    }

    private void checkSameThread() {
        if (this.currentThreadId != Thread.currentThread().getId()) {
            throw new QuickJSException("Must be call same thread in QuickJSContext.create!");
        }
    }

    private native byte[] compile(long j, String str, String str2, boolean z);

    public static QuickJSContext create() {
        return new QuickJSContext(new JSObjectCreator() { // from class: com.whl.quickjs.wrapper.QuickJSContext.1
            @Override // com.whl.quickjs.wrapper.JSObjectCreator
            public JSArray newArray(QuickJSContext quickJSContext, long j) {
                return new QuickJSArray(quickJSContext, j);
            }

            @Override // com.whl.quickjs.wrapper.JSObjectCreator
            public JSFunction newFunction(QuickJSContext quickJSContext, long j, long j2, int i) {
                return new QuickJSFunction(quickJSContext, j, j2, i);
            }

            @Override // com.whl.quickjs.wrapper.JSObjectCreator
            public JSObject newObject(QuickJSContext quickJSContext, long j) {
                return new QuickJSObject(quickJSContext, j);
            }
        });
    }

    private native long createContext(long j);

    private native long createRuntime();

    private native void destroyContext(long j);

    private native void dumpMemoryUsage(long j, String str);

    private native void dumpObjects(long j, String str);

    private native void dupValue(long j, long j2);

    private void dupValue(JSObject jSObject) {
        checkSameThread();
        checkDestroyed();
        dupValue(this.context, jSObject.getPointer());
    }

    private native Object evaluate(long j, String str, String str2);

    private native Object evaluateModule(long j, String str, String str2);

    private native Object execute(long j, byte[] bArr);

    private native void freeDupValue(long j, long j2);

    private native void freeValue(long j, long j2);

    private native Object get(long j, long j2, int i);

    private native JSObject getGlobalObject(long j);

    private native long getMemoryUsedSize(long j);

    private native Object getOwnPropertyNames(long j, long j2);

    private native Object getProperty(long j, long j2, String str);

    private native boolean isLiveObject(long j, long j2);

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    public static /* synthetic */ Object lambda$setConsole$0(Console console, Object[] objArr) {
        String str;
        if (objArr.length != 2) {
            return null;
        }
        String str2 = (String) objArr[0];
        String str3 = (String) objArr[1];
        switch (str2.hashCode()) {
            case 107332:
                str = "log";
                break;
            case 3237038:
                if (str2.equals("info")) {
                    console.info(str3);
                    return null;
                }
                console.log(str3);
                return null;
            case 3641990:
                if (str2.equals("warn")) {
                    console.warn(str3);
                    return null;
                }
                console.log(str3);
                return null;
            case 95458899:
                str = "debug";
                break;
            case 96784904:
                if (str2.equals("error")) {
                    console.error(str3);
                    return null;
                }
                console.log(str3);
                return null;
            default:
                console.log(str3);
                return null;
        }
        str2.equals(str);
        console.log(str3);
        return null;
    }

    private native int length(long j, long j2);

    private native Object parseJSON(long j, String str);

    private void putCallFunction(JSCallFunction jSCallFunction) {
        this.callFunctionMap.put(Integer.valueOf(jSCallFunction.hashCode()), jSCallFunction);
    }

    private native void runGC(long j);

    private native void set(long j, long j2, Object obj, int i);

    private native void setGCThreshold(long j, int i);

    private native void setMaxStackSize(long j, int i);

    private native void setMemoryLimit(long j, int i);

    private native void setProperty(long j, long j2, String str, Object obj);

    private native String stringify(long j, long j2);

    public Object call(JSObject jSObject, long j, int i, Object... objArr) {
        checkSameThread();
        checkDestroyed();
        for (Object obj : objArr) {
            if (obj instanceof JSCallFunction) {
                putCallFunction((JSCallFunction) obj);
            }
        }
        return call(this.context, jSObject.getPointer(), j, i, objArr);
    }

    public Object callFunctionBack(int i, Object... objArr) {
        checkSameThread();
        checkDestroyed();
        Object objCall = this.callFunctionMap.get(Integer.valueOf(i)).call(objArr);
        if (objCall instanceof JSCallFunction) {
            putCallFunction((JSCallFunction) objCall);
        }
        if (objCall instanceof JSObject) {
            JSObject jSObject = (JSObject) objCall;
            jSObject.decrementRefCount();
            if (jSObject.getRefCount() == 0) {
                this.objectRecords.remove(jSObject);
            }
        }
        return objCall;
    }

    @Override // java.io.Closeable, java.lang.AutoCloseable
    public void close() {
        destroy();
    }

    public byte[] compile(String str, String str2) {
        if (str == null) {
            // 原反编译为 C1902zy.m12469m（抛异常）；还原为 IllegalArgumentException。
            throw new IllegalArgumentException("Script cannot be null with " + str2);
        }
        checkSameThread();
        checkDestroyed();
        return compile(this.context, str, str2, false);
    }

    public byte[] compileModule(String str, String str2) {
        if (str == null) {
            // 原反编译为 C1902zy.m12469m（抛异常）；还原为 IllegalArgumentException。
            throw new IllegalArgumentException("Script cannot be null with " + str2);
        }
        checkSameThread();
        checkDestroyed();
        return compile(this.context, str, str2, true);
    }

    public JSArray createNewJSArray() {
        // 反编译把字符串常量错关联到 _UrlKt；与下方 createNewJSObject 的 "{}" 对称，此处应为空数组。
        return (JSArray) parseJSON("[]");
    }

    public JSObject createNewJSObject() {
        return parseJSON("{}");
    }

    public void destroy() {
        checkSameThread();
        checkDestroyed();
        this.callFunctionMap.clear();
        releaseObjectRecords();
        this.objectRecords.clear();
        destroyContext(this.context);
        this.destroyed = true;
    }

    public void dumpMemoryUsage(File file) {
        checkSameThread();
        checkDestroyed();
        dumpMemoryUsage(this.runtime, (file == null || !file.exists()) ? null : file.getAbsolutePath());
    }

    public void dumpObjects(File file) {
        checkSameThread();
        checkDestroyed();
        dumpObjects(this.runtime, (file == null || !file.exists()) ? null : file.getAbsolutePath());
    }

    public Object evaluate(String str, String str2) {
        if (str == null) {
            // 原反编译为 C1902zy.m12469m（抛异常）；还原为 IllegalArgumentException。
            throw new IllegalArgumentException("Script cannot be null with " + str2);
        }
        checkSameThread();
        checkDestroyed();
        return evaluate(this.context, str, str2);
    }

    public Object evaluateModule(String str, String str2) {
        if (str == null) {
            // 原反编译为 C1902zy.m12469m（抛异常）；还原为 IllegalArgumentException。
            throw new IllegalArgumentException("Script cannot be null with " + str2);
        }
        checkSameThread();
        checkDestroyed();
        return evaluateModule(this.context, str, str2);
    }

    public Object execute(byte[] bArr) {
        if (bArr == null) {
            throw new IllegalArgumentException("Bytecode cannot be null");
        }
        checkSameThread();
        checkDestroyed();
        return execute(this.context, bArr);
    }

    public void freeValue(JSObject jSObject) {
        checkSameThread();
        checkDestroyed();
        freeValue(this.context, jSObject.getPointer());
        if (jSObject.getRefCount() == 0) {
            this.objectRecords.remove(jSObject);
        }
    }

    public Object get(JSArray jSArray, int i) {
        checkSameThread();
        checkDestroyed();
        return get(this.context, jSArray.getPointer(), i);
    }

    public int getCallFunctionMapSize() {
        return this.callFunctionMap.size();
    }

    public JSObjectCreator getCreator() {
        return this.creator;
    }

    public long getCurrentThreadId() {
        return this.currentThreadId;
    }

    public JSObject getGlobalObject() {
        checkSameThread();
        checkDestroyed();
        JSObject jSObject = this.globalObject;
        if (jSObject != null) {
            return jSObject;
        }
        JSObject globalObject = getGlobalObject(this.context);
        this.globalObject = globalObject;
        return globalObject;
    }

    public long getMemoryUsedSize() {
        return getMemoryUsedSize(this.runtime);
    }

    public ModuleLoader getModuleLoader() {
        return this.moduleLoader;
    }

    public List<JSObject> getObjectRecords() {
        return this.objectRecords;
    }

    public Object getOwnPropertyNames(JSObject jSObject) {
        return getOwnPropertyNames(this.context, jSObject.getPointer());
    }

    public Object getProperty(JSObject jSObject, String str) {
        checkSameThread();
        checkDestroyed();
        return getProperty(this.context, jSObject.getPointer(), str);
    }

    public void hold(JSObject jSObject) {
        checkSameThread();
        checkDestroyed();
        dupValue(jSObject);
    }

    public boolean isLiveObject(JSObject jSObject) {
        return isLiveObject(this.runtime, jSObject.getPointer());
    }

    public int length(JSArray jSArray) {
        checkSameThread();
        checkDestroyed();
        if (isLiveObject(jSArray)) {
            return length(this.context, jSArray.getPointer());
        }
        return 0;
    }

    public Object parse(String str) {
        checkSameThread();
        checkDestroyed();
        return parseJSON(this.context, str);
    }

    @Deprecated
    public JSObject parseJSON(String str) {
        checkSameThread();
        checkDestroyed();
        Object json = parseJSON(this.context, str);
        if (json instanceof JSObject) {
            return (JSObject) json;
        }
        throw new QuickJSException("Only parse json with valid format, must be start with '{', if it contains other case, use parse(String) replace.");
    }

    public void releaseObjectRecords(boolean z) {
        JSFunction jSFunction = getGlobalObject().getJSFunction("format");
        Iterator<JSObject> it = this.objectRecords.iterator();
        while (it.hasNext()) {
            JSObject next = it.next();
            if (!next.isRefCountZero() && next != getGlobalObject() && next != jSFunction) {
                int refCount = next.getRefCount();
                if (this.leakDetectionListener != null) {
                    this.leakDetectionListener.notifyLeakDetected(next, (String) jSFunction.call(next));
                }
                if (z) {
                    for (int i = 0; i < refCount; i++) {
                        next.decrementRefCount();
                        freeValue(this.context, next.getPointer());
                    }
                    if (next.getRefCount() == 0) {
                        it.remove();
                    }
                }
            }
        }
        jSFunction.release();
    }

    public void removeCallFunction(int i) {
        this.callFunctionMap.remove(Integer.valueOf(i));
    }

    public void runGC() {
        runGC(this.runtime);
    }

    public void set(JSArray jSArray, Object obj, int i) {
        checkSameThread();
        checkDestroyed();
        set(this.context, jSArray.getPointer(), obj, i);
    }

    public void setConsole(final Console console) {
        if (console == null) {
            return;
        }
        JSObject jSObject = getGlobalObject().getJSObject("console");
        jSObject.setProperty("stdout", new JSCallFunction() { // from class: kz3
            @Override // com.whl.quickjs.wrapper.JSCallFunction
            public final Object call(Object[] objArr) {
                return QuickJSContext.lambda$setConsole$0(console, objArr);
            }
        });
        jSObject.release();
    }

    public void setEnableStackTrace(boolean z) {
        this.enableStackTrace = z;
    }

    public void setGCThreshold(int i) {
        setGCThreshold(this.runtime, i);
    }

    public void setLeakDetectionListener(LeakDetectionListener leakDetectionListener) {
        this.leakDetectionListener = leakDetectionListener;
    }

    public void setMaxStackSize(int i) {
        setMaxStackSize(this.runtime, i);
    }

    public void setMemoryLimit(int i) {
        setMemoryLimit(this.runtime, i);
    }

    public void setModuleLoader(ModuleLoader moduleLoader) {
        checkSameThread();
        checkDestroyed();
        if (moduleLoader != null) {
            this.moduleLoader = moduleLoader;
        } else {
            throw new IllegalArgumentException("The moduleLoader can not be null!");
        }
    }

    public void setProperty(JSObject jSObject, String str, Object obj) {
        checkSameThread();
        checkDestroyed();
        if (obj instanceof JSCallFunction) {
            putCallFunction((JSCallFunction) obj);
        }
        setProperty(this.context, jSObject.getPointer(), str, obj);
    }

    public String stringify(JSObject jSObject) {
        checkSameThread();
        checkDestroyed();
        return stringify(this.context, jSObject.getPointer());
    }

    public void throwJSException(String str) {
        evaluate("throw \"" + str + "\";");
    }

    public static QuickJSContext create(JSObjectCreator jSObjectCreator) {
        return new QuickJSContext(jSObjectCreator);
    }

    public void dumpMemoryUsage() {
        dumpMemoryUsage(null);
    }

    public void dumpObjects() {
        dumpObjects(null);
    }

    public Object evaluate(String str) {
        return evaluate(str, UNKNOWN_FILE);
    }

    public Object evaluateModule(String str) {
        return evaluateModule(str, UNKNOWN_FILE);
    }

    public byte[] compile(String str) {
        return compile(str, UNKNOWN_FILE);
    }

    public byte[] compileModule(String str) {
        return compileModule(str, UNKNOWN_FILE);
    }

    public void releaseObjectRecords() {
        releaseObjectRecords(true);
    }
}
