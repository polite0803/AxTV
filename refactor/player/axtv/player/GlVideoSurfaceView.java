/*
 * 来源: src/app/p000/mp1.java（GLSurfaceView 宿主）、xn5.java（GLSurfaceView.Renderer 实现）、
 *       lp1.java:24-102（surface 绑定/解绑、缓冲区尺寸）、ro5.java:1288-1302（挂载）
 * 原职责: 自定义 GL 直通渲染，绕开厂商色彩转换，修复 SDR 画面发白
 * 重构说明: GL 调用只能在 Android 层执行，这里给出接口契约 + 全部关键参数（EGL/纹理/着色器/帧循环）
 */
package axtv.player;

/**
 * GL 直通渲染视图的契约。
 *
 * <p>存在意义：部分机型（尤其 TV 芯片）在 {@code SurfaceView} 直出路径上会做
 * SDR&rarr;HDR 或 BT.601/709 的错误转换，导致画面发白。绕过方式是把 ExoPlayer 的输出
 * 接到自建 {@code SurfaceTexture}，再用一个最简 GL 程序以
 * {@code GL_TEXTURE_EXTERNAL_OES} 原样贴回屏幕——全程不做任何颜色运算，像素直通。
 *
 * <h2>管线（{@code xn5.java}）</h2>
 * <pre>
 * onSurfaceCreated:
 *   glGenTextures → bind GL_TEXTURE_EXTERNAL_OES
 *   MIN/MAG_FILTER = LINEAR, WRAP_S/T = CLAMP_TO_EDGE
 *   编译 vertex/fragment shader → link program（失败则整条管线停用，退回普通 SurfaceView）
 *   new SurfaceTexture(textureId) → new Surface(st)
 *   st.setDefaultBufferSize(w, h)（若已收到视频尺寸）
 *   回调 onSurfaceReady(surface)   ← 播放器 setVideoSurface 用这个
 *
 * onFrameAvailable:
 *   frameAvailable = true; requestRender()      // RENDERMODE_WHEN_DIRTY
 *
 * onDrawFrame:
 *   if (!frameAvailable) return;
 *   frameAvailable = false; st.updateTexImage(); st.getTransformMatrix(m4)
 *   glClearColor(0,0,0,1); glClear(COLOR_BUFFER_BIT)
 *   glUseProgram; glActiveTexture(TEXTURE0); glBindTexture(EXTERNAL_OES, tex)
 *   glUniform1i(uTexture, 0); glUniformMatrix4fv(uTexMatrix, m4)
 *   glVertexAttribPointer(aPosition/aTexCoord); glDrawArrays(TRIANGLE_STRIP, 0, 4)
 *
 * onSurfaceChanged:
 *   glViewport(0, 0, w, h)
 *
 * onDetachedFromWindow:
 *   回调 onSurfaceReleasing() → 播放器 setVideoSurface(null)
 *   逐个 release SurfaceTexture + Surface，清空队列
 * </pre>
 *
 * <p>所有 GL 调用必须在该视图的 GL 线程执行；{@code setDefaultBufferSize} 要用
 * {@code view.queueEvent(...)} 切线程（{@code lp1.java:88}）。
 */
public interface GlVideoSurfaceView {

    /** 生命周期回调，对应 {@code mp1.setOnSurfaceReady/setOnSurfaceReleasing}。 */
    interface Callback {

        /** GL 线程创建好 surface，交给播放器绑定。 */
        void onSurfaceReady(Object surface);

        /** 视图销毁，需立刻解绑，否则会被系统回收纹理。 */
        void onSurfaceReleasing();
    }

    void setCallback(Callback callback);

    /**
     * 设置输出缓冲区尺寸（{@code lp1.m7475d}）。
     *
     * <p>内部去重：与上次相同则跳过；切到 GL 线程执行
     * {@code SurfaceTexture.setDefaultBufferSize(w, h)}，失败只记
     * {@code "setDefaultBufferSize 失败: WxH"} 警告，不影响播放。
     */
    void setDefaultBufferSize(int width, int height);

    /** 请求渲染一帧（{@code RENDERMODE_WHEN_DIRTY} 下由 {@code onFrameAvailable} 触发）。 */
    void requestRender();

    /** 当前 surface 是否已创建。 */
    boolean isSurfaceReady();

    /** GL 参数与着色器源码，逐字取自 {@code mp1.java} / {@code xn5.java}。 */
    final class Spec {

        private Spec() {
        }

        /** {@code mp1.java:29} RENDERMODE_WHEN_DIRTY：只在有新帧时渲染，静态画面零功耗。 */
        public static final int RENDER_MODE = PlayerConfig.GL_RENDER_MODE_WHEN_DIRTY;

        /** 顶点着色器（{@code xn5.java:181}）。 */
        public static final String VERTEX_SHADER = """
                    attribute vec4 aPosition;
                    attribute vec4 aTexCoord;
                    varying vec2 vTexCoord;
                    void main() {
                        gl_Position = aPosition;
                        vTexCoord = aTexCoord.xy;
                    }
                """;

        /** 片元着色器（{@code xn5.java:182}）：采样外部纹理，套 SurfaceTexture 给的变换矩阵。 */
        public static final String FRAGMENT_SHADER = """
                    #extension GL_OES_EGL_image_external : require
                    precision mediump float;
                    uniform samplerExternalOES uTexture;
                    uniform mat4 uTexMatrix;
                    varying vec2 vTexCoord;
                    void main() {
                        gl_FragColor = texture2D(uTexture, (uTexMatrix * vec4(vTexCoord, 0.0, 1.0)).xy);
                    }
                """;

        /** 顶点数组：四角，{@code TRIANGLE_STRIP} 一次画完（{@code wx5.f24193t}）。 */
        public static final float[] QUAD_POSITIONS = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};

        /** 纹理坐标（{@code wx5.f24194u}，注意 y 方向与顶点相反，配合 SurfaceTexture 的矩阵）。 */
        public static final float[] QUAD_TEX_COORDS = {0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f};

        /** clearColor 为不透明黑，避免首帧前闪白（{@code xn5.java:139}）。 */
        public static final float[] CLEAR_COLOR = {0f, 0f, 0f, 1f};

        /** 每次绘制 4 个顶点（{@code xn5.java:155} {@code glDrawArrays(5, 0, 4)}）。 */
        public static final int VERTEX_COUNT = 4;
    }
}
