/*
 * 电视台直播 —— 列表页。
 *
 * 结构对齐 ui/live/LiveListScreen（同一套 TV 焦点语言）：LazyColumn + 自适应列数网格，加载/失败/空三态齐全。
 * 数据来自 IptvRepository.load()（内置 + 用户源，走核心层解析）。
 *
 * 呈现红线（见 PLAN §3.3）：
 *  - 「网络全挂」与「真的没有频道」是两种缺席，各占一句，不并成一句、不显示成可点的空列表；
 *  - 顶部如实列出每个源的健康度（成功 N 个 / 失败：原因 / 空），不把失败的源静默剔除。
 */
package com.github.axtv.dv.ui.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.FocusMemory
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.settings.LiveSourceQrDialog

/** 电视台列表页（供导航接线）。 */
@Composable
fun IptvListScreen(
    onBack: () -> Unit,
    onPlay: (channelId: String) -> Unit,
) {
    var catalog by remember { mutableStateOf<IptvCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var showQr by remember { mutableStateOf(false) }

    val headerRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    // 分组选择跨「播放返回」保留（SaveableStateHolder 按路由保存屏内状态）。
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    // 会话内缓存：再次进入本页直接复用上次目录，不再联网重拉；「刷新」按钮才强制重拉。
    LaunchedEffect(reloadToken) {
        val cachedNow = IptvRepository.cachedCatalog()
        if (reloadToken == 0 && cachedNow != null) {
            catalog = cachedNow
            loading = false
            return@LaunchedEffect
        }
        loading = true
        catalog = IptvRepository.load()
        loading = false
    }

    // 有频道时把初始/切组后的焦点落到目标组第一张卡片；无频道时交给顶部按钮，避免遥控器无处可去。
    LaunchedEffect(loading, catalog, selectedGroup) {
        if (loading || catalog == null) return@LaunchedEffect
        val c = catalog!!
        if (c.totalChannels == 0) {
            withFrameNanos { }
            runCatching { headerRequester.requestFocus() }
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        val targetGroup = if (selectedGroup == null) {
            c.groups.firstOrNull { it.channels.isNotEmpty() }
        } else {
            c.groups.firstOrNull { it.title == selectedGroup && it.channels.isNotEmpty() }
        } ?: return@LaunchedEffect
        // 优先回到上次聚焦的频道（播放返回不丢位置），不在本组才落第一张。
        val rememberedId = FocusMemory.last("iptv")
        val target = targetGroup.channels.firstOrNull { it.id == rememberedId }
            ?: targetGroup.channels.first()
        runCatching { cardRequesters["${targetGroup.title}|${target.id}"]?.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AxTvColors.Bg)
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "电视台",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "央视 / 卫视 · 内置清单 + 手机推送",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IptvTopButton(label = "刷新", requester = refreshRequester) { reloadToken += 1 }
                IptvTopButton(label = "用手机添加直播源", requester = headerRequester) { showQr = true }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!loading && catalog != null && catalog!!.totalChannels > 0) {
            IptvSourceHealthRow(catalog!!.sources)
            Spacer(Modifier.height(10.dp))
            // 频道分组选择：遥控器可直接跳到 央视频道 / 卫视频道 / 地方频道 等。
            IptvGroupChips(
                groups = catalog!!.groups,
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
            )
            Spacer(Modifier.height(10.dp))
        }

        when {
            loading -> IptvStatePanel("正在加载直播源…", "首次进入需要联网拉取清单，可能要几秒")

            catalog == null || catalog!!.totalChannels == 0 -> {
                val c = catalog
                if (c != null && c.allFailed) {
                    IptvStatePanel(
                        title = "所有直播源都拉取失败",
                        detail = "多为网络不可达或清单已失效。请检查电视网络，或用手机添加可用的源。",
                        actionLabel = "重试",
                        actionRequester = headerRequester,
                        onAction = { reloadToken += 1 },
                    )
                } else {
                    IptvStatePanel(
                        title = "暂时没有可播放的频道",
                        detail = "内置清单未解析到频道。可用手机扫码添加自己的直播源清单。",
                        actionLabel = "添加直播源",
                        actionRequester = headerRequester,
                        onAction = { showQr = true },
                    )
                }
            }

            else -> {
                val all = catalog!!.groups
                val shown = if (selectedGroup == null) all else all.filter { it.title == selectedGroup }
                IptvGroupList(
                    groups = shown,
                    cardRequesters = cardRequesters,
                    onPlay = onPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showQr) {
        LiveSourceQrDialog(onDismiss = { showQr = false })
    }
}

@Composable
private fun IptvGroupChips(
    groups: List<IptvGroup>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IptvChip(label = "全部 ${groups.sumOf { it.channels.size }}", selected = selected == null) {
            onSelect(null)
        }
        groups.forEach { g ->
            IptvChip(label = "${g.title} ${g.channels.size}", selected = selected == g.title) {
                onSelect(g.title)
            }
        }
    }
}

@Composable
private fun IptvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> AxTvColors.Focus.copy(alpha = 0.30f)
                    focused -> AxTvColors.SurfaceHigh
                    else -> AxTvColors.Surface
                },
                RoundedCornerShape(10.dp),
            )
            .focusRing(focused, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = AxTvColors.TextPrimary,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun IptvGroupList(
    groups: List<IptvGroup>,
    cardRequesters: MutableMap<String, FocusRequester>,
    onPlay: (channelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            item(key = "g:${group.title}") {
                Text(
                    text = "${group.title} · ${group.channels.size}",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            item(key = "r:${group.title}") {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = (maxWidth / 220.dp).toInt().coerceAtLeast(2)
                    Column {
                        group.channels.chunked(columns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowItems.forEach { channel ->
                                    val reqKey = "${group.title}|${channel.id}"
                                    val requester = remember(reqKey) { FocusRequester() }
                                        .also { cardRequesters[reqKey] = it }
                                    Box(modifier = Modifier.weight(1f)) {
                                        IptvChannelCard(
                                            channel = channel,
                                            requester = requester,
                                            onClick = { onPlay(channel.id) },
                                        )
                                    }
                                }
                                // 末行不满时补位，保证每列等宽
                                repeat(columns - rowItems.size) {
                                    Box(modifier = Modifier.weight(1f)) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvChannelCard(
    channel: IptvChannel,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .focusRequester(requester)
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) FocusMemory.remember("iptv", channel.id)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) AxTvColors.Focus.copy(alpha = 0.30f) else AxTvColors.Surface,
                RoundedCornerShape(12.dp),
            )
            .focusRing(focused, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = channel.name,
            color = AxTvColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "候选 ${channel.candidates.size} · ${channel.sourceName}",
            color = AxTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 11.sp),
        )
    }
}

@Composable
private fun IptvTopButton(label: String, requester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(AxTvColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun IptvSourceHealthRow(sources: List<IptvSourceHealth>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sources.forEach { s ->
            val label = when {
                s.error != null -> "${s.sourceName}：失败"
                s.channels == 0 -> "${s.sourceName}：空清单"
                else -> "${s.sourceName}：${s.channels}"
            }
            val color = when {
                s.error != null -> AxTvColors.AccentWarm
                s.channels == 0 -> AxTvColors.TextSecondary
                else -> AxTvColors.Accent
            }
            Text(text = label, color = color, style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
private fun IptvStatePanel(
    title: String,
    detail: String,
    actionLabel: String? = null,
    actionRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = detail,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        if (actionLabel != null && onAction != null && actionRequester != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .focusRequester(actionRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(AxTvColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
