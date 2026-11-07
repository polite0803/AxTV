/*
 * 热榜页（NavRoutes.Hot / NavDestination.Hot）。
 *
 * 数据：HotRepository.load() → SearchApi.hotSearch()，即 /aweme/v1/web/hot/search/list/。
 *       整榜一次返回，没有分页参数，所以本页没有续页逻辑，也不该有。
 * 呈现：LazyColumn + 搜索模块已有的 HotWordRow（名次 / 词 / 热度），不另造一份行组件。
 * 焦点：进入本页落到榜首；失败与空态把焦点交给页内「重试」，遥控器不会走进死格。
 *       本页是顶层标签，返回键交系统处理，因此不接 onBack。
 * 错误处理：加载中 / 失败（可重试）/ 空态各有画面，绝不把异常吞成空列表。
 */
package com.github.axtv.dv.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.search.HotWordRow
import kotlinx.coroutines.launch

/** 榜项的组合键：必须与 items(key=) 用同一个函数，requester 才能和行一一绑定。 */
private fun hotKey(word: String, index: Int): String = word + "#" + index

@Composable
fun HotScreen(
    onOpenSearch: (keyword: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var board by remember { mutableStateOf(HotRepository.cachedBoard) }
    var loading by remember { mutableStateOf(board.entries.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var focusRestored by remember { mutableStateOf(false) }
    var rememberedFocusIndex by remember { mutableIntStateOf(0) }
    val retryRequester = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /** 榜面刷新后清掉已消失词条的请求器：残留条目会让 requestFocus() 甩到回收掉的槽位。 */
    fun pruneRequesters() {
        val alive = board.entries.indices.map { hotKey(board.entries[it].word, it) }.toSet()
        requesters.keys.retainAll(alive)
    }

    suspend fun fetch() {
        loading = true
        errorMessage = null
        val result = HotRepository.load()
        loading = false
        result.fold(
            onSuccess = { page ->
                board = page
                HotRepository.cache(page)
                focusRestored = false
                pruneRequesters()
            },
            onFailure = { t ->
                errorMessage = t.message ?: "热榜加载失败"
            },
        )
    }

    // 首屏：会话内已有缓存就直接复用，不重复请求。
    LaunchedEffect(Unit) {
        if (board.entries.isEmpty()) fetch()
    }

    // 数据到位后把焦点落到记住的那一行；没有列表时落到「重试」。
    LaunchedEffect(board.entries.size, focusRestored) {
        if (board.entries.isEmpty()) return@LaunchedEffect
        if (focusRestored) return@LaunchedEffect
        val target = rememberedFocusIndex.coerceIn(0, board.entries.size - 1)
        runCatching { listState.scrollToItem(target) }
        withFrameNanos { }
        runCatching { requesters[hotKey(board.entries[target].word, target)]?.requestFocus() }
        focusRestored = true
    }

    LaunchedEffect(errorMessage, board.entries.size, loading) {
        if (!loading && board.entries.isEmpty()) {
            withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Text(
            text = "热榜",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(14.dp))

        when {
            loading -> FeedStatePanel(
                title = "加载中…",
                detail = "正在请求全站热搜榜（hot/search/list）。",
            )

            errorMessage != null -> FeedStatePanel(
                title = "加载失败",
                detail = errorMessage.orEmpty(),
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { scope.launch { fetch() } },
            )

            board.entries.isEmpty() -> FeedStatePanel(
                title = "暂无内容",
                detail = "接口返回成功，但榜单里没有热搜词，可稍后重试。",
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { scope.launch { fetch() } },
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusGroup(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    count = board.entries.size,
                    key = { index -> hotKey(board.entries[index].word, index) },
                ) { index ->
                    val entry = board.entries[index]
                    val key = hotKey(entry.word, index)
                    val requester = remember(key) { FocusRequester() }
                    SideEffect { requesters[key] = requester }
                    HotWordRow(
                        position = entry.position,
                        word = entry.word,
                        hotValue = entry.hotValue,
                        requester = requester,
                        onFocused = { rememberedFocusIndex = index },
                        onClick = { onOpenSearch(entry.word) },
                    )
                }
            }
        }
    }
}
