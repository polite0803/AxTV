/*
 * 来源: src/app/p000/g73.java:481-520 (m4569k)、721-760 (m4577t/m4578u)、158-190 (m4560G)、200+ (m4561a)
 *       src/app/p000/jb5.java:9-13、AbstractC1803x9.java:301 (requestChannelBrowsable)
 *       src/app/p000/mb5.java:78-136 (logo 生成)
 * 原职责: 把 Android TvContract / ContentResolver / Bitmap 能力抽象成核心层可替换的接口
 * 重构说明: 核心算法（同步/去重/diff）全部留在本模块；Android 侧只需实现本接口
 */
package axtv.tv;

import java.util.List;
import java.util.Map;

/**
 * Android TV 频道能力的边界。
 *
 * <p>原实现对应：{@code ContentResolver.query/insert/update/delete} 操作
 * {@code content://android.media.tv/channel} 与 {@code .../preview_program}，
 * {@code TvContract.requestChannelBrowsable}、{@code TvContract.buildChannelUri}、
 * 以及 cacheDir 下 96×96 PNG 的频道 logo。
 */
public interface TvProvider {

    /** {@code Build.VERSION.SDK_INT}。原实现所有操作都要求 &gt;= 26。 */
    int sdkInt();

    /** {@code context.getPackageManager().resolveContentProvider("android.media.tv", 0) != null}。 */
    boolean hasTvProvider();

    /** 按 {@link ChannelSpec#CHANNEL_PROJECTION} 查询全部频道（{@code g73.m4569k}）。 */
    List<Map<String, Object>> queryChannels();

    /** 查询全部 preview_program（{@code mb5.m7710c} 里 selection 为 null，核心层再按 channel_id 过滤）。 */
    List<Map<String, Object>> queryAllPrograms();

    /** 按 _id 查询单条节目（{@code g73.m4560G} 做字段 diff 时用）。无结果返回 null。 */
    Map<String, Object> queryProgram(long rowId);

    /** 插入频道，返回解析出的 _id；被系统拒绝（SecurityException / SDK&lt;26）返回 -1。 */
    long insertChannel(Map<String, Object> values);

    /**
     * 把 logo 写入频道（{@code g73.m4561a}）。
     * 原实现按 URI scheme 分三路：{@code android.resource} / {@code file} / {@code content} 用
     * ContentResolver 打开，http(s) 用 HttpURLConnection；失败记日志并返回 false。
     */
    boolean addChannelLogo(long channelId, String logoUri);

    /** 删除频道（logo 添加失败时的回滚）。 */
    void deleteChannel(long channelId);

    /** {@code TvContract.requestChannelBrowsable(context, channelId)}，仅 SDK &gt;= 26。 */
    void requestChannelBrowsable(long channelId);

    /** 插入节目，返回 _id；被拒绝返回 -1。 */
    long insertProgram(Map<String, Object> values);

    /** 按 _id 更新节目。仅在新旧值有差异时由核心层调用（{@code g73.m4560G}）。 */
    void updateProgram(long rowId, Map<String, Object> values);

    /** 按 _id 删除节目（{@code ContentUris.withAppendedId(preview_program, id)}）。 */
    void deleteProgram(long rowId);

    /** logo 文件已存在且长度 &gt; 0（{@code mb5.m7709b:102} 的判定）。 */
    boolean channelLogoReady();

    /** 用应用图标绘制 96×96 ARGB_8888 位图并以 PNG/100 写到 cacheDir/tv_channel_logo.png。 */
    void createChannelLogo();

    /** {@code Uri.fromFile(new File(cacheDir, "tv_channel_logo.png")).toString()}。 */
    String channelLogoUri();
}
