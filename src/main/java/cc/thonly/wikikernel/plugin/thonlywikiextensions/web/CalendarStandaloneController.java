package cc.thonly.wikikernel.plugin.thonlywikiextensions.web;

import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 独立页面 {@code /calendar}（对应线上 {@code thonly.cc/calendar/} 的**无 wiki 布局**整页日历）：
 * 本地 evoCalendar + 本站活动表数据，不再依赖 Google 表格与 iframe。
 * <p>
 * 读权限由 {@code /api/thonly-activity/*} 接口自己把关（页面读权限不足时日历显示加载失败提示）；
 * 页面样式与「活动日历」wiki 页面 / {@code <touhou-calendar />} 标签共用
 * {@code activity-calendar.css} 与 {@code activity-calendar.js}。
 */
@Controller
public class CalendarStandaloneController {

    @GetMapping({"/calendar", "/calendar/"})
    public ModelAndView page() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("pageTitle", "东方Project线下活动日程信息");
        model.put("scheduleUrl", "/wiki/" + ThonlyActivitySchedulePlugin.PAGE_TITLE);
        return new ModelAndView("plugins/thonly-wiki-extensions/calendar", model);
    }
}
