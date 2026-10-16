package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.ParserTag;
import cc.thonly.wikikernel.common.extension.ParserTagCall;

/**
 * {@code <touhou-calendar />} 解析标签：任何页面写上它就渲染「活动日历」
 * （本地内置的 evoCalendar，数据来自本站活动表，不再依赖 Google 表格）。
 * <p>
 * 渲染结果只是一个容器（{@code div[data-touhou-calendar]}），真正的内容由
 * {@code activity-calendar.js} 拉数据后初始化；标签同时往页面属性写
 * {@link #USED_PROPERTY}，{@link CalendarHeadProvider} 据此只在用到的页面注入日历资源。
 * <p>
 * 页面标题 {@code 活动日历} 由 {@link CalendarPageHook} 直接接管正文（不写标签也有日历）。
 */
public class TouhouCalendarTag implements ParserTag {

    /** 标签名（大小写不敏感）。 */
    public static final String TAG_NAME = "touhou-calendar";

    /** 渲染上下文属性：页面用到日历标签时置 {@code 1}（保存后成为页面属性，供 head 注入判断）。 */
    public static final String USED_PROPERTY = "touhou-calendar-used";

    /** 日历容器（三种用法共用；JS 按 {@code [data-touhou-calendar]} 初始化）。 */
    public static final String CONTAINER_HTML = "<div class=\"touhou-calendar\" data-touhou-calendar=\"1\"></div>";

    @Override
    public String name() {
        return TAG_NAME;
    }

    /** 自闭合标签：内容（通常为空）不参与模板展开。 */
    @Override
    public boolean expandBody() {
        return false;
    }

    @Override
    public String render(ParserTagCall call) {
        call.context().setProperty(USED_PROPERTY, "1");
        return CONTAINER_HTML;
    }
}
