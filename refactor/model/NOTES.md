# axtv.model —— 数据模型层 重构说明

来源：`src/app/com/github/mytv/p004dv/model/`（133 个文件 = 45 个 `$$serializer` 脚手架 + **88 个**业务/工具类）
产出：`refactor/model/` 下 **85 个 Java 文件**、3577 行、覆盖 **87 个原类**
（唯一跳过：`C0507xa6a52f16` —— 它是 `JsonNames` 注解的 R8 内联实现壳，无业务语义）。

- 语言：Java 17（`javac --release 17` 零告警编译通过）
- 依赖：纯 JDK（`java.util`、`java.util.regex`、`java.util.Locale`）
- 形态：全部业务模型为 `record`（值语义，`equals/hashCode/toString` 与原 Kotlin `data class` 一致），
  有逻辑的类为 `final class` + 静态方法。
- JSON key：每个 record 分量上都标了 `@JsonKey`，主键取自反编译 `$$serializer` 的
  `SerialDescriptor` 元素名，备用键取自 `@c72(names = {...})`（kotlinx `JsonNames`）。

---

## 1. 类映射表

### 1.1 基础设施

| 原类 | 新类 | 行数 | 说明 |
|---|---|---|---|
| `FlexibleIntSerializer` + `FlexibleLongSerializer` + `FlexibleBooleanSerializer` | `FlexibleJson` | 171 | 三个 KSerializer 合并为一个静态解码器 |
| `DataModelKt` | `DataModels` | 378 | 计数格式化 / aweme 过滤 / 码率排序 / 直播选档 |
| `VideoPlayUrlResolverKt` | 并入 `VideoPlayUrlResolver` | — | 只是转发到 `INSTANCE` 的顶层函数壳 |
| `LiveRoomUtilsKt` | `LiveRoomUtils` | 28 | 观看人数展示 |
| （无） | `JsonKey` | 34 | 替代 `SerialName` / `JsonNames` 的纯标记注解 |
| `C0507xa6a52f16` | 不再需要 | — | 是 `JsonNames` 注解的 R8 内联实现类，无业务语义 |

### 1.2 视频核心

| 原类 | 新类 | 行数 |
|---|---|---|
| `Aweme` | `Aweme` | 70 |
| `Video` | `Video` | 71 |
| `Author` | `Author` | 61 |
| `Avatar` | `Avatar` | 27 |
| `Cover` | `Cover` | 28 |
| `Statistics` | `Statistics` | 27 |
| `PlayProgress` | `PlayProgress` | 22 |
| `BitRate` | `BitRate` | 53 |
| `PlayAddr` | `PlayAddr` | 43 |
| `VideoBigThumb` | `VideoBigThumb` | 33 |
| `VideoPlayUrlResolver` | `VideoPlayUrlResolver` | 465 |
| `MixInfo` | `MixInfo` | 33 |
| `MixStatis` | `MixStatis` | 25 |

### 1.3 评论 / 动态流

| 原类 | 新类 | 行数 |
|---|---|---|
| `Comment` | `Comment` | 57 |
| `CommentListResponse` | `CommentListResponse` | 37 |
| `FeedResponse` | `FeedResponse` | 49 |
| `FeedDataItem` | `FeedDataItem` | 16 |

### 1.4 用户

| 原类 | 新类 | 行数 |
|---|---|---|
| `UserProfileResponse` | `UserProfileResponse` | 19 |
| `UserWorksResponse` | `UserWorksResponse` | 46 |
| `UserListResponse` | `UserListResponse` | 39 |

### 1.5 搜索

| 原类 | 新类 | 行数 |
|---|---|---|
| `SearchResponse` | `SearchResponse` | 50 |
| `SearchItem` | `SearchItem` | 19 |
| `SearchNilInfo` | `SearchNilInfo` | 30 |
| `LogPb` | `LogPb` | 20 |
| `SearchUserResponse` | `SearchUserResponse` | 35 |
| `SearchUserItem` | `SearchUserItem` | 23 |
| `SearchUserPage` | `SearchUserPage` | 25 |
| `SearchLiveResponse` | `SearchLiveResponse` | 35 |
| `SearchLiveData` | `SearchLiveData` | 16 |
| `SearchLiveLives` | `SearchLiveLives` | 27 |
| `SearchLivePage` | `SearchLivePage` | 25 |
| `SearchVideoWorkResponse` | `SearchVideoWorkResponse` | 40 |
| `SearchVideoWorkData` | `SearchVideoWorkData` | 20 |
| `SearchSugResponse` | `SearchSugResponse` | 25 |
| `SearchSugItem` | `SearchSugItem` | 24 |
| `SuggestWordsResponse` | `SuggestWordsResponse` | 25 |
| `SuggestWordGroup` | `SuggestWordGroup` | 22 |
| `SuggestWord` | `SuggestWord` | 23 |
| `HotSearchResponse` | `HotSearchResponse` | 17 |
| `HotSearchData` | `HotSearchData` | 22 |
| `HotWord` | `HotWord` | 30 |
| `SeoLinkResponse` | `SeoLinkResponse` | 23 |
| `SeoLink` | `SeoLink` | 24 |

### 1.6 直播

| 原类 | 新类 | 行数 |
|---|---|---|
| `LiveRoom` | `LiveRoom` | 61 |
| `LiveRoomData` | `LiveRoomData` | 23 |
| `LiveRoomResponse` | `LiveRoomResponse` | 17 |
| `LiveRoomEnterData` | `LiveRoomEnterData` | 27 |
| `LiveRoomEnterResponse` | `LiveRoomEnterResponse` | 23 |
| `LiveFeedData` | `LiveFeedData` | 22 |
| `LiveFeedResponse` | `LiveFeedResponse` | 17 |
| `LiveFeedDataHome` | `LiveFeedDataHome` | 23 |
| `LiveFeedResponseHome` | `LiveFeedResponseHome` | 24 |
| `StreamUrl` | `StreamUrl` | 37 |
| `LiveCoreSdkData` | `LiveCoreSdkData` | 16 |
| `LiveCorePullData` | `LiveCorePullData` | 20 |
| `LiveCorePullOptions` | `LiveCorePullOptions` | 23 |
| `LiveStreamQuality` | `LiveStreamQuality` | 32 |
| `RoomViewStats` | `RoomViewStats` | 47 |
| `CameraInfo` | `CameraInfo` | 28 |
| `EpisodeExtra` | `EpisodeExtra` | 29 |
| `ResolvedLiveStream` | `ResolvedLiveStream` | 25 |
| `ResolvedStreamQuality` | `ResolvedStreamQuality` | 27 |
| `LiveRoomUtilsKt` | `LiveRoomUtils` | 28 |
| `LiveDanmakuMessage` | `LiveDanmakuMessage` | 49 |
| `LiveDanmakuSettings`（+ `p000.an2`） | `LiveDanmakuSettings`（+ `DanmakuStyle`） | 79 |

### 1.7 弹幕 / 表情 / 其它

| 原类 | 新类 | 行数 |
|---|---|---|
| `DanmakuItem` | `DanmakuItem` | 56 |
| `DanmakuResponse` | `DanmakuResponse` | 25 |
| `EmojiItem` | `EmojiItem` | 32 |
| `EmojiUrl` | `EmojiUrl` | 26 |
| `EmojiListResponse` | `EmojiListResponse` | 27 |
| `ChannelItem` | `ChannelItem` | 27 |
| `ChannelSettingResponse` | `ChannelSettingResponse` | 24 |
| `CollectItem` | `CollectItem` | 26 |
| `CollectListResponse` | `CollectListResponse` | 31 |
| `NoticeItem` | `NoticeItem` | 33 |
| `NoticeListResponse` | `NoticeListResponse` | 35 |
| `MixListResponse` | `MixListResponse` | 30 |
| `MixDetailResponse` | `MixDetailResponse` | 20 |
| `MixPlaylistPagination` | `MixPlaylistPagination` | 30 |
| `WorkDetailResponse` | `WorkDetailResponse` | 18 |
| `InteractionMutationResponse` | `InteractionMutationResponse` | 29 |
| `ApiStatusResponse` | `ApiStatusResponse` | 27 |

---

## 2. 字段总览表

> 「来源」列：**实证** = 反编译 `$$serializer.java` 的 descriptor 里能读到该 key；
> **推定** = 该类的 `$$serializer` 未被 jadx 导出，按同模块 snake_case 惯例推得（见「存疑 S1」）。

### 2.1 Aweme（`aweme_id` 为 entry 主键）

| 字段 | JSON key | 类型 | 含义 | 来源 |
|---|---|---|---|---|
| awemeId | `aweme_id` | String | 作品 id | 实证 |
| desc | `desc` | String | 文案/标题 | 实证 |
| video | `video` | Video | 播放信息 | 实证 |
| author | `author` | Author | 作者 | 实证 |
| statistics | `statistics` | Statistics | 互动数据 | 实证 |
| createTime | `create_time` | long | 发布秒级时间戳 | 实证 |
| awemeType | `aweme_type` | int | 0 = 普通视频（可播） | 实证 |
| playProgress | `play_progress` | PlayProgress | 服务端进度 | 实证 |
| userDigged | `user_digged`（备用 `is_digg`、`digg_status`） | long | 是否已点赞，1 = 是 | 实证 |
| collectStat | `collect_stat`（备用 `collect_status`、`is_collect`、`is_collected`、`is_favorited`） | long | 是否已收藏，1 = 是 | 实证 |
| authenticationToken | `authentication_token` | String | 播放鉴权 token | 实证 |
| mixInfo | `mix_info` | MixInfo（可空） | 所属合集 | 实证 |

### 2.2 Video

| 字段 | JSON key | 类型 | 含义 |
|---|---|---|---|
| bitRate | `bit_rate` | List\<BitRate> | 多码率列表 |
| playAddr | `play_addr` | PlayAddr | H.264 主地址 |
| playAddr265 | `play_addr_265` | PlayAddr | H.265 主地址 |
| downloadAddr | `download_addr` | PlayAddr | 下载地址 |
| cover | `cover` | Cover | 静态封面 |
| dynamicCover | `dynamic_cover` | Cover | 动态封面 |
| originCover | `origin_cover` | Cover | 原始封面 |
| bigThumbs | `big_thumbs` | List\<VideoBigThumb> | 进度条缩略图雪碧图 |
| duration | `duration` | int | 时长（毫秒） |
| width / height | `width` / `height` | int | 画幅 |
| ratio | `ratio` | String | 比例字符串 |
| authenticationToken | `authentication_token` | String | 鉴权 token |

### 2.3 BitRate / PlayAddr / VideoBigThumb

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| BitRate | gearName | `gear_name` | 档位名，如 `normal_1080_0`、`adapt_lowest_1080_1`；选档按「包含 1080/720/540」匹配 |
| | bitRateValue | `bit_rate` | 码率 bps |
| | qualityType | `quality_type` | 画质类型 |
| | isH265 | `is_h265` | 是否 H.265，1 = 是（保持 int，原码全程 `== 1`） |
| | isBytevc1 | `is_bytevc1` | bytevc1（H.265 的另一种标记），1 = 是 |
| | fps | `FPS` | 帧率（key 全大写，容易踩坑） |
| | format | `format` | 容器格式 |
| | playAddr | `play_addr` | 该档位地址 |
| PlayAddr | urlList | `url_list` | 同一路流的多个候选地址 |
| | uri | `uri` | 资源 uri |
| | width / height | `width` / `height` | 该路流分辨率（选流按此排序） |
| | dataSize | `data_size` | 文件字节数 |
| VideoBigThumb | imgUrls | `img_urls` | 雪碧图地址 |
| | imgXSize / imgYSize | `img_x_size` / `img_y_size` | 单格尺寸 |
| | imgXLen / imgYLen | `img_x_len` / `img_y_len` | 横/纵格数 |
| | imgNum | `img_num` | 总格数 |

### 2.4 Author / Avatar / Cover / Statistics / PlayProgress

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| Author | uid | `uid` | 用户数字 id |
| | nickname | `nickname` | 昵称 |
| | avatarThumb / avatarMedium / avatarLarger | `avatar_thumb` / `avatar_medium` / `avatar_larger` | 三档头像 |
| | secUid | `sec_uid` | 加密 id（接口参数常用） |
| | signature | `signature` | 签名 |
| | followerCount | `follower_count` | 粉丝数 |
| | followingCount | `following_count` | 关注数 |
| | totalFavorited | `total_favorited` | 获赞总数 |
| | awemeCount | `aweme_count` | 作品数 |
| | favoritingCount | `favoriting_count` | 收藏数 |
| Avatar / Cover | urlList | `url_list` | 图片候选地址 |
| Statistics | diggCount / commentCount / shareCount / collectCount / playCount | `digg_count` / `comment_count` / `share_count` / `collect_count` / `play_count` | 互动计数（全部柔性 long） |
| PlayProgress | lastModifiedTime | `last_modified_time` | 记录修改时间 |
| | playProgress | `play_progress` | 看到的位置（毫秒） |
| | watchTimes | `watch_times` | 观看次数，**可空 Long**（null ≠ 0） |

### 2.5 Comment / CommentListResponse

| 字段 | JSON key | 含义 |
|---|---|---|
| cid | `cid` | 评论 id（descriptor 里用的是 media3 常量 `CmcdConfiguration.KEY_CONTENT_ID`，值就是 `cid`） |
| text | `text` | 内容 |
| user | `user` | 评论者 |
| diggCount | `digg_count` | 点赞数 |
| replyCommentTotal | `reply_comment_total` | 回复总数 |
| createTime | `create_time` | 发布时间 |
| awemeId | `aweme_id` | 所属作品 |
| ipLabel | `ip_label` | IP 属地 |
| replyComments | `reply_comment` | 内嵌回复，**可空** |
| isUserDigg | `is_user_digg` | 是否已赞（0/1） |
| *(CommentListResponse)* comments | `comments`（推定） | 列表 |
| hasMore | `has_more`（推定） | 0/1 |
| cursor | `cursor`（推定） | 游标 |
| total | `total`（推定） | 总数 |
| statusCode | `status_code`（推定） | 状态码 |

### 2.6 Feed

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| FeedResponse | awemeList | `aweme_list`（推定） | 老形态作品列表 |
| | data | `data`（推定） | 新形态 `[{aweme:{...}}]` |
| | hasMore | `has_more`（推定） | 柔性布尔 |
| | maxCursor / cursor | `max_cursor` / `cursor`（推定） | 翻页游标 |
| | statusCode | `status_code`（推定） | 状态码 |
| FeedDataItem | aweme | `aweme`（实证） | 作品包装，可空 |

### 2.7 用户

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| UserProfileResponse | user | `user`（推定*） | 用户资料 |
| | statusCode | `status_code`（推定*） | 状态码 |
| UserWorksResponse | awemeList / items | `aweme_list` / `items`（推定） | 两种作品形态 |
| | hasMore | `has_more`（推定） | 柔性布尔 |
| | maxCursor / cursor | `max_cursor` / `cursor`（推定） | 游标，cursor 可空 |
| | awemeDate | `aweme_date`（推定） | 作品 id → 时间戳 |
| | statusCode | `status_code`（推定） | 状态码 |
| UserListResponse | followings / followers | `followings` / `followers`（推定） | 关注/粉丝列表 |
| | hasMore | `has_more`（推定） | 柔性布尔 |
| | total | `total`（推定） | 总数 |
| | minTime / maxTime | `min_time` / `max_time`（推定） | 时间窗口，柔性 long |
| | offset | `offset`（推定） | 偏移 |
| | statusCode | `status_code`（推定） | 状态码 |

（\*）`UserProfileResponse` 的 `$$serializer` 同样未导出，key 为推定。

### 2.8 搜索

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| SearchResponse | data | `data`（推定） | 搜索结果条目 |
| | awemeList | `aweme_list`（推定） | 平铺作品 |
| | hasMore | `has_more`（推定） | 柔性布尔 |
| | cursor | `cursor`（推定） | 游标 |
| | statusCode / statusMsg | `status_code` / `status_msg`（推定） | 状态 |
| | searchNilInfo | `search_nil_info`（推定） | 空结果提示 |
| | logPb | `log_pb`（推定） | 埋点 |
| SearchItem | awemeInfo / type | `aweme_info` / `type`（实证） | 条目 |
| SearchNilInfo | searchNilType / isLoadMore / searchNilItem / textType | `search_nil_type` / `is_load_more` / `search_nil_item` / `text_type`（实证） | 注意 `is_load_more` 是**字符串** |
| LogPb | imprId | `impr_id`（实证） | 曝光 id |
| SearchUserResponse | userList / hasMore / cursor / statusCode / logPb | `user_list` / `has_more` / `cursor` / `status_code` / `log_pb`（推定） | 用户搜索 |
| SearchUserItem | userInfo / rawAdCommon | `user_info` / `raw_ad_common`（实证） | 用户条目 |
| SearchLiveResponse | data / hasMore / cursor / statusCode / logPb | `data` / `has_more` / `cursor` / `status_code` / `log_pb`（推定） | 直播搜索 |
| SearchLiveData | lives | `lives`（实证） | 包装 |
| SearchLiveLives | awemeId / author / rawdata | `aweme_id` / `author` / `rawdata`（实证） | `rawdata` 是**原始 JSON 字符串** |
| SearchVideoWorkResponse | cursor / data / hasMore / logPb / searchNilInfo / statusCode / statusMsg | 同名字段（推定） | 作品聚合搜索 |
| SearchVideoWorkData | awemeInfo | `aweme_info`（实证） | 包装 |
| SearchSugResponse | sugList / statusCode | `sug_list` / `status_code`（推定） | 联想词 |
| SearchSugItem | content / sugType | `content` / `sug_type`（实证） | 联想条目 |
| SuggestWordsResponse | data / statusCode | `data` / `status_code`（推定） | 猜你想搜 |
| SuggestWordGroup | words | `words`（实证） | 词组 |
| SuggestWord | word / wordType | `word` / `word_type`（实证） | 词条 |
| HotSearchResponse | data / statusCode | `data` / `status_code`（推定） | 热搜 |
| HotSearchData | wordList | `word_list`（实证） | 热搜词列表 |
| HotWord | word / hotValue / position / sentenceId / label | `word` / `hot_value` / `position` / `sentence_id` / `label`（实证） | 热搜词 |
| SeoLinkResponse | data / statusCode | `data` / `status_code`（推定） | SEO 链接 |
| SeoLink | link / title | `link` / `title`（实证） | 链接 |
| SearchLivePage / SearchUserPage | — | 无（内存态分页对象） | 不发 JSON |

### 2.9 直播

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| LiveRoom | roomId / idStr / enterRoomId / webRid | `room_id` / `id_str` / `enter_room_id` / `web_rid`（实证） | 房间标识 |
| | title / cover | `title` / `cover`（实证） | 标题封面 |
| | userCount | `user_count_str`（实证） | **字符串**人数文案，默认 `"0"` |
| | roomViewStats | `room_view_stats`（实证） | 精细人数展示 |
| | owner | `owner`（实证） | 主播 |
| | streamUrl | `stream_url`（实证） | 拉流地址 |
| | status | `status`（实证） | 2 = 直播中 |
| | cameraInfos / episodeExtra | `camera_infos` / `episode_extra`（实证） | 多机位 |
| RoomViewStats | isHidden / displayShort / displayMiddle / displayLong / displayValue / displayVersion / incremental / displayType / displayShortAnchor / displayMiddleAnchor / displayLongAnchor | `is_hidden` / `display_short` / `display_middle` / `display_long` / `display_value` / `display_version` / `incremental` / `display_type` / `display_short_anchor` / `display_middle_anchor` / `display_long_anchor`（实证） | 人数展示 |
| StreamUrl | flvPullUrl / hlsPullUrlMap / liveCoreSdkData | `flv_pull_url` / `hls_pull_url_map` / `live_core_sdk_data`（实证） | 两个 Map **可空** |
| LiveCoreSdkData | pullData | `pull_data`（实证） | 拉流数据 |
| LiveCorePullData | options / streamData | `options` / `stream_data`（实证） | JSON 树（可能是字符串），用 Object 承载 |
| LiveStreamQuality | level / sdkKey / vCodec / name / resolution | `level` / `sdk_key` / `v_codec` / `name` / `resolution`（实证） | `level` 柔性 int |
| CameraInfo | cameraIdStr / title / cameraType / streamInfo | `camera_id_str` / `title` / `camera_type` / `stream_info`（实证） | 机位 |
| EpisodeExtra | cameraInfos / defaultCameraIdStr / cameraInfosTableTitle | `camera_infos` / `default_camera_id_str` / `camera_infos_table_title`（实证） | 剧集附加 |
| LiveRoomData | webRid / room | `web_rid` / `room`（实证） | 列表项 |
| LiveFeedData | data | `data`（实证） | 流数据 |
| LiveFeedDataHome | webRid / data | `web_rid` / `data`（实证） | 首页流项 |
| LiveRoomEnterData | data | `data`（实证） | 进房 data |
| LiveRoomResponse / LiveFeedResponse / LiveFeedResponseHome / LiveRoomEnterResponse | data / statusCode | `data` / `status_code`（推定） | 响应壳 |
| LiveDanmakuMessage | messageId / userName / content / timestamp / userId / level / isVip / badgeName / badgeLevel / type / onlineCount | 无（WS 解析产物，非 JSON 直出） | type 默认 `"chat"` |
| LiveDanmakuSettings | enabled / emojiEnabled / style / speed / fontSize / opacity / displayAreaPercent / verticalSpacingScale / horizontalSpacingScale / filterPackageComment / blockEnabled / blockWords | 无（本地 SharedPreferences 模型） | 默认值见类注释 |

### 2.10 弹幕 / 表情 / 合集 / 其它

| 类 | 字段 | JSON key | 含义 |
|---|---|---|---|
| DanmakuItem | danmakuId / itemId / userId / offsetTime / text / status / diggType / score / danmakuType / diggCount / showDigg / showCopy / fromCopy / danmakuTypeBits / hasEmoji / dislikeType / isAd | `danmaku_id` / `item_id` / `user_id` / `offset_time` / `text` / `status` / `digg_type` / `score` / `danmaku_type` / `digg_count` / `show_digg` / `show_copy` / `from_copy` / `danmaku_type_bits` / `has_emoji` / `dislike_type` / `is_ad`（全部实证） | 录播弹幕 |
| DanmakuResponse | danmakuList / statusCode | `danmaku_list` / `status_code`（推定） | 弹幕接口 |
| EmojiItem | displayName / originUri / hide / emojiUrl | `display_name` / `origin_uri` / `hide` / `emoji_url`（实证） | 表情 |
| EmojiUrl | uri / urlList | `uri` / `url_list`（实证） | 表情图 |
| EmojiListResponse | emojiList / statusCode / version | `emoji_list` / `status_code` / `version`（推定） | 表情列表（version 用于缓存失效） |
| MixInfo | mixId / mixName / desc / coverUrl / statis | `mix_id`（备用 `series_id`）/ `mix_name`（备用 `series_name`）/ `desc` / `cover_url` / `statis`（实证） | 合集 |
| MixStatis | currentEpisode / updatedToEpisode / totalEpisode / playVv / collectVv | `current_episode` / `updated_to_episode` / `total_episode` / `play_vv` / `collect_vv`（实证） | 合集进度 |
| MixListResponse | mixInfos / hasMore / cursor / statusCode | `mix_infos`（实证，来自 @JsonNames）/ `has_more` / `cursor`（备用 `next_cursor`，实证同源）/ `status_code` | 合集列表 |
| MixDetailResponse | mixInfo / statusCode | `mix_info`（备用 `series_info`，实证同源）/ `status_code` | 合集详情 |
| MixPlaylistPagination | mixId / pages / nextCursor / hasMore | 无（内存态） | 合集播放分页 |
| NoticeItem | noticeId / noticeType / content / createTime / user / aweme | `notice_id` / `notice_type` / `content` / `create_time` / `user` / `aweme`（实证） | 通知 |
| NoticeListResponse | noticeList / hasMore / minTime / maxTime / statusCode | 同名 snake_case（推定） | 通知列表 |
| CollectItem | collectId / collectName / collectCount | `collect_id` / `collect_name` / `collect_count`（实证） | 收藏夹 |
| CollectListResponse | collectsList / hasMore / statusCode | `collects_list` / `has_more` / `status_code`（推定） | 收藏夹列表 |
| ChannelItem | chName / tagId / chId | `ch_name` / `tag_id` / `ch_id`（实证） | 频道 |
| ChannelSettingResponse | data / statusCode | `data` / `status_code`（推定） | 频道列表 |
| WorkDetailResponse | awemeDetail / statusCode | `aweme_detail` / `status_code`（推定） | 作品详情 |
| InteractionMutationResponse | isDigg / isCollect / statusCode / statusMsg | `is_digg` / `is_collect` / `status_code` / `status_msg`（推定） | 点赞/收藏回执；两个状态**可空 Integer** |
| ApiStatusResponse | statusCode / statusMsg | `status_code` / `status_msg`（推定） | 纯状态壳 |
| ResolvedLiveStream | urls / qualityName | 无（计算结果） | 选流结果 |
| ResolvedStreamQuality | name / level / urls | 无（计算结果） | 单档结果 |

---

## 3. VideoPlayUrlResolver —— 选流规则

### 3.1 入口

| 入口 | 场景 |
|---|---|
| `resolveFinalPlayUrl(Video, TargetResolution)` | 播放页，传入作品的 video |
| `resolveFinalPlayUrl(Video, TargetResolution, Aweme)` | 播放页，同时有 aweme；**aweme 的 video 优先**（见 3.2） |
| `resolveH264PlayUrl(Video, TargetResolution)` | 强制 H.264（软解/老设备） |
| `selectCandidateUrls(...)` | 只拿候选地址列表，不挑最终 url |
| `selectBestPlayUrl(List<String>)` | 从候选里挑一条 |

### 3.2 aweme 覆盖规则（仅三参入口）

1. `playAddr = aweme.video.playAddr`；其 `url_list` 为空时回退到入参 `video.playAddr`
2. `playAddr265` 同理回退
3. `bitRate` 用 `aweme.video.bitRate`，**为空时**才用入参 `video.bitRate`

### 3.3 目标清晰度 `TargetResolution` 解析顺序

1. 方法入参（非 null 直接返回）
2. Settings 的运行时值 `Settings.targetResolution()`
3. SharedPreferences `video_resolution` 原值按 `prefValue` 反查
   （`auto` / `highest` / `1080p` / `720p` / `480p`）
4. 都拿不到 → `AUTO`

| 枚举 | prefValue | shortLabel | 说明 |
|---|---|---|---|
| AUTO | `auto` | 自动 | 直接用主地址，不查 bit_rate |
| HIGHEST | `highest` | 最高 | 码率列表里画质最高那条 |
| P1080 | `1080p` | 1080P | gear_name 含 `1080` |
| P720 | `720p` | 720P | gear_name 含 `720` |
| P540 | `480p` | 540P | gear_name 含 `540`（注意 pref 值写 `480p`，显示写 `540P`，原始如此） |

### 3.4 编码过滤（三步，顺序不能换）

1. **设备能力过滤**：`fitsDeviceDecoder(br)` —— `br.playAddr` 的宽高必须 ≤ 该编码的解码上限。
   宽高任一 ≤ 0 或查不到上限时**一律放行**（宁可给可能播不了的档，也不要把列表筛空）。
2. **编码偏好过滤**（`CodecPreference`：AUTO / H264 / H265）：
   - AUTO → 不过滤
   - H264 → 只保留 `isH265 != 1`
   - H265 → 只保留 `isH265 == 1`
3. **forceH264 收窄**：`resolveH264PlayUrl` 时编码偏好强制 H264，并且把「设备能解」这一档
   也替换成 H264 结果，避免后续兜底又拿到 HEVC。

得到两个列表：
- `byCodec`：设备能解 ∩ 编码偏好 —— **优先用它选档**
- `decoderFits`：设备能解（forceH264 时等于 `byCodec`）—— **兜底用它再选一次**

### 3.5 按清晰度选档

| 目标 | 规则（按顺序尝试，命中即返回） |
|---|---|
| AUTO | ① 编码偏好 ≠ H265 → 返回 `play_addr.url_list`<br>② `play_addr_265` 超设备能力 → 返回 `play_addr.url_list`<br>③ `play_addr_265.url_list` 非空 → 返回它，否则 `play_addr.url_list` |
| HIGHEST | ① `DataModels.highestQualityBitRate(byCodec)` 有结果 → 用它的 url_list<br>② 同上用 `decoderFits`<br>③ 否则 `play_addr.url_list` |
| P1080/P720/P540 | ① 在 `byCodec` 里找**第一条** gear_name 含目标数字的码率，url_list 非空则用<br>② 同上在 `decoderFits` 里找<br>③ 否则 `play_addr.url_list` |

> **关键怪癖（必须保留）**：gear 匹配只检查「第一条命中的码率」，如果它的 `url_list` 为空就直接返回
> null，**不会继续往后找第二条**。改成「继续找」会导致选到与原 App 不同的档位。

### 3.6 `highestQualityBitRate` 排序键（逐级比较，取最大）

| 优先级 | 比较项 | 方向 |
|---|---|---|
| 1 | `play_addr.width * play_addr.height` | 越大越好 |
| 2 | `FPS` | 越大越好 |
| 3 | 编码分（HEVC = 1，H.264 = 0） | HEVC 优先 |
| 4 | `bit_rate`（码率 bps） | 越大越好 |
| 5 | gear_name 是否含 `adapt_lowest` | **不含**的优先 |

### 3.7 最终 URL 挑选（`selectBestPlayUrl`）

| 优先级 | 判据 | 理由 |
|---|---|---|
| 1 | 含 `douyinvod.com` **且**路径匹配 `douyinvod\.com/[0-9a-f]{32}/[0-9a-f]{8}/` | 已带路径签名的 CDN 直链，无需二次请求 |
| 2 | 含 `douyin.com/aweme/v1/play` | 官方 play 接口，会 302 到可用 CDN |
| 3 | 同时含 `signature=` 与 `douyinvod.com` | 查询参数签名版直链 |
| 4 | 列表**最后一条**（原 `lastOrNull`） | 兜底 |
| — | 列表为空 → 返回 `""` | |

### 3.8 与原实现的依赖对照

| 原依赖 | 新形态 |
|---|---|
| `MediaCodecList` 查询 `video/hevc`、`video/avc` 的解码上限（取「宽×高」最大的一组） | `DecoderCapabilities` 接口；Android 侧实现见接口 javadoc |
| `fl4`(SettingsManager) 的 `f6266n`/`f6274r` StateFlow + SharedPreferences `video_resolution` | `Settings` 接口三个方法 |
| `init(Context)` + 后台线程查解码能力 | `init(Settings, DecoderCapabilities)`，注入即可，去掉了线程 |

---

## 4. FlexibleJson —— 柔性解码规则

原实现的输入是 kotlinx `JsonPrimitive`。本层接收**已解析的 JSON 值**（`null` / `Boolean` / `Number` / `String`），
先按 kotlinx `content` 语义取「token 文本」，再解析：

| 输入 | token 文本 |
|---|---|
| `"123"`（JSON 字符串） | `123`（引号去掉，所以字符串数字能解析） |
| `123` / `1.5`（JSON 数字） | `123` / `1.5`（源字面量） |
| `true` | `true` |
| `null` | 无文本 → 默认值 |
| 对象 / 数组 | 无文本（原实现抛异常被吞）→ 默认值 |

### 4.1 int

| token 文本 | 结果 |
|---|---|
| 合法整数且在 int 范围 | 该值 |
| 合法整数但越界（如 `99999999999`） | **`(int)` 截断**（原实现溢出后强转） |
| `"12.0"`、`"abc"`、`" 7"`（带空格）、`""`、`true` | 0 |
| null | 0 |

### 4.2 long

| token 文本 | 结果 |
|---|---|
| `[+-]?[0-9]+` 整串匹配 | 该值 |
| 其余（含 `"1.5"`、`" 7"`、空串、`true`） | 0 |
| null | 0 |

> 原实现是「先 kotlinx 严格 long 解析，失败再 `toLongOrNull`」，两者在 Java 里都等价于
> `Long.parseLong`，所以合并为一次解析，不会出现小数截断。

### 4.3 boolean

| token 文本 | 结果 |
|---|---|
| 恰好 `"true"` 或 `"1"` | **true** |
| `"TRUE"`、`"True"`、`"true "`、`"yes"`、`"0"`、`false`、`0`、`1.0` | false |
| null | false |

> 注意大小写敏感、且 `1.0` 是 false（字面量文本是 `"1.0"` 不等于 `"1"`）。
> 原代码写了一个恒等三元表达式（两分支完全一样），说明作者本意是「字符串/非字符串分别判断」，
> 但实际行为就是字符串相等判断，这里按实际行为还原。

### 4.4 写回方向

`writeInt/writeLong/writeBoolean` 原样返回，对应原实现的 `encodeInt/encodeLong/encodeBoolean`
（即输出 JSON 数字/布尔，不回写成字符串）。

---

## 5. 行为清单（本模块对外提供什么）

1. **79 个值对象**，字段与 JSON key 一一对应，`equals/hashCode/toString` 与原 `data class` 等价。
2. **播放地址决策**：`VideoPlayUrlResolver` 三个入口 + 候选列表/最终挑选两个子步骤，
   规则与原实现逐分支对齐（含 3.5/3.6 的排序键与怪癖）。
3. **柔性解码**：`FlexibleJson` 三类标量的完整转换表。
4. **模型工具**：`DataModels.formatCount`（亿/万/k）、`isPrimaryAweme` / `takeIfPrimaryAweme` /
   `filterPrimaryAwemes` / `filterPrimaryPlayableAwemes`、`highestQualityBitRate`。
5. **直播选档**：`selectQualityIndex`、`resolveLiveStreamBySdkData`、
   `resolveLiveStreamForQuality`、`resolveLiveStreamWithFallback`、`resolveSdkQualities`（纯计算部分）。
6. **展示工具**：`LiveRoomUtils.displayViewerCount`、`LiveDanmakuSettings.displayArea`、
   `ResolvedStreamQuality.toResolvedLiveStream`（空白名 → 「默认」）、`ResolvedLiveStream.firstUrl`。
7. **便捷归一化**：`FeedResponse.flatAwemes()` / `SearchResponse.flatAwemes()` /
   `UserWorksResponse.works()` / `Video.isPlayable()` / `Aweme.isDigged()/isCollected()/hasMix()` 等
   —— 都是把原代码里重复出现的判空/取首元素收敛成方法，语义不变。

---

## 6. 待迁移（依赖 Android / JSON 库，核心层未落地）

| # | 内容 | 原位置 | 处理方式 |
|---|---|---|---|
| M1 | 设备解码能力查询 | `VideoPlayUrlResolver.queryDecoderMaxDims()` | 需 Android 侧实现 `DecoderCapabilities`：遍历 `MediaCodecList(REGULAR_CODECS)`，跳过 encoder，对 `video/hevc`、`video/avc` 分别取所有解码器 `VideoCapabilities` 的 width/height upper bound，按「宽×高」取最大一组；查不到返回 0 |
| M2 | 清晰度/编码偏好的持久化 | `fl4.f6266n`、`fl4.f6274r`、`SharedPreferences("video_resolution")` | 需 settings 模块实现 `VideoPlayUrlResolver.Settings` |
| M3 | embedded JSON 字符串解包 | `DataModelKt.unwrapEmbeddedJsonStringOrNull` | 规则：值是字符串且 trim 后以 `{`/`[` 开头 → 再解析一次；失败视为 null。已由 `axtv.api.ModelJsonCodec` 在绑定 `Object` 字段时实现 |
| M4 | `options.qualities` 解析 | `DataModelKt.extractQualities` | 先尝试「解包字符串 → 反序列化为 `List<LiveStreamQuality>`」，失败再直接按对象反序列化；异常时返回空表 |
| M5 | SDK 档位地址提取 | `DataModelKt.resolveSdkQualities` 的 JSON 导航部分 | 路径 `stream_data.data[quality.sdk_key].main.{flv,hls}`；`data` 节点缺失时退化为「按 level 从 flv/hls 的 values 末尾倒取」。已拆成纯计算版 `DataModels.resolveSdkQualities(...)`，由调用方先做 JSON 导航 |
| M6 | `LiveCorePullData.options/streamData` 的具体类型 | kotlinx `JsonElement` | 现为 `Object`（已解析的 JSON 树），由 `ModelJsonCodec` 解包为 `Map`/`List`/标量 |
| M7 | 解码能力查询的异步初始化 | `init()` 里 `new Thread(...)` | 现为同步注入；Android 侧若查询耗时可在子线程完成后调用 `init(null, caps)` |
| M8 | `LiveDanmakuSettings` 的 SharedPreferences 读写 | 由 settings 模块负责，本层只保留默认值 | — |

## 7. 存疑

| # | 问题 | 说明 |
|---|---|---|
| S1 | **27 个响应类的 JSON key 是推定的** | 这些类的 `$$serializer` 在反编译产物里**整体缺失**（`awk` 统计 model 目录只有 45 个 `$$serializer`，但 72 个类实现了 `write$Self`），所以无法从 descriptor 读到真实 key。推定依据：同目录所有能读到 key 的类都是 camelCase → snake_case（`aweme_id`、`create_time`、`user_count_str`…）。涉及：ApiStatusResponse 之外的 `*Response` 壳类（详见 2.x 表中标「推定」的行）。**建议真机抓包复核 status_code / status_msg / has_more / log_pb 这几个高频键。** |
| S2 | JSON 解析配置 | `DataModelKt.LenientJson` 的 builder 只设了两个布尔（`ignoreUnknownKeys`、`isLenient` 的高可能性推断，另有 `coerceInputValues` 的可能）。若是 `coerceInputValues = true`，则「字段显式给 null」会回落默认值；否则对非空属性给 null 会抛异常。本层按「null → 默认值」处理（更宽容）。需 api 层确认 |
| S3 | `Comment.cid` 的 key | descriptor 用的是 media3 常量 `CmcdConfiguration.KEY_CONTENT_ID`，反编译没内联成字面量。该常量值是 `"cid"`，已采用 |
| S4 | `BitRate` 的 `FPS` key | descriptor 里就是大写 `"FPS"`，不是 `fps`。已按实证保留，但**与其他字段风格不一致**，抓包时留意 |
| S5 | H.264 过滤只看 `isH265` | `isH265 != 1` 即可通过 H.264 过滤，**不看 `isBytevc1`**。理论上「isH265=0 但 isBytevc1=1」的码率会被当成 H.264 选中。这是原实现的行为，已原样保留 |
| S6 | `formatCount` 的 Locale | 原实现用默认 Locale 的 `String.format`，小数点符号随系统区域变化。已保留默认 Locale（未改 `Locale.ROOT`）以维持行为一致 |
| S7 | `MixInfo` / `MixListResponse` 的主键 | 主键取 `mix_id`/`mix_name`/`cursor`，`series_id`/`series_name`/`next_cursor` 作为备用键（`@c72` 是 `JsonNames` 备用名，不是主名） |
| S8 | `LiveRoom.status` 的取值含义 | 只从代码里看到被读取，未找到常量定义；「2 = 直播中」是从抖音公开协议推断，代码内无法证实 |
| S9 | `ResolvedStreamQuality` 可见性 | 原为包私有 `final class`，现提升为 public（直播模块跨包需要） |
| S10 | `SearchNilInfo.isLoadMore` | 类型确实是 **String**（不是布尔），字段名有误导性，已保留原语义 |

---

## 8. 编译验证

```
cd refactor/model && javac --release 17 -Xlint:all -d out *.java
```

无错误、无告警。另用一段临时 main 跑过选流冒烟：

| 用例 | 期望 | 实际 |
|---|---|---|
| 1080p 目标，三档码率 | 选中 gear 含 1080 的码率 | ✓（返回其已签名直链） |
| 720p / 540p 目标 | 各自档位 | ✓ |
| 最高画质（1080 为 HEVC+60fps） | 1080 | ✓ |
| 自动 + 编码偏好 H265 | `play_addr_265` | ✓ |
| 强制 H264 + 最高画质 | 跳过 HEVC 的 1080，选 720 | ✓ |
| `formatCount(123456789 / 25000 / 1500 / 999)` | `1.2亿 / 2.5万 / 1.5k / 999` | ✓ |
| `FlexibleJson.toInt("99999999999")` | 截断为 `1215752191` | ✓ |
| `FlexibleJson.toBoolean("TRUE")` | `false` | ✓ |
