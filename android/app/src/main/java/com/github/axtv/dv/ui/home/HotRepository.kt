/*
 * 热榜数据层。
 * 端点：/aweme/v1/web/hot/search/list/（SearchApi.hotSearch()，无业务参数，只注入 msToken）。
 * 规格出处：refactor/api/axtv/api/DouyinEndpoints.java:64、refactor/api/axtv/api/SearchApi.java:176。
 *
 * 为什么这一页是词表而不是视频列表：全量端点清单里并没有「热门合集」「热门视频榜」这类接口，
 * 抖音 web 唯一的全站榜单就是 hot/search/list，它返回的是热搜词（名次 / 词 / 热度）。
 * 所以本页如实展示榜单本身，点开一行进入该词的搜索结果，而不是伪造成一个视频信息流。
 */
package com.github.axtv.dv.ui.home

import axtv.api.SearchApi
import axtv.model.HotWord
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 榜单中的一行：名次（从 1 开始）、热搜词、热度值。 */
data class HotBoardEntry(
    val position: Int,
    val word: String,
    val hotValue: Long,
)

/** 一次热榜请求的快照。该接口一次返回整榜，没有分页参数。 */
data class HotBoard(
    val entries: List<HotBoardEntry> = emptyList(),
)

/** 热榜仓储：一次请求整榜，结果按会话缓存，返回本页不重复请求。 */
object HotRepository {

    var cachedBoard by mutableStateOf(HotBoard())
        private set

    fun cache(board: HotBoard) {
        cachedBoard = board
    }

    suspend fun load(): Result<HotBoard> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val response = SearchApi(api).hotSearch()
            if (response.statusCode() != 0) {
                return@withContext Result.failure(
                    IllegalStateException("热榜接口返回状态码 " + response.statusCode()),
                )
            }
            val entries = response.data()?.wordList().orEmpty()
                .filter { it.word().isNotBlank() }
                .distinctBy { it.word() }
                .mapIndexed { index, hotWord -> hotWord.toEntry(index) }
            Result.success(HotBoard(entries))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/** 接口可能不给 position（回 0），此时用列表序号补位，保证榜上名次连续可读。 */
private fun HotWord.toEntry(index: Int): HotBoardEntry {
    val rank = if (position() > 0) position() else index + 1
    return HotBoardEntry(rank, word(), hotValue())
}
