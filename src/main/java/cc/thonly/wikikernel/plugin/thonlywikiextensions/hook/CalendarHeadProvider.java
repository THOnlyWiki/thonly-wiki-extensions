package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.HeadContext;
import cc.thonly.wikikernel.common.extension.HtmlHeadProvider;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;

import java.util.List;

/**
 * 页面级资源注入（{@code siteWide=false}）：页面用了 {@code <touhou-calendar />} 标签
 * （渲染时写了 {@link TouhouCalendarTag#USED_PROPERTY} 页面属性）就注入日历的 CSS / JS。
 * <p>
 * 「活动日历」页面本身由 siteWide 的 {@link ActivityHeadProvider} 按标题注入（那条路径在
 * 「页面不存在」时也生效），这里跳过它，避免同一份资源注入两遍、日历被初始化两次。
 */
public class CalendarHeadProvider implements HtmlHeadProvider {

    @Override
    public int order() {
        return 630;
    }

    @Override
    public List<String> headItems(HeadContext context) {
        if (context == null || !"1".equals(context.properties().get(TouhouCalendarTag.USED_PROPERTY))) {
            return List.of();
        }
        if (ThonlyActivitySchedulePlugin.CALENDAR_PAGE_TITLE.equals(context.pageTitle())) {
            return List.of();
        }
        return CalendarAssets.headItems();
    }
}
