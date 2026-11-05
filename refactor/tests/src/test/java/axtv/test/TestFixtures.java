/*
 * 共享测试夹具：Aweme 工厂 + 内存假 TvProvider。
 * 移植自 .workbuddy/SmokeTest.java，供多个测试类复用。
 */
package axtv.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import axtv.model.Author;
import axtv.model.Aweme;
import axtv.model.Cover;
import axtv.model.Video;
import axtv.tv.TvProvider;

public final class TestFixtures {
    private TestFixtures() {}

    public static Aweme aweme(String id, String desc, String cover, String nickname, boolean portrait) {
        Cover c = new Cover(cover == null ? List.of() : List.of(cover));
        Video v = new Video(null, null, null, null, c, null, null, null,
                1234, portrait ? 1080 : 1920, portrait ? 1920 : 1080, null, null);
        Author a = new Author(null, nickname, null, null, null, null, null, 0, 0, 0, 0, 0);
        return new Aweme(id, desc, v, a, null, 0, 0, null, 0, 0, null, null);
    }

    /** 内存假 TvProvider，记录插入/更新/删除计数与 browsable/logo 状态。 */
    public static class FakeProvider implements TvProvider {
        public int sdk = 33;
        public boolean hasProvider = true;
        public long nextChannelId = 100, nextProgramId = 1000;
        public Map<Long, Map<String, Object>> channels = new LinkedHashMap<>();
        public Map<Long, Map<String, Object>> programs = new LinkedHashMap<>();
        public boolean browsableRequested = false, logoCreated = false;
        public int inserts = 0, updates = 0, deletes = 0;

        @Override public int sdkInt() { return sdk; }
        @Override public boolean hasTvProvider() { return hasProvider; }
        @Override public List<Map<String, Object>> queryChannels() { return new ArrayList<>(channels.values()); }
        @Override public List<Map<String, Object>> queryAllPrograms() { return new ArrayList<>(programs.values()); }
        @Override public Map<String, Object> queryProgram(long id) { return programs.get(id); }
        @Override public long insertChannel(Map<String, Object> v) {
            long id = nextChannelId++;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(v);
            row.put("_id", id);
            channels.put(id, row);
            return id;
        }
        @Override public boolean addChannelLogo(long id, String uri) { return uri != null; }
        @Override public void deleteChannel(long id) { channels.remove(id); }
        @Override public void requestChannelBrowsable(long id) { browsableRequested = true; }
        @Override public long insertProgram(Map<String, Object> v) {
            long id = nextProgramId++;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(v);
            row.put("_id", id);
            programs.put(id, row);
            inserts++;
            return id;
        }
        @Override public void updateProgram(long id, Map<String, Object> v) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(v);
            row.put("_id", id);
            programs.put(id, row);
            updates++;
        }
        @Override public void deleteProgram(long id) { programs.remove(id); deletes++; }
        @Override public boolean channelLogoReady() { return logoCreated; }
        @Override public void createChannelLogo() { logoCreated = true; }
        @Override public String channelLogoUri() { return "file:///cache/tv_channel_logo.png"; }
    }
}
