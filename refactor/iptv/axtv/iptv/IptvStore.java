/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 核心层接口，对称 AuthStore。Android 侧实现见 AndroidIptvStore。
 *           提供 InMemory 实现供 JVM 单测使用。
 */
package axtv.iptv;

import java.util.List;

/** 电视源存储接口。
 *
 * <p>每个源对应 {@link Channel} 的 sourceId，源列表以 JSON 数组形式持久化到 {@link Settings#IPTV_SOURCES_LIST}。
 *
 * <p>线程安全说明：所有方法均为线程安全，由 Android 侧 SharedPreferences 实现自行同步，
 * 核心层 InMemory 实现为单实例、非线程安全（单测用）。
 */
public interface IptvStore {

    /** 源 JSON 对象：{@code {"name":"...", "url":"..."|"content":"...", "group":"...", "logoUrl":"...", "sourceId":"..."}}。 */
    record SourceJson(String name, String urlOrContent, String group, String logoUrl, String sourceId) {}

    /** 获取当前用户源列表。 */
    List<SourceJson> sources();

    /** 新增源（name/urlOrContent/group/logoId/sourceId 均不可为空）。 */
    void addSource(SourceJson source);

    /** 根据 sourceId 移除源。 */
    void removeSource(String sourceId);

    /** 空实现（核心层单测用）。 */
    final class InMemory implements IptvStore {

        private volatile List<SourceJson> sources = List.of();

        @Override
        public List<SourceJson> sources() {
            return sources;
        }

        @Override
        public void addSource(SourceJson source) {
            List<SourceJson> newList = new java.util.ArrayList<>(sources);
            newList.add(source);
            this.sources = List.copyOf(newList);
        }

        @Override
        public void removeSource(String sourceId) {
            List<SourceJson> newList = new java.util.ArrayList<>();
            for (SourceJson s : sources) {
                if (s.sourceId() == null || !s.sourceId().equals(sourceId)) {
                    newList.add(s);
                }
            }
            this.sources = List.copyOf(newList);
        }
    }
}