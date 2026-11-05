/*
 * 来源: src/app/p000/mp1.java（GLSurfaceView 宿主）、lp1.java（surface 绑定/释放）、
 *       ro5.java:1288-1302（GL 模式挂载）、ro5.java:35866-35887（SurfaceHolder 尺寸）
 * 原职责: 把播放器输出接到 SurfaceView / TextureView / 自定义 GLSurfaceView
 * 重构说明: 抽成接口，Android 侧分别用 SurfaceView.getHolder().getSurface()、
 *          TextureView.getSurfaceTexture()、mp1 的回调 surface
 */
package axtv.player;

/**
 * 画面宿主。
 *
 * <p>三种实现（{@link PlayerSurfaceMode}）：
 * <ul>
 *   <li>{@code SURFACE_VIEW} — {@code PlayerView.getVideoSurfaceView()} 就是 SurfaceView，
 *       直接 {@code holder.getSurface()}；缩放靠 {@code setScaleX/Y + setTranslationX/Y}
 *       （API 24+ 才生效，{@code ro5.java:1417}）。</li>
 *   <li>{@code TEXTURE_VIEW} — 通过 {@code TextureView.setTransform(Matrix)} 缩放
 *       （{@code ro5.java:1412-1416}）。</li>
 *   <li>{@code GL_SURFACE_VIEW} — 自定义 GLSurfaceView 在 {@code onSurfaceCreated} 里
 *       自建 SurfaceTexture + Surface 交给播放器（{@code xn5.java:208-227}），
 *       见 {@link GlVideoSurfaceView}。</li>
 * </ul>
 */
public interface SurfaceHost {

    /** 当前可用 surface，未就绪时返回 null。 */
    Object currentSurface();

    /** surface 宽高（像素），用于固定缓冲区尺寸与偏移钳制。 */
    int width();

    int height();

    PlayerSurfaceMode mode();

    /**
     * 设置 SurfaceView 固定缓冲区。
     *
     * @param fixedSize true 时 {@code holder.setFixedSize(align64(w), h)}，
     *                  false 时 {@code holder.setSizeFromLayout()}
     *                  （{@code ro5.java:35881-35885}）
     */
    void applyFixedSize(boolean fixedSize);

    /** 设置 AspectRatioFrameLayout 的宽高比（{@code ro5.java:35860}）。 */
    void applyAspectRatio(float aspectRatio);

    /**
     * 应用缩放/位移。
     *
     * <p>TextureView：{@code matrix.setScale(s, s, w/2, h/2); matrix.postTranslate(tx, ty)}；
     * SurfaceView：{@code setScaleX/Y(s)} + {@code setTranslationX/Y(tx, ty)}。
     */
    void applyTransform(VideoScaleMatrix.Transform transform);
}
