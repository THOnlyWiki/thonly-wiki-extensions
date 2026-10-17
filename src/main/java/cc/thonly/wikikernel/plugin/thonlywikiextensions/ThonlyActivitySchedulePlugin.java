package cc.thonly.wikikernel.plugin.thonlywikiextensions;

import cc.thonly.wikikernel.common.extension.HtmlHeadProvider;
import cc.thonly.wikikernel.common.extension.PageDisplayHook;
import cc.thonly.wikikernel.common.extension.ParserTag;
import cc.thonly.wikikernel.common.plugin.PluginContext;
import cc.thonly.wikikernel.common.plugin.PluginDescriptor;
import cc.thonly.wikikernel.common.plugin.WikiPlugin;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.ActivityHeadProvider;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.ActivityPageHook;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.CalendarHeadProvider;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.CalendarPageHook;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.MapPageHook;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.TouhouCalendarTag;

/**
 * {@code thonly-wiki-extensions} 扩展（移植自 {@code thonly.cc/excel} 的「全国东方Project线下活动表格」）：
 * <ul>
 *     <li>数据：**分年份的物理表** {@code thonly_activity_<年>}（列 category / status / name / region /
 *         date / qq / sort_order / deleted），外加一张 {@code thonly_activity_revision} 变更历史表
 *         （每次增删改都留痕，可按版本回滚）；</li>
 *     <li>读接口：{@code GET /api/thonly-activity/years}、{@code …/list?year=}（受页面读权限约束、
 *         这是「表格要有 API 可以读取」的那套）；</li>
 *     <li>写接口：{@code POST /api/thonly-activity/save|delete|restore} —— 必须登录 + 未被封禁 +
 *         对「线下活动速查表」有 {@code edit} 权限（走 core 访问控制；**默认就是「登录用户」**，
 *         不需要管理员，可在 特殊:权限配置 里按用户组动态调整，例如只放给「管理员」或某个自定义组）；
 *         每次修改都会写一条日志（类型 {@code edit}、目标「线下活动速查表」），因此**最近更改里能看见**；</li>
 *     <li>页面：{@code /wiki/线下活动速查表} 由 {@link ActivityPageHook}
 *         接管正文渲染（无论该页是否已创建都不会 404），编辑（文字「编辑」链接 / 「添加活动」）都是弹窗，
 *         变更历史也可回滚；样式与脚本由 {@link ActivityHeadProvider}
 *         注入；{@code /excel} 是与线上一致的独立只读页面（无 wiki 布局）；</li>
 *     <li>日历：{@code /excel/calendar/<年>/<id>.ics} 下载全天事件（手机 / 电脑通用，每行「操作」列的
 *         「加入日历」）；不再有 Google 链接。</li>
 * </ul>
 * 数据来源：从线上 {@code /proxy_google_doc} 反代接口抓取的三年数据（2024–2026 共 592 行），
 * 随插件资源内的 TSV 在首次启动时导入一次（表为空才导入）。
 */
public class ThonlyActivitySchedulePlugin implements WikiPlugin {

    /** 表格所在页面标题（权限、日志、正文接管都以它为目标）。 */
    public static final String PAGE_TITLE = "线下活动速查表";

    /** 活动分布地图页面标题（正文接管：不嵌 iframe，直接用本地 ECharts 画）。 */
    public static final String MAP_PAGE_TITLE = "活动分布地图";

    /** 活动日历页面标题（正文接管：不嵌 iframe，直接用本地 evoCalendar 渲染）。
     *  其它页面用解析标签 {@code <touhou-calendar />} 嵌入日历，独立整页是 {@code /calendar}。 */
    public static final String CALENDAR_PAGE_TITLE = "活动日历";

    /** 站点设置里的功能开关（与 config-catalog.yaml 一致）。 */
    public static final String ENABLED_KEY = "features.thonly-wiki-extensions";

    /** 页面正文接管（注册为扩展点，参见 PLUGIN-DEV：@Component 只是 Spring Bean，扩展要 context.register）。 */
    @org.springframework.beans.factory.annotation.Autowired
    private ActivityPageHook pageHook;

    /** 「活动分布地图」页面正文接管（ECharts 中国地图，不嵌 iframe）。 */
    @org.springframework.beans.factory.annotation.Autowired
    private MapPageHook mapPageHook;

    /** 「活动日历」页面正文接管（本地 evoCalendar，不嵌 iframe）。 */
    @org.springframework.beans.factory.annotation.Autowired
    private CalendarPageHook calendarPageHook;

    @Override
    public void onStart(PluginContext context) {
        context.register(PageDisplayHook.class, pageHook);
        context.register(PageDisplayHook.class, mapPageHook);
        context.register(PageDisplayHook.class, calendarPageHook);
        // <touhou-calendar />：任何页面写上就渲染活动日历（渲染时写页面属性，head 按属性注入资源）
        context.register(ParserTag.class,
                new TouhouCalendarTag());
        // 样式 / 脚本必须走 HtmlHeadProvider（siteWide + 标题过滤）：
        // PageDisplayHook#headItems 在「页面不存在」的接管路径里不会被注入（踩过坑）
        context.register(HtmlHeadProvider.class,
                new ActivityHeadProvider());
        // 用到 <touhou-calendar /> 的普通页面：按页面属性注入日历资源（页面级 provider 才能读到属性）
        context.register(HtmlHeadProvider.class,
                new CalendarHeadProvider());
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor("thonly-wiki-extensions", "Thonly Wiki Extensions", "1.0.0",
                "全国东方Project线下活动速查表（分年份数据表 + 变更回溯 + 最近更改日志 + /excel 独立页 + 日历导出 "
                        + "+ 活动分布地图 + 活动日历 <touhou-calendar /> 与 /calendar 独立页）");
    }
}
