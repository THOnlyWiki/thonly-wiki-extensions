package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.HeadContext;
import cc.thonly.wikikernel.common.extension.HtmlHeadProvider;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;

import java.util.List;

/**
 * 「线下活动速查表」/「活动分布地图」两个页面的样式与脚本注入：
 * <ul>
 *     <li>速查表：{@code activity.css}（照原站移植的表格 / 搜索 / 下拉 / 链接样式）
 *         + {@code activity.js}（筛选 + 编辑 / 添加 / 变更历史弹窗）；</li>
 *     <li>分布地图：{@code activity-map.css} + 本地内置的 ECharts / china.js + {@code activity-map.js}
 *         （ECharts 约 2.5MB，只在这个页面加载）。</li>
 * </ul>
 * 用 {@link HtmlHeadProvider}（{@code siteWide=true} + 标题过滤）而**不是**
 * {@code PageDisplayHook#headItems}：后者只在「页面已创建」的阅读视图里生效，而这两张页面在
 * **未创建**时也由插件接管渲染（{@code renderMissingPage} 路径）——之前用 headItems，
 * CSS 从来没被注入过，整页是裸样式（表格没边框、搜索框又短又不居中）。
 */
public class ActivityHeadProvider implements HtmlHeadProvider {

    @Override
    public int order() {
        return 620;
    }

    @Override
    public boolean siteWide() {
        return true;
    }

    @Override
    public List<String> headItems(HeadContext context) {
        if (context == null) {
            return List.of();
        }
        String title = context.pageTitle();
        if (ThonlyActivitySchedulePlugin.PAGE_TITLE.equals(title)) {
            return List.of(
                    "<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/activity.css\">",
                    "<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/activity-ai.css\">",
                    "<script defer src=\"/plugins/thonly-wiki-extensions/activity.js\"></script>",
                    "<script defer src=\"/plugins/thonly-wiki-extensions/activity-ai.js\"></script>");
        }
        if (ThonlyActivitySchedulePlugin.MAP_PAGE_TITLE.equals(title)) {
            // ECharts（~2.5MB）只在「活动分布地图」这一个页面加载
            return List.of(
                    "<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/activity-map.css\">",
                    "<script defer src=\"/plugins/thonly-wiki-extensions/map/echarts.min.js\"></script>",
                    "<script defer src=\"/plugins/thonly-wiki-extensions/map/china.js\"></script>",
                    "<script defer src=\"/plugins/thonly-wiki-extensions/activity-map.js\"></script>");
        }
        if (ThonlyActivitySchedulePlugin.CALENDAR_PAGE_TITLE.equals(title)) {
            // 「活动日历」页面接管：日历资源按标题注入（页面上没有标签也能渲染）
            return CalendarAssets.headItems();
        }
        return List.of();
    }
}
