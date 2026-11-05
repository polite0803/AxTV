/*
 * 点播评论 → 弹幕条目（纯 JVM，可 JVM 单测）。
 *
 * 数据来源：`InteractionApi.comments` 解出的 `axtv.model.Comment`
 * （refactor/model/axtv/model/Comment.java:23-42：cid / text / create_time / digg_count …）。
 * 目标模型：本包 `DanmakuComment`（时间轴条目，由 DanmakuEngine 消费）。
 *
 * 字段映射（评论没有颜色/字号/轨道字段，缺省值按播放器既有约定）：
 * | 目标字段      | 来源 / 规则 |
 * |---------------|-------------|
 * | id            | `cm-<cid>`；cid 为空时退化为 `cm-<规范化文本的 hashCode>`，保证多次加载稳定去重 |
 * | timeMs        | 见下方「时间轴策略」 |
 * | text          | `Comment.text`：空白折叠为单空格、截断到 [MAX_TEXT_LENGTH]（超出加「…」） |
 * | colorArgb     | 恒为 `DanmakuComment.DEFAULT_COLOR_ARGB`（白色）；评论下发没有颜色字段 |
 * | fontSizeScale | 恒 1.0；全局字号由设置键 `danmaku_font_size` → DanmakuConfig.fontScale 控制 |
 * | type          | 恒 SCROLL；原版渲染链不区分弹幕类型（取证见 DanmakuMapper.kt 头注释） |
 * | hasEmoji      | 恒 false；评论没有 has_emoji 字段 |
 * | isLocal       | 恒 false |
 * 轨道不在这里分配：由 `DanmakuEngine` → `ScrollLaneAllocator` 按行高/显示区域/文字宽度统一分配，
 * 转换层只负责时间轴，避免与既有轨道算法出现第二套口径。
 *
 * 时间轴策略（两段式）：
 * 1. 时间戳模式：全部评论都有非零 `create_time` 且至少两个不同取值时，按发布时间升序，
 *    把 [最早, 最晚] 线性归一化到播放窗口 [2%, 95%] × 时长。评论顺序即发布时间顺序，
 *    播放进度推进到对应位置时上屏。
 * 2. 兜底模式（短视频 / 时间戳缺失 / 全部同时间戳）：按接口返回顺序（默认热度+时间排序）
 *    在播放窗口内等间距铺开。短视频（时长 ≤ [MIN_DURATION_MS]）直接不注入。
 *
 * 去重与限流（避免同一评论重复刷屏）：
 * - 去重：cid 相同、或规范化文本相同（忽略大小写）只保留第一条；
 * - 条数上限 [MAX_COMMENTS]；
 * - 相邻两条至少间隔 [MIN_GAP_MS]；任意一秒最多 [MAX_PER_SECOND] 条；
 *   超出窗口的条目直接丢弃（不截断到片尾，避免结尾突然刷屏）。
 * 限流只调整时间轴、不改变弹幕内容；被推后的条目保持原有先后顺序。
 */
package com.github.axtv.dv.danmaku

import axtv.model.Comment

/** 评论 → 弹幕的注入参数（固定策略，不新增设置键）。 */
object CommentDanmakuPolicy {

    /** 单个视频最多注入的评论条数。 */
    const val MAX_COMMENTS = 40

    /** 评论接口单页条数（沿用详情页的 20）。 */
    const val PAGE_SIZE = 20

    /** 最多翻页数；与 [MAX_COMMENTS] 一起限制请求量。 */
    const val MAX_PAGES = 2

    /** 单条弹幕文本上限（字符），超出截断加「…」。 */
    const val MAX_TEXT_LENGTH = 40

    /** 相邻两条弹幕的最小时间间隔（毫秒）。 */
    const val MIN_GAP_MS = 600L

    /** 同一秒内最多上屏条数。 */
    const val MAX_PER_SECOND = 3

    /** 时长低于该值视为短视频，不注入（毫秒）。 */
    const val MIN_DURATION_MS = 1_000L

    /** 播放窗口起点占比：片头 2% 之后开始。 */
    const val START_RATIO = 0.02

    /** 播放窗口终点占比：片尾 5% 留空。 */
    const val END_RATIO = 0.95
}

/**
 * 整片转换入口。
 *
 * @param durationMs 视频时长（毫秒）；≤ 0 或低于 [CommentDanmakuPolicy.MIN_DURATION_MS] 时返回空表。
 * @return 已排序、已去重、已限流的时间轴条目；不会抛异常。
 */
fun List<Comment>.toDanmakuComments(durationMs: Long): List<DanmakuComment> {
    if (durationMs < CommentDanmakuPolicy.MIN_DURATION_MS) {
        return emptyList()
    }
    val candidates = dedupeCandidates()
    if (candidates.isEmpty()) {
        return emptyList()
    }
    val windowStartMs = (durationMs * CommentDanmakuPolicy.START_RATIO).toLong()
    val windowEndMs = maxOf(
        windowStartMs + 1L,
        (durationMs * CommentDanmakuPolicy.END_RATIO).toLong(),
    )
    val ordered = orderCandidates(candidates)
    val planned = planOffsets(ordered, windowStartMs, windowEndMs)

    return applyRateLimit(ordered, planned, windowEndMs).map { (candidate, timeMs) ->
        DanmakuComment(
            id = candidate.id,
            timeMs = timeMs,
            text = candidate.text,
            colorArgb = DanmakuComment.DEFAULT_COLOR_ARGB,
            type = DanmakuType.SCROLL,
            fontSizeScale = 1f,
            hasEmoji = false,
            isLocal = false,
        )
    }
}

// ───────────────────────────── 内部实现 ─────────────────────────────

private data class Candidate(
    val id: String,
    val text: String,
    val createTimeSec: Long,
)

/**
 * 去重 + 文本规范化。
 *
 * 先到先得：接口默认按「热度/时间」排序，保留靠前的一条更符合评论列表的展示优先级。
 */
private fun List<Comment>.dedupeCandidates(): List<Candidate> {
    val seenIds = HashSet<String>()
    val seenTexts = HashSet<String>()
    val out = ArrayList<Candidate>(minOf(size, CommentDanmakuPolicy.MAX_COMMENTS))
    for (comment in this) {
        val cid = comment.cid().trim()
        val text = normalizeText(comment.text())
        if (text.isEmpty()) {
            continue
        }
        if (cid.isNotEmpty() && !seenIds.add(cid)) {
            continue
        }
        if (!seenTexts.add(text.lowercase())) {
            continue
        }
        out.add(
            Candidate(
                id = if (cid.isNotEmpty()) "cm-$cid" else "cm-" + text.hashCode().toString(16),
                text = text,
                createTimeSec = comment.createTime(),
            ),
        )
        if (out.size >= CommentDanmakuPolicy.MAX_COMMENTS) {
            break
        }
    }
    return out
}

private val whitespaceRegex = Regex("\\s+")

/** 折叠换行/连续空白为单空格，并按上限截断。 */
private fun normalizeText(raw: String): String {
    val collapsed = raw.replace(whitespaceRegex, " ").trim()
    if (collapsed.length <= CommentDanmakuPolicy.MAX_TEXT_LENGTH) {
        return collapsed
    }
    return collapsed.take(CommentDanmakuPolicy.MAX_TEXT_LENGTH) + "…"
}

/**
 * 时间戳可用时按发布时间排序；否则保留接口顺序。
 *
 * 「可用」= 全部条目都有非零 create_time 且至少两个不同取值。只要有一条缺失就整体走兜底，
 * 避免把「无时间的条目」排到错误位置。
 */
private fun orderCandidates(candidates: List<Candidate>): List<Candidate> {
    val timed = candidates.all { it.createTimeSec > 0L }
    if (!timed) {
        return candidates
    }
    if (candidates.map { it.createTimeSec }.distinct().size < 2) {
        return candidates
    }
    return candidates.sortedBy { it.createTimeSec }
}

/**
 * 规划每条的出现时间。
 *
 * 时间戳模式：线性归一化 [最早, 最晚] → [windowStart, windowEnd]；
 * 兜底模式：等间距铺开。
 */
private fun planOffsets(
    ordered: List<Candidate>,
    windowStartMs: Long,
    windowEndMs: Long,
): List<Long> {
    if (ordered.size == 1) {
        return listOf(windowStartMs)
    }
    val timed = ordered.all { it.createTimeSec > 0L } &&
        ordered.map { it.createTimeSec }.distinct().size >= 2
    if (!timed) {
        val step = (windowEndMs - windowStartMs) / (ordered.size - 1)
        return ordered.indices.map { index -> windowStartMs + step * index }
    }
    val first = ordered.first().createTimeSec
    val span = ordered.last().createTimeSec - first
    val windowMs = (windowEndMs - windowStartMs).toDouble()
    return ordered.map { candidate ->
        val ratio = (candidate.createTimeSec - first).toDouble() / span.toDouble()
        windowStartMs + (windowMs * ratio).toLong()
    }
}

/**
 * 限流：保证相邻间隔与每秒条数，超出窗口的条目丢弃。
 *
 * 条目按计划时间单调递增；这里只会把条目往后推，不会打乱顺序。
 */
private fun applyRateLimit(
    ordered: List<Candidate>,
    planned: List<Long>,
    windowEndMs: Long,
): List<Pair<Candidate, Long>> {
    val perSecond = HashMap<Long, Int>()
    val out = ArrayList<Pair<Candidate, Long>>(ordered.size)
    var lastEmittedMs = Long.MIN_VALUE
    for (index in ordered.indices) {
        var timeMs = planned[index]
        if (lastEmittedMs != Long.MIN_VALUE) {
            timeMs = maxOf(timeMs, lastEmittedMs + CommentDanmakuPolicy.MIN_GAP_MS)
        }
        var second = timeMs / 1000L
        while (perSecond.getOrDefault(second, 0) >= CommentDanmakuPolicy.MAX_PER_SECOND) {
            timeMs = (second + 1L) * 1000L
            second = timeMs / 1000L
        }
        if (timeMs > windowEndMs) {
            // 本条推后已越过播放窗口：丢弃，不再继续往后堆，避免片尾突刺。
            continue
        }
        perSecond[second] = perSecond.getOrDefault(second, 0) + 1
        out.add(ordered[index] to timeMs)
        lastEmittedMs = timeMs
    }
    return out
}
