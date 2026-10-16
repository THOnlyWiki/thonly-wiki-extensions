package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import java.util.List;

/**
 * 「活动日历」（evoCalendar）的资源清单：CSS / JS 都从插件静态资源提供
 * （jQuery 全站已由 core 的 {@code /vendor/jquery/jquery.min.js} 加载，这里不重复引入）。
 * <p>
 * 三个使用场景共用这一份清单：
 * <ul>
 *     <li>解析标签 {@code <touhou-calendar />}（页面属性驱动，见 {@link CalendarHeadProvider}）；</li>
 *     <li>「活动日历」页面正文接管（按页面标题注入，见 {@link ActivityHeadProvider}）；</li>
 *     <li>独立页 {@code /calendar}（模板自己引入）。</li>
 * </ul>
 */
public final class CalendarAssets {

    private CalendarAssets() {
    }

    /** {@code <head>} 片段（样式在上、脚本 defer，顺序即执行顺序）。 */
    public static List<String> headItems() {
        return List.of(
                "<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/calendar/evo-calendar.css\">",
                "<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/activity-calendar.css\">",
                "<script defer src=\"/plugins/thonly-wiki-extensions/calendar/evo-calendar.js\"></script>",
                "<script defer src=\"/plugins/thonly-wiki-extensions/activity-calendar.js\"></script>");
    }
}
