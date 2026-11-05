package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.web.WikiUrls;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;

/** 三个活动页面共用导航；速查表可在 open() 之后继续追加有权限的管理入口。 */
public final class ActivityToolbar {
    private ActivityToolbar() {}

    public static String open(String currentTitle) {
        StringBuilder html = new StringBuilder("<div class=\"thonly-toolbar thonly-activity-navigation\"><nav class=\"thonly-views\" aria-label=\"活动视图\">");
        item(html, currentTitle, ThonlyActivitySchedulePlugin.PAGE_TITLE, "速查表");
        item(html, currentTitle, ThonlyActivitySchedulePlugin.CALENDAR_PAGE_TITLE, "活动日历");
        item(html, currentTitle, ThonlyActivitySchedulePlugin.MAP_PAGE_TITLE, "活动地图");
        return html.append("</nav>").toString();
    }

    public static String render(String currentTitle) {
        return open(currentTitle) + "</div>";
    }

    private static void item(StringBuilder html, String current, String title, String label) {
        if (title.equals(current)) {
            html.append("<span class=\"is-current\" aria-current=\"page\">").append(label).append("</span>");
        } else {
            html.append("<a href=\"").append(WikiUrls.pagePath(title)).append("\">").append(label).append("</a>");
        }
    }
}
