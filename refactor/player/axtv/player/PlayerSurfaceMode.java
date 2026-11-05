/*
 * 来源: src/app/p000/dp3.java（渲染模式枚举）、ro5.java:1262-1302（按模式选布局）、
 *       ro5.java:1284（setResizeMode(0)）
 * 原职责: 三种渲染方式的选择与各自初始化差异
 * 重构说明: 枚举承载"布局文件 + 是否 GL 管线 + 是否允许 Matrix 变换"三处差异
 */
package axtv.player;

/**
 * 渲染模式。
 *
 * <p>对应 {@code dp3.java:19-21} 的三个常量，值即设置项里存的字符串。
 */
public enum PlayerSurfaceMode {

    /**
     * 硬件 overlay 直出。功耗最低、延迟最小，但不能做任意矩阵变换，
     * 只能用 {@code setScaleX/Y + setTranslationX/Y}（API 24+）。
     */
    SURFACE_VIEW("surface_view", "player_view_surface", false, false),

    /**
     * 走 GPU 合成。支持 {@code setTransform(Matrix)}，但多一次纹理拷贝，功耗略高。
     */
    TEXTURE_VIEW("texture_view", "player_view_texture", false, true),

    /**
     * 自定义 GL 直通（{@code mp1} + {@code xn5}）。用于修复部分机型 SDR 画面发白：
     * ExoPlayer 输出到 SurfaceTexture，再由 GL 以 {@code GL_TEXTURE_EXTERNAL_OES}
     * 原样贴回屏幕，绕开厂商的 HDR/色彩空间转换。
     */
    GL_SURFACE_VIEW("gl_surface_view", "player_view_gl", true, false);

    private final String key;
    private final String layoutName;
    private final boolean glPipeline;
    private final boolean supportsMatrix;

    PlayerSurfaceMode(String key, String layoutName, boolean glPipeline, boolean supportsMatrix) {
        this.key = key;
        this.layoutName = layoutName;
        this.glPipeline = glPipeline;
        this.supportsMatrix = supportsMatrix;
    }

    /** 持久化的枚举字符串（{@code dp3.f4213I}）。 */
    public String key() {
        return key;
    }

    /** 布局资源名（{@code ro5.java:1265-1273} 的 {@code R.layout.*}）。 */
    public String layoutName() {
        return layoutName;
    }

    /** 是否走 GL 直通管线（需要往 {@code exo_content_frame} 里塞 GLSurfaceView）。 */
    public boolean isGlPipeline() {
        return glPipeline;
    }

    /** 是否支持 {@code Matrix} 变换（只有 TextureView 支持）。 */
    public boolean supportsMatrix() {
        return supportsMatrix;
    }

    /** 由持久化字符串解析，未知值回退 {@link #SURFACE_VIEW}。 */
    public static PlayerSurfaceMode fromKey(String key) {
        if (key != null) {
            for (PlayerSurfaceMode mode : values()) {
                if (mode.key.equals(key)) {
                    return mode;
                }
            }
        }
        return SURFACE_VIEW;
    }
}
