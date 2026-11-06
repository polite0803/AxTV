/*
 * 作者频道的数据层：他人主页资料 + 作者作品列表（aweme/post）。
 *
 * 两处消费：
 *  - AuthorChannelScreen 拉资料与首屏作品，并把结果写进本对象；
 *  - PlayerFromAuthorScreen 用这份快照组成连播队列，播放到队尾时按 max_cursor 继续翻页，
 *    实现「进作者频道后连续看完该发布者的所有视频」。
 *
 * 接口出处：refactor/api/axtv/api/UserApi.java（profileOther / userPosts）。
 * 原版 profile/other 的 source 固定为 channel_pc_web（src/app/p000/n11.java:3534）。
 */
package com.github.axtv.dv.ui.author

import axtv.api.DouyinApi
import axtv.api.UserApi
import axtv.model.Aweme
import axtv.model.UserProfileResponse
import axtv.model.UserWorksResponse
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 作者作品每页条数；与个人页作品列表保持一致。 */
internal const val AUTHOR_PAGE_SIZE = 18

private const val PROFILE_SOURCE = "channel_pc_web"

internal object AuthorRepository {

    /** 当前快照所属的 sec_user_id；切换作者时频道页会重新覆盖。 */
    @Volatile
    var cachedSecUid: String = ""
        private set

    /** 已加载的作者作品（按接口顺序，去重后追加）。 */
    @Volatile
    var cachedAwemes: List<Aweme> = emptyList()
        private set

    @Volatile
    private var maxCursor: Long = 0L

    @Volatile
    private var hasMorePages: Boolean = false

    @Volatile
    private var loadingMore: Boolean = false

    suspend fun fetchProfile(secUid: String): UserProfileResponse = withContext(Dispatchers.IO) {
        UserApi(requireApi()).profileOther(secUid, PROFILE_SOURCE)
    }

    suspend fun fetchPage(secUid: String, cursor: Long): UserWorksResponse =
        withContext(Dispatchers.IO) {
            UserApi(requireApi()).userPosts(secUid, cursor, AUTHOR_PAGE_SIZE)
        }

    /** 频道页首屏落地：整体替换快照，避免上一个作者的作品混进当前队列。 */
    @Synchronized
    fun cacheFirst(
        secUid: String,
        awemes: List<Aweme>,
        cursor: Long,
        hasMore: Boolean,
    ) {
        cachedSecUid = secUid
        cachedAwemes = awemes.toList()
        maxCursor = cursor
        hasMorePages = hasMore
    }

    /** 频道页「加载更多」落地：按 id 去重后追加。 */
    @Synchronized
    fun cacheMore(
        secUid: String,
        awemes: List<Aweme>,
        cursor: Long,
        hasMore: Boolean,
    ) {
        if (secUid != cachedSecUid) {
            return
        }
        cachedAwemes = awemes.toList()
        maxCursor = cursor
        hasMorePages = hasMore
    }

    /** 当前作者已加载的作品快照；作者不匹配时返回空表（避免播到上一个作者的队列）。 */
    @Synchronized
    fun snapshot(secUid: String): List<Aweme> =
        if (secUid == cachedSecUid) cachedAwemes else emptyList()

    /**
     * 播放器队尾续页：按快照的 max_cursor 继续拉作者作品。
     *
     * 业务码非 0 视为「没有更多」；网络异常保留 hasMore，播放器下次按「下」还能再试。
     */
    suspend fun loadMore(secUid: String): List<Aweme> {
        if (loadingMore) {
            return emptyList()
        }
        val cursor = synchronized(this) {
            if (secUid != cachedSecUid || !hasMorePages) null else maxCursor
        } ?: return emptyList()
        loadingMore = true
        try {
            val response = fetchPage(secUid, cursor)
            if (response.statusCode() != 0) {
                synchronized(this) { if (secUid == cachedSecUid) hasMorePages = false }
                return emptyList()
            }
            return synchronized(this) {
                if (secUid != cachedSecUid) {
                    emptyList()
                } else {
                    val known = cachedAwemes.mapNotNullTo(HashSet()) {
                        it.awemeId().takeIf(String::isNotEmpty)
                    }
                    val added = response.works().filter { aweme ->
                        val id = aweme.awemeId()
                        id.isNotEmpty() && known.add(id)
                    }
                    cachedAwemes = cachedAwemes + added
                    maxCursor = response.maxCursor()
                    hasMorePages = response.hasMore() && added.isNotEmpty()
                    added
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // 保留 hasMore：网络恢复后播放器再按一次「下」可以继续续页。
            return emptyList()
        } finally {
            loadingMore = false
        }
    }

    private fun requireApi(): DouyinApi =
        AppRuntime.api ?: throw IllegalStateException("接口尚未装配，请稍后重试")
}
