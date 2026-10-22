package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.ActivityStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThonlyAiServiceTest {

    @Test
    void applyRevalidatesAndCreatesActivity() throws Exception {
        FakeStore store = new FakeStore();
        ObjectMapper mapper = new ObjectMapper();
        ThonlyAiService service = new ThonlyAiService(store, null, null, mapper);
        String json = "[{\"type\":\"create\",\"category\":\"tho\",\"status\":\"计划\","
                + "\"name\":\"测试活动\",\"region\":\"测试地区\",\"date\":\"2026/8/2\",\"qq\":\"123\"}]";
        ThonlyAiService.ApplyResult result = service.apply(
                Viewer.of(1, "admin", Set.of("管理员")), 2026, mapper.readTree(json));
        assertEquals(1, result.applied());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, store.added.size());
        assertEquals("测试活动", store.added.get(0).name);
    }

    @Test
    void applyRejectsDuplicateCreateAndMergesUpdate() throws Exception {
        FakeStore store = new FakeStore();
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("id", 7L);
        existing.put("category", "thp");
        existing.put("status", "计划");
        existing.put("name", "旧名称");
        existing.put("region", "旧地区");
        existing.put("date", "2026/1/1");
        existing.put("qq", "");
        store.rows.add(existing);
        ObjectMapper mapper = new ObjectMapper();
        ThonlyAiService service = new ThonlyAiService(store, null, null, mapper);
        String json = "[{\"type\":\"update\",\"id\":7,\"name\":\"新名称\",\"status\":\"定档\"},"
                + "{\"type\":\"create\",\"category\":\"tho\",\"name\":\"新名称\"}]";
        ThonlyAiService.ApplyResult result = service.apply(
                Viewer.of(1, "admin", Set.of("管理员")), 2026, mapper.readTree(json));
        assertEquals(1, result.applied());
        assertEquals("新名称", store.updated.get(0).name);
        assertEquals("旧地区", store.updated.get(0).region);
        assertTrue(result.errors().stream().anyMatch(text -> text.contains("已存在")));
    }

    private static final class FakeStore extends ActivityStore {
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private final List<Added> added = new ArrayList<>();
        private final List<Added> updated = new ArrayList<>();

        @Override
        public List<Map<String, Object>> list(int year) {
            return rows;
        }

        @Override
        public long add(Viewer viewer, int year, String category, String status, String name,
                        String region, String date, String qq, String comment) {
            Added value = new Added(0, category, status, name, region, date, qq);
            added.add(value);
            return 1;
        }

        @Override
        public boolean update(Viewer viewer, int year, long id, String category, String status, String name,
                              String region, String date, String qq, String comment) {
            updated.add(new Added(id, category, status, name, region, date, qq));
            return true;
        }
    }

    private record Added(long id, String category, String status, String name,
                         String region, String date, String qq) {
    }
}
