package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.PageDisplayContext;
import cc.thonly.wikikernel.common.extension.PageDisplayHook;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.web.CalendarStandaloneController;
import org.springframework.stereotype.Component;

/**
 * 「活动日历」页面：**接管正文渲染**（线上原版是 MediaWiki 页面里嵌 iframe 指向 {@code /calendar/}，
 * 这里不嵌 iframe）——直接渲染本地活动日历，数据来自本站活动表。
 * <p>
 * 页面不需要写任何标签；其它页面想嵌日历用 {@code <touhou-calendar />} 标签（见
 * {@link TouhouCalendarTag}），独立整页用 {@code /calendar}（见
 * {@link CalendarStandaloneController}）。
 */
@Component
public class CalendarPageHook implements PageDisplayHook {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.CALENDAR_PAGE_TITLE;

    @Override
    public int order() {
        return 62;
    }

    @Override
    public String renderBody(PageDisplayContext context) {
        return PAGE_TITLE.equals(context.title()) ? render() : null;
    }

    @Override
    public String renderMissingPage(PageDisplayContext context) {
        return PAGE_TITLE.equals(context.title()) ? render() : null;
    }

    private String render() {
        return "<div class=\"thonly-calendar-app\">"
                + TouhouCalendarTag.CONTAINER_HTML
                + "</div>";
    }
}
