package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.PageDisplayContext;
import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ai.ThonlyAiConfig;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.ActivityStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ActivityPageHookTest {
    @Test
    void navigationAndCountryFiltersAreSharedAcrossActivityPages() throws Exception {
        String table = hook(ROWS, false).renderBody(context(TITLE, EDITOR));
        for (String country : ActivityRegions.COUNTRIES) { assertTrue(table.contains("value=\"" + country + "\"")); }
        assertEquals("日本国", ActivityRegions.canonicalize("日本(Japan)"));
        assertEquals("大韩民国", ActivityRegions.canonicalize("韩国(Korea)"));
        String calendar = new CalendarPageHook().renderBody(context("活动日历", EDITOR));
        assertTrue(calendar.contains("thonly-toolbar"));
        assertTrue(calendar.contains("aria-current=\"page\">活动日历"));
        MapPageHook map = new MapPageHook();
        var field = MapPageHook.class.getDeclaredField("categoryStore"); field.setAccessible(true);
        field.set(map, new cc.thonly.wikikernel.common.service.CategoryStore() {
            @Override public List<cc.thonly.wikikernel.common.service.CategoryMember> members(String title,int limit) { return List.of(); }
            @Override public List<String> categoriesOf(long pageId) { return List.of(); }
            @Override public cc.thonly.wikikernel.common.service.CategoryCounts counts(String title) { return null; }
        });
        String mapHtml = map.renderBody(context("活动分布地图",EDITOR));
        assertTrue(mapHtml.contains("thonly-toolbar"));
        assertTrue(mapHtml.contains("aria-current=\"page\">活动地图"));
        fixture("calendar",calendar); fixture("map",mapHtml);
    }

    private static final String TITLE = "线下活动速查表";
    private static final Viewer EDITOR = Viewer.of(1, "维护者", Set.of());
    private static final List<Map<String, Object>> ROWS = List.of(
            row(1, "tho", "定档", "金陵幻想乡 · 秋日同人展", "江苏省", "2026/11/7", "123456701"),
            row(2, "live", "定档", "沪上东方音乐夜", "上海市", "2026-11-14", "123456702"),
            row(3, "thp", "计划", "岭南秋日茶会", "广东省", "2026/11/21", "123456703"),
            row(4, "school", "如期", "蓉城东方高校联合例会", "四川省", "2026/11/28", "123456704"),
            row(5, "tho", "定档", "钱塘冬日幻想祭", "浙江省", "2026/12/6", ""),
            row(6, "live", "延期", "江城幻想乡音乐会 · 一段用于检查换行的较长活动名称", "湖北省", "暂无", "报名表：https://example.com/registration/long-public-information"),
            row(7, "thp", "取消", "京华冬日茶会", "北京市", "2026/12/19", "123456707"),
            row(8, "thp", "计划", "海外交流日", "日本(Japan)", "2026/12/25", ""));

    @Test
    void claimsExistingAndMissingScheduleOnly() throws Exception {
        ActivityPageHook hook = hook(ROWS, false);
        PageDisplayContext context = context(TITLE, EDITOR);
        assertEquals(hook.renderBody(context), hook.renderMissingPage(context));
        assertNull(hook.renderBody(context("其他页面", EDITOR)));
        assertNull(hook.renderMissingPage(context("其他页面", EDITOR)));
    }

    @Test
    void anonymousNeverReceivesWriteControlsEvenWhenAccessRuleAllowsEdit() throws Exception {
        String html = hook(ROWS, true).renderBody(context(TITLE, Viewer.ANONYMOUS));
        assertTrue(html.contains("金陵幻想乡"));
        assertFalse(html.contains("id=\"thonly-add\""));
        assertFalse(html.contains("id=\"thonly-dialog\""));
        assertFalse(html.contains("id=\"thonly-ai-config\""));
        assertFalse(html.contains("id=\"thonly-detail-edit\""));
        fixture("reader", html);
    }

    @Test
    void rendersUsableEditorAdminAndEmptyViewsWithoutDuplicateIds() throws Exception {
        String editor = hook(ROWS, false).renderBody(context(TITLE, EDITOR));
        assertTrue(editor.contains("id=\"thonly-add\""));
        assertFalse(editor.contains("id=\"thonly-ai-config\""));
        assertTrue(editor.indexOf("data-id=\"1\"") < editor.indexOf("data-id=\"2\""));
        assertTrue(editor.indexOf("data-id=\"8\"") < editor.indexOf("data-id=\"6\""));
        assertTrue(editor.contains("/excel/calendar/2026/2.ics"));
        assertFalse(editor.contains("/excel/calendar/2026/6.ics"));
        String admin = hook(ROWS, true).renderBody(context(TITLE, EDITOR));
        assertTrue(admin.contains("id=\"thonly-ai-config\""));
        var matcher = Pattern.compile("\\bid=\"([^\"]+)\"").matcher(admin);
        Set<String> ids = new HashSet<>();
        while (matcher.find()) { assertTrue(ids.add(matcher.group(1)), matcher.group(1)); }
        String empty = hook(List.of(), false).renderBody(context(TITLE, EDITOR));
        assertTrue(empty.contains("id=\"thonly-empty\">"));
        fixture("editor", editor);
        fixture("admin", admin);
        fixture("empty", empty);
    }

    @Test
    void escapesStoredContentAndPreservesInvalidLegacyDate() throws Exception {
        String html = hook(List.of(row(9, "tho", "计划", "<img src=x onerror=alert(1)>",
                "\"><script>alert(1)</script>", "2026/8/45", "<b>联络</b>")), false)
                .renderBody(context(TITLE, EDITOR));
        assertFalse(html.contains("<img src=x"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;img src=x"));
        assertTrue(html.contains("data-date=\"2026/8/45\""));
        assertFalse(html.contains("/excel/calendar/2026/9.ics"));
    }

    private static Map<String, Object> row(long id, String category, String status, String name,
                                           String region, String date, String qq) {
        return Map.of("id", id, "category", category, "status", status, "name", name,
                "region", region, "date", date, "qq", qq);
    }

    private static ActivityPageHook hook(List<Map<String, Object>> rows, boolean admin) throws Exception {
        ActivityPageHook hook = new ActivityPageHook();
        set(hook, "store", new ActivityStore() {
            @Override public List<Integer> years() { return List.of(2025, 2026); }
            @Override public int defaultYear() { return 2026; }
            @Override public List<Map<String, Object>> list(int year) { return rows; }
        });
        set(hook, "accessCheck", new AccessCheck() {
            @Override public boolean can(AccessAction action, String title, Viewer viewer) {
                return action != AccessAction.PROTECT || admin;
            }
            @Override public Optional<String> blockNotice(Viewer viewer) { return Optional.empty(); }
        });
        set(hook, "aiConfig", new ThonlyAiConfig(null) {
            @Override public Settings settings() {
                return new Settings(true, "AI 整理导入", "", "", false, false);
            }
        });
        return hook;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = ActivityPageHook.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // 浏览器回归直接使用实际 Java 渲染的正文，避免手工 HTML 与实现脱节。
    private static void fixture(String name, String html) throws Exception {
        Path directory = Path.of("build", "ui-fixtures");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".html"), html);
    }

    private static PageDisplayContext context(String title, Viewer viewer) {
        return new PageDisplayContext() {
            @Override public long pageId() { return 0; }
            @Override public String title() { return title; }
            @Override public String content() { return ""; }
            @Override public boolean oldRevision() { return false; }
            @Override public String pageUrl() { return "/wiki/" + title; }
            @Override public Map<String, String> properties() { return Map.of(); }
            @Override public Map<String, String> query() { return Map.of("year", "2026"); }
            @Override public Viewer viewer() { return viewer; }
            @Override public String siteName() { return "活动维基"; }
            @Override public String baseUrl() { return "http://activity.test"; }
        };
    }
}
