package cc.thonly.wikikernel.plugin.thonlywikiextensions.web;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.common.service.CurrentUser;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.ActivityStore;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.CalendarLinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动速查表的 API（「表格要有 API 可以读取」的那套；写接口全部走鉴权）：
 * <ul>
 *     <li><b>读</b>：{@code GET /api/thonly-activity/years}、{@code GET /api/thonly-activity/list?year=}、
 *         {@code GET /api/thonly-activity/revisions?year=}（变更历史，需编辑权限）；
 *         读也要过 {@code AccessCheck.canRead(「线下活动速查表」, viewer)}，页面对谁都不可读时接口同样拒绝，
 *         不泄露数据。</li>
 *     <li><b>写</b>：{@code POST /save}、{@code POST /delete}、{@code POST /restore} ——
 *         ① 必须登录（匿名 401）② 必须未被封禁 ③ 必须对「线下活动速查表」有 {@code edit} 权限：
 *         走 core 访问控制，**内置默认就是「登录用户」**（不需要管理员），
 *         可在 特殊:权限配置 里按用户组动态收紧 / 放开（例如只留给管理员或某个自定义组）；
 *         CSRF 由 core 的 {@code SameOriginWriteFilter} 兜底（外站 Origin 到不了这里），
 *         插件自己的脚本仍带 {@code X-Requested-With} 作为额外约定。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/thonly-activity")
public class ActivityApiController {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.PAGE_TITLE;

    @Autowired
    private ActivityStore store;

    @Autowired
    private AccessCheck accessCheck;

    @Autowired
    private CurrentUser currentUser;

    // ==================== 读 ====================

    @GetMapping("/years")
    public ResponseEntity<Object> years() {
        Viewer viewer = currentUser.viewer();
        if (!accessCheck.canRead(PAGE_TITLE, viewer)) {
            return forbidden("permissions.readForbidden");
        }
        List<Integer> years = store.years();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("years", years);
        data.put("categories", ActivityStore.CATEGORIES);
        data.put("canEdit", accessCheck.can(AccessAction.EDIT, PAGE_TITLE, viewer));
        return ResponseEntity.ok(data);
    }

    @GetMapping("/list")
    public ResponseEntity<Object> list(@RequestParam(value = "year", required = false) Integer year) {
        Viewer viewer = currentUser.viewer();
        if (!accessCheck.canRead(PAGE_TITLE, viewer)) {
            return forbidden("permissions.readForbidden");
        }
        int target = year == null ? store.years().get(store.years().size() - 1) : year;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : store.list(target)) {
            rows.add(decorate(target, row));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", target);
        data.put("years", store.years());
        data.put("categories", ActivityStore.CATEGORIES);
        data.put("canEdit", accessCheck.can(AccessAction.EDIT, PAGE_TITLE, viewer));
        data.put("rows", rows);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/revisions")
    public ResponseEntity<Object> revisions(@RequestParam(value = "year", required = false) Integer year,
                                            @RequestParam(value = "limit", required = false) Integer limit) {
        Viewer viewer = currentUser.viewer();
        if (!accessCheck.can(AccessAction.EDIT, PAGE_TITLE, viewer)) {
            return forbidden("permissions.editForbidden");
        }
        int target = year == null ? store.years().get(store.years().size() - 1) : year;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", target);
        data.put("revisions", store.revisions(target, limit == null ? 50 : limit));
        return ResponseEntity.ok(data);
    }

    // ==================== 写 ====================

    @PostMapping("/save")
    public ResponseEntity<Object> save(@RequestParam Map<String, String> params) {
        ResponseEntity<Object> denied = guard();
        if (denied != null) {
            return denied;
        }
        int year = intOf(params.get("year"), 0);
        long id = longOf(params.get("id"), 0);
        String comment = params.get("comment");
        try {
            if (id <= 0) {
                long created = store.add(currentUser.viewer(), year, params.get("category"), params.get("status"),
                        params.get("name"), params.get("region"), params.get("date"), params.get("qq"), comment);
                return ResponseEntity.ok(Map.of("id", created, "saved", true));
            }
            boolean updated = store.update(currentUser.viewer(), year, id, params.get("category"),
                    params.get("status"), params.get("name"), params.get("region"), params.get("date"),
                    params.get("qq"), comment);
            return updated ? ResponseEntity.ok(Map.of("id", id, "saved", true))
                    : ResponseEntity.status(404).body(Map.of("message", "活动不存在或已删除"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Object> delete(@RequestParam Map<String, String> params) {
        ResponseEntity<Object> denied = guard();
        if (denied != null) {
            return denied;
        }
        int year = intOf(params.get("year"), 0);
        long id = longOf(params.get("id"), 0);
        boolean deleted = store.delete(currentUser.viewer(), year, id, params.get("comment"));
        return deleted ? ResponseEntity.ok(Map.of("deleted", true))
                : ResponseEntity.status(404).body(Map.of("message", "活动不存在或已删除"));
    }

    @PostMapping("/restore")
    public ResponseEntity<Object> restore(@RequestParam Map<String, String> params) {
        ResponseEntity<Object> denied = guard();
        if (denied != null) {
            return denied;
        }
        long revisionId = longOf(params.get("revisionId"), 0);
        boolean restored = store.restore(currentUser.viewer(), revisionId, params.get("comment"));
        return restored ? ResponseEntity.ok(Map.of("restored", true))
                : ResponseEntity.status(404).body(Map.of("message", "版本不存在"));
    }

    // ==================== 内部 ====================

    /** 返回 null 表示通过；否则返回应当直接回给客户端的错误响应。 */
    private ResponseEntity<Object> guard() {
        Viewer viewer = currentUser.viewer();
        if (!viewer.loggedIn()) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!accessCheck.can(AccessAction.EDIT, PAGE_TITLE, viewer)) {
            return forbidden("permissions.editForbidden");
        }
        java.util.Optional<String> block = accessCheck.blockNotice(viewer);
        if (block.isPresent()) {
            return ResponseEntity.status(403).body(Map.of("message", block.get()));
        }
        return null;
    }

    private static ResponseEntity<Object> forbidden(String key) {
        return ResponseEntity.status(403).body(Map.of("message", key));
    }

    /** 给每行补上日历链接与分类文案。 */
    private Map<String, Object> decorate(int year, Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        long id = row.get("id") instanceof Number number ? number.longValue() : 0;
        String date = row.get("date") == null ? "" : String.valueOf(row.get("date"));
        String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
        String region = row.get("region") == null ? "" : String.valueOf(row.get("region"));
        String qq = row.get("qq") == null ? "" : String.valueOf(row.get("qq"));
        String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
        result.put("icsUrl", CalendarLinks.icsUrl(year, id, date));
        result.put("year", year);
        return result;
    }

    private static int intOf(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long longOf(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
