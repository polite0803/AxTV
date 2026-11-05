package com.whl.quickjs.wrapper;

/* JADX INFO: compiled from: r8-map-id-c2f6a97aa2e8a24cc4494d4253d5e904442917ade8115d3074f7f7c1f1495db8 */
/* JADX INFO: loaded from: classes.dex */
public abstract class ModuleLoader {
    public abstract byte[] getModuleBytecode(String str);

    public abstract String getModuleStringCode(String str);

    public abstract boolean isBytecodeMode();

    public String moduleNormalizeName(String str, String str2) {
        return str2;
    }
}
