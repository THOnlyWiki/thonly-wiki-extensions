package cc.thonly.wikikernel.plugin.thonlywikiextensions.web;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.common.service.CurrentUser;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.ActivityStore;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.CalendarLinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立页面 {@code /excel}（与线上 {@code thonly.cc/excel/} 一致的**无 wiki 布局**整页应用）：
 * 自己一套样式与脚本、年份标签 + 分类过滤 + 可直接编辑的表格（有编辑权限才有编辑控件）、
 * 变更历史与回滚、每条活动的日历快捷方式；另提供 {@code /excel/calendar/<年>/<id>.ics} 下载。
 * <p>
 * 数据仍走 {@link ActivityApiController} 的接口，权限判定与页面一致（读 = 页面对谁都不可读则拒绝；
 * 写 = 登录 + {@code edit} 权限 + 同源 + 未封禁）。
 */
@Controller
public class ExcelStandaloneController {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.PAGE_TITLE;

    @Autowired
    private ActivityStore store;

    @Autowired
    private AccessCheck accessCheck;

    @Autowired
    private CurrentUser currentUser;

    /** 独立页面（无皮肤布局）。 */
    @GetMapping({"/excel", "/excel/"})
    public ModelAndView page(@org.springframework.web.bind.annotation.RequestParam(value = "year", required = false)
                             Integer year) {
        Viewer viewer = currentUser.viewer();
        List<Integer> years = store.years();
        int target = year == null || !years.contains(year) ? years.get(years.size() - 1) : year;
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("years", years);
        model.put("year", target);
        model.put("categories", ActivityStore.CATEGORIES);
        model.put("categoryLabels", categoryLabels());
        model.put("rows", decorate(target, store.list(target)));
        model.put("canRead", accessCheck.canRead(PAGE_TITLE, viewer));
        model.put("canEdit", accessCheck.can(AccessAction.EDIT, PAGE_TITLE, viewer));
        model.put("loggedIn", viewer.loggedIn());
        model.put("pageTitle", "全国东方Project线下活动表格");
        return new ModelAndView("plugins/thonly-wiki-extensions/excel", model);
    }

    /** 单条活动的 .ics（全天事件；手机 / 电脑日历通用）。 */
    @GetMapping("/excel/calendar/{year}/{id}.ics")
    public ResponseEntity<byte[]> calendar(@PathVariable("year") int year, @PathVariable("id") long id) {
        Viewer viewer = currentUser.viewer();
        if (!accessCheck.canRead(PAGE_TITLE, viewer)) {
            return ResponseEntity.status(403).build();
        }
        for (Map<String, Object> row : store.list(year)) {
            long rowId = row.get("id") instanceof Number number ? number.longValue() : 0;
            if (rowId != id) {
                continue;
            }
            String ics = CalendarLinks.ics(text(row.get("name")), text(row.get("region")),
                    text(row.get("date")), text(row.get("qq")), text(row.get("status")));
            if (ics == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] body = ics.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=UTF-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"thonly-activity-" + year + "-" + id + ".ics\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(body);
        }
        return ResponseEntity.notFound().build();
    }

    private Map<String, String> categoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("tho", "THO");
        labels.put("thp", "THP&茶会");
        labels.put("school", "高校例会");
        labels.put("live", "Live&音乐会");
        return labels;
    }

    private List<Map<String, Object>> decorate(int year, List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            long id = row.get("id") instanceof Number number ? number.longValue() : 0;
            String date = text(row.get("date"));
            copy.put("icsUrl", CalendarLinks.icsUrl(year, id, date));
            result.add(copy);
        }
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
