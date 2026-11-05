package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.PageDisplayContext;
import cc.thonly.wikikernel.common.extension.PageDisplayHook;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ai.ThonlyAiConfig;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.ActivityStore;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.service.CalendarLinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * 接管「线下活动速查表」正文（含尚未创建的页面）。
 * 保留皮肤标题与导航；查询、速查表及管理入口在正文内呈现。
 * 独立页 /excel 保持只读，写操作仍由原 API 校验登录与权限。
 */
@Component
public class ActivityPageHook implements PageDisplayHook {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.PAGE_TITLE;

    @Autowired
    private ActivityStore store;

    @Autowired
    private AccessCheck accessCheck;

    @Autowired
    private ThonlyAiConfig aiConfig;

    @Override
    public int order() {
        return 60;
    }

    @Override
    public String renderBody(PageDisplayContext context) {
        return PAGE_TITLE.equals(context.title()) ? render(context) : null;
    }

    @Override
    public String renderMissingPage(PageDisplayContext context) {
        return PAGE_TITLE.equals(context.title()) ? render(context) : null;
    }

    private String render(PageDisplayContext context) {
        List<Integer> years = store.years();
        int year = intOf(context.query().get("year"), store.defaultYear());
        if (!years.contains(year)) {
            year = store.defaultYear();
        }
        List<Map<String, Object>> rows = store.list(year).stream()
                .sorted(Comparator.comparing(
                        (Map<String, Object> row) -> CalendarLinks.parseDate(text(row.get("date"))),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        boolean canEdit = context.viewer().loggedIn() && accessCheck.can(AccessAction.EDIT, PAGE_TITLE, context.viewer());
        boolean canAdmin = context.viewer().loggedIn() && accessCheck.can(AccessAction.PROTECT, PAGE_TITLE, context.viewer());

        StringBuilder html = new StringBuilder(24576);
        html.append("<div class=\"thonly-activity-app thonly-app\"><div class=\"thonly-activity-content\">");
        html.append("<div class=\"thonly-intro\"><p>查找各地活动，快速核对日期、地区与联络方式。</p>")
                .append("<a class=\"thonly-community\" target=\"_blank\" rel=\"noopener\"")
                .append(" href=\"https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&amp;k=yI4hzduOvfoisCSfcG7RRD5O5daf_UDi\">")
                .append("反馈与交流：868256565</a></div>");
        html.append(ActivityToolbar.open(PAGE_TITLE));
        if (canEdit || canAdmin) {
            html.append("<div class=\"thonly-management\" aria-label=\"活动管理\">");
            if (canEdit) {
                html.append("<button type=\"button\" id=\"thonly-history\" class=\"wiki-button\">变更历史</button>")
                        .append("<button type=\"button\" id=\"thonly-add\" class=\"wiki-button wiki-button-primary\">＋ 添加活动</button>");
            }
            if (canAdmin) {
                html.append("<details class=\"thonly-admin-tools\"><summary>更多工具</summary>")
                        .append(aiBar(aiConfig.settings())).append("</details>");
            }
            html.append("</div>");
        }
        html.append("</div>");
        appendFilters(html, years, year, rows);
        html.append("<div class=\"thonly-results-heading\"><strong>活动列表 <span id=\"thonly-result-count\" aria-live=\"polite\">")
                .append(rows.size()).append(" 项</span></strong><span>按日期升序 · 日期待定排在最后</span></div>");
        html.append("<div class=\"thonly-table-panel\"><table class=\"thonly-excel\" data-year=\"").append(year)
                .append("\" role=\"table\"><caption class=\"thonly-visually-hidden\">").append(year)
                .append(" 年线下活动速查表</caption><thead><tr>")
                .append("<th scope=\"col\">日期</th><th scope=\"col\">状态</th><th scope=\"col\">活动名称</th>")
                .append("<th scope=\"col\">地区</th><th scope=\"col\">类型</th><th scope=\"col\">QQ群 / 联络信息</th>")
                .append("<th scope=\"col\">操作</th></tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            appendRow(html, year, row, canEdit);
        }
        html.append("</tbody></table><div class=\"thonly-empty\" id=\"thonly-empty\"")
                .append(rows.isEmpty() ? "" : " hidden").append(">没有匹配的活动。")
                .append("<button type=\"button\" class=\"wiki-button\" data-thonly-reset>重置筛选</button></div></div>");
        html.append("<p class=\"thonly-footnote\">活动安排可能调整，请以主办方最终公告为准。点击活动名称可查看详情。</p>")
                .append("</div>");
        // 弹窗放在查询容器外，避免 container-type 改变 fixed 定位的参照。
        html.append(detailDialog(canEdit));
        if (canEdit) {
            html.append(editDialog()).append(historyDialog());
        }
        if (canAdmin) {
            html.append(aiConfigDialog()).append(aiImportDialog());
        }
        html.append("<p class=\"thonly-message\" id=\"thonly-message\" role=\"status\" hidden></p></div>");
        return html.toString();
    }

    private static void appendFilters(StringBuilder html, List<Integer> years, int year,
                                      List<Map<String, Object>> rows) {
        html.append("<section class=\"thonly-filters\" aria-label=\"查询活动\"><div class=\"thonly-search-row\">")
                .append("<label class=\"thonly-visually-hidden\" for=\"thonly-search\">搜索活动名称、地区或联络信息</label>")
                .append("<input type=\"search\" id=\"thonly-search\" class=\"thonly-search\" placeholder=\"搜索活动名称、地区或 QQ 群…\"/>")
                .append("<button type=\"button\" class=\"wiki-button wiki-button-quiet\" data-thonly-reset>重置筛选</button></div>")
                .append("<div class=\"thonly-filter-fields\" id=\"thonly-filter-fields\">")
                .append("<div class=\"thonly-filter-field\"><label for=\"thonly-year\">年份</label><select id=\"thonly-year\">");
        for (int value : years) {
            html.append("<option value=\"").append(value).append("\"")
                    .append(value == year ? " selected" : "").append(">").append(value).append("</option>");
        }
        html.append("</select></div><div class=\"thonly-filter-field\"><label for=\"thonly-region\">省份 / 地区</label>")
                .append("<select id=\"thonly-region\"><option value=\"\">全部地区</option>");
        appendOptions(html, rows, "region");
        html.append("</select></div><div class=\"thonly-filter-field thonly-extra-filter\"><label for=\"thonly-type\">活动类型</label>")
                .append("<select id=\"thonly-type\"><option value=\"ALL\">全部类型</option>")
                .append("<option value=\"TOUHOU_ONLY\">THO</option><option value=\"TOUHOU_PARTY\">THP / 茶会</option>")
                .append("<option value=\"SCHOOL_PARTY\">高校例会</option><option value=\"LIVE_OR_MUSIC\">Live / 音乐会</option></select></div>")
                .append("<div class=\"thonly-filter-field thonly-extra-filter\"><label for=\"thonly-status\">活动状态</label>")
                .append("<select id=\"thonly-status\"><option value=\"\">全部状态</option>");
        appendOptions(html, rows, "status");
        html.append("</select></div><div class=\"thonly-filter-field thonly-extra-filter\"><label for=\"thonly-month\">月份</label>")
                .append("<select id=\"thonly-month\"><option value=\"\">全年</option>");
        for (int month = 1; month <= 12; month++) {
            html.append("<option value=\"").append(month).append("\">").append(month).append(" 月</option>");
        }
        html.append("</select></div></div><button type=\"button\" class=\"thonly-more-filters\" id=\"thonly-more-filters\"")
                .append(" aria-expanded=\"false\" aria-controls=\"thonly-filter-fields\">更多筛选 · 类型 / 状态 / 月份</button>")
                .append("<div class=\"thonly-quick-filters\" aria-label=\"快捷查询\">")
                .append("<button type=\"button\" data-thonly-quick=\"all\" aria-pressed=\"true\">全部活动</button>")
                .append("<button type=\"button\" data-thonly-quick=\"upcoming\" aria-pressed=\"false\">即将开始</button>")
                .append("<button type=\"button\" data-thonly-quick=\"pending\" aria-pressed=\"false\">日期待定</button></div></section>");
    }

    private static void appendOptions(StringBuilder html, List<Map<String, Object>> rows, String key) {
        TreeSet<String> values = new TreeSet<>();
        if ("region".equals(key)) {
            values.addAll(ActivityRegions.PROVINCES);
            values.addAll(ActivityRegions.COUNTRIES);
            values.addAll(List.of("海外", "多地区"));
        }
        for (Map<String, Object> row : rows) {
            String value = text(row.get(key));
            if ("region".equals(key)) {
                value = ActivityRegions.canonicalize(value);
            }
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        String currentGroup = null;
        for (String value : "region".equals(key) ? ActivityRegions.ordered(values) : values) {
            if ("region".equals(key)) {
                String group = ActivityRegions.group(value);
                if (!group.equals(currentGroup)) {
                    if (currentGroup != null) { html.append("</optgroup>"); }
                    html.append("<optgroup label=\"").append(group).append("\">");
                    currentGroup = group;
                }
            }
            html.append("<option value=\"").append(attr(value)).append("\">").append(esc(value)).append("</option>");
        }
        if (currentGroup != null) { html.append("</optgroup>"); }
    }

    private static void appendRow(StringBuilder html, int year, Map<String, Object> row, boolean canEdit) {
        long id = row.get("id") instanceof Number number ? number.longValue() : 0;
        String status = text(row.get("status"));
        String name = text(row.get("name"));
        String region = text(row.get("region"));
        String date = text(row.get("date"));
        String qq = text(row.get("qq"));
        String category = text(row.get("category")).toUpperCase(Locale.ROOT);
        String icsUrl = CalendarLinks.icsUrl(year, id, date);
        String badgeClass = status.contains("取消") ? "is-cancelled"
                : status.contains("计划") || status.contains("延期") ? "is-planned" : "";
        html.append("<tr data-id=\"").append(id).append("\" data-category=\"").append(attr(category))
                .append("\" data-status=\"").append(attr(status)).append("\" data-name=\"").append(attr(name))
                .append("\" data-region=\"").append(attr(region)).append("\" data-date=\"").append(attr(date))
                .append("\" data-qq=\"").append(attr(qq)).append("\">");
        html.append("<td class=\"thonly-col-date\"><span class=\"thonly-date-value\">")
                .append(esc(date.isBlank() || "暂无".equals(date) ? "日期待定" : date)).append("</span></td>");
        html.append("<td class=\"thonly-col-status\"><span class=\"thonly-status-badge ").append(badgeClass)
                .append("\">").append(esc(status.isBlank() ? "未填写" : status)).append("</span></td>");
        html.append("<td class=\"thonly-col-name\"><button type=\"button\" class=\"thonly-detail\">")
                .append(esc(name)).append("</button></td>");
        html.append("<td class=\"thonly-col-region\"><span class=\"thonly-mobile-label\">地区 · </span>")
                .append(esc(region.isBlank() ? "未填写" : region)).append("</td>");
        html.append("<td class=\"thonly-col-type\"><span class=\"thonly-type-badge\">")
                .append(esc(categoryLabel(category))).append("</span></td>");
        html.append("<td class=\"thonly-col-qq\"><span class=\"thonly-mobile-label\">联络 · </span>")
                .append(esc(qq.isBlank() ? "暂未公布" : qq)).append("</td><td class=\"thonly-actions\"><div>");
        if (canEdit) {
            html.append("<button type=\"button\" class=\"thonly-edit\">编辑</button>");
        }
        if (icsUrl != null) {
            html.append("<a class=\"thonly-ics\" href=\"").append(icsUrl)
                    .append("\" download=\"\" title=\"下载 .ics，加入手机 / 电脑日历\">加入日历</a>");
        }
        html.append("</div></td></tr>");
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "THO" -> "THO";
            case "THP" -> "THP / 茶会";
            case "SCHOOL" -> "高校例会";
            case "LIVE" -> "Live / 音乐会";
            default -> category.isBlank() ? "其他" : category;
        };
    }

    private static String detailDialog(boolean canEdit) {
        return "<div class=\"wiki-dialog-overlay thonly-modal\" id=\"thonly-detail-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"thonly-detail-title\">"
                + "<div class=\"wiki-dialog-title\"><span id=\"thonly-detail-title\">活动详情</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<div class=\"wiki-dialog-body\"><dl class=\"thonly-detail-fields\">"
                + "<dt>日期</dt><dd id=\"thonly-detail-date\"></dd>"
                + "<dt>地区</dt><dd id=\"thonly-detail-region\"></dd>"
                + "<dt>状态</dt><dd id=\"thonly-detail-status\"></dd>"
                + "<dt>类型</dt><dd id=\"thonly-detail-type\"></dd>"
                + "<dt>QQ群 / 联络信息</dt><dd><span id=\"thonly-detail-qq\"></span>"
                + "<button type=\"button\" class=\"wiki-button\" id=\"thonly-copy-qq\">复制</button></dd></dl>"
                + "<p class=\"thonly-footnote\">请以主办方最终公告为准。</p></div>"
                + "<div class=\"wiki-dialog-actions\"><a id=\"thonly-detail-ics\" class=\"wiki-button\" download=\"\">加入日历</a>"
                + (canEdit ? "<button type=\"button\" class=\"wiki-button wiki-button-primary\" id=\"thonly-detail-edit\">编辑活动</button>" : "")
                + "</div></div></div>";
    }

    /** 管理员工具条：AI 整理与 AI 接口设置。 */
    private static String aiBar(ThonlyAiConfig.Settings settings) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"thonly-editor-bar thonly-ai-bar\">");
        if (settings != null && settings.enabled()) {
            html.append("<button type=\"button\" id=\"thonly-ai-import\">")
                    .append(esc(settings.buttonLabel()))
                    .append("</button>");
        }
        html.append("<button type=\"button\" id=\"thonly-ai-config\">AI 设置</button>");
        html.append("</div>");
        return html.toString();
    }

    /** AI 接口配置弹窗。Key 不回显；留空表示保留原值。 */
    private static String aiConfigDialog() {
        return "<div class=\"wiki-dialog-overlay\" id=\"thonly-ai-config-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\">"
                + "<div class=\"wiki-dialog-title\"><span>AI 接口设置</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<div class=\"wiki-dialog-body\">"
                + "<p class=\"wiki-hint\">只有管理员能使用。接口地址、模型和密钥都保存在服务端；"
                + "前端不会拿到密钥，也没有任意 URL 代理接口。</p>"
                + "<label class=\"thonly-ai-check\"><input type=\"checkbox\" id=\"thonly-ai-config-enabled\"/>"
                + " 启用 AI 整理按钮</label>"
                + "<div class=\"form-field\"><label for=\"thonly-ai-config-label\">按钮文字</label>"
                + "<input type=\"text\" id=\"thonly-ai-config-label\" maxlength=\"60\" placeholder=\"AI 整理导入\"/></div>"
                + "<div class=\"form-field\"><label for=\"thonly-ai-config-base-url\">OpenAI 兼容接口地址</label>"
                + "<input type=\"url\" id=\"thonly-ai-config-base-url\" maxlength=\"2048\" "
                + "placeholder=\"https://api.openai.com/v1\"/>"
                + "<div class=\"wiki-hint\">会自动拼接 <code>/chat/completions</code>；也可以直接填完整地址。"
                + "默认只允许公网 HTTPS。</div></div>"
                + "<div class=\"form-field\"><label for=\"thonly-ai-config-model\">模型</label>"
                + "<input type=\"text\" id=\"thonly-ai-config-model\" maxlength=\"200\" placeholder=\"gpt-4o-mini\"/></div>"
                + "<div class=\"form-field\"><label for=\"thonly-ai-config-key\">API Key</label>"
                + "<input type=\"password\" id=\"thonly-ai-config-key\" maxlength=\"1000\" autocomplete=\"new-password\" "
                + "placeholder=\"留空表示不修改\"/>"
                + "<div class=\"wiki-hint\" id=\"thonly-ai-key-hint\">未配置密钥。</div></div>"
                + "<label class=\"thonly-ai-check\"><input type=\"checkbox\" id=\"thonly-ai-config-clear-key\"/>"
                + " 清除已保存的 API Key</label>"
                + "<label class=\"thonly-ai-check\"><input type=\"checkbox\" id=\"thonly-ai-config-allow-private\"/>"
                + " 允许 http / 本机 / 内网端点（仅用于本地 Ollama、vLLM 等；会增加 SSRF 风险）</label>"
                + "<p class=\"form-message\" id=\"thonly-ai-config-message\" hidden></p>"
                + "</div>"
                + "<div class=\"wiki-dialog-actions thonly-dialog-actions\">"
                + "<button type=\"button\" id=\"thonly-ai-config-save\" class=\"wiki-button wiki-button-primary\">保存</button>"
                + "<button type=\"button\" class=\"wiki-button thonly-ai-cancel\">取消</button>"
                + "</div></div></div>";
    }

    /** AI 整理弹窗：粘贴文案 → 预览候选操作 → 管理员确认应用。 */
    private static String aiImportDialog() {
        return "<div class=\"wiki-dialog-overlay\" id=\"thonly-ai-dialog\" hidden>"
                + "<div class=\"wiki-dialog thonly-ai-dialog\" role=\"dialog\" aria-modal=\"true\">"
                + "<div class=\"wiki-dialog-title\"><span>AI 整理宣传文案</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<div class=\"wiki-dialog-body\">"
                + "<p class=\"wiki-hint\">把活动宣传文案整段粘进来。AI 只生成候选变更；"
                + "下面预览并勾选后才会写入当前年份的速查表，不会删除已有活动。</p>"
                + "<textarea id=\"thonly-ai-text\" class=\"thonly-ai-text\" rows=\"12\" maxlength=\"120000\" "
                + "placeholder=\"例如：活动名称、时间、地点、QQ群、状态等宣传文案……\"></textarea>"
                + "<div class=\"thonly-ai-count\" id=\"thonly-ai-count\">0 / 120000</div>"
                + "<div class=\"form-actions thonly-ai-actions\">"
                + "<button type=\"button\" id=\"thonly-ai-organize\" class=\"wiki-button wiki-button-primary\">"
                + "让 AI 整理</button>"
                + "<button type=\"button\" id=\"thonly-ai-apply\" class=\"wiki-button\" hidden>应用选中的修改</button>"
                + "</div>"
                + "<p class=\"form-message\" id=\"thonly-ai-status\" hidden></p>"
                + "<div id=\"thonly-ai-result\" class=\"thonly-ai-result\" hidden></div>"
                + "</div>"
                + "<div class=\"wiki-dialog-actions thonly-dialog-actions\">"
                + "<button type=\"button\" class=\"wiki-button thonly-ai-cancel\">关闭</button>"
                + "</div></div></div>";
    }

    /** 编辑抽屉复用通用按钮与主题变量，保留现有字段 ID 和写接口。 */
    private static String editDialog() {
        return "<div class=\"wiki-dialog-overlay thonly-modal thonly-edit-overlay\" id=\"thonly-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"thonly-dialog-title\">"
                + "<div class=\"wiki-dialog-title\"><span id=\"thonly-dialog-title\">编辑活动</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<form id=\"thonly-edit-form\"><div class=\"wiki-dialog-body\"><div class=\"thonly-edit-fields\">"
                + "<div class=\"thonly-edit-wide\"><label for=\"thonly-f-name\">活动名称 <span aria-hidden=\"true\">*</span></label>"
                + "<input type=\"text\" id=\"thonly-f-name\" required maxlength=\"255\"/></div>"
                + "<div><label for=\"thonly-f-category\">活动类型</label><select id=\"thonly-f-category\">"
                + "<option value=\"tho\">THO</option><option value=\"thp\">THP / 茶会</option>"
                + "<option value=\"school\">高校例会</option><option value=\"live\">Live / 音乐会</option></select></div>"
                + "<div><label for=\"thonly-f-status\">状态</label><input type=\"text\" id=\"thonly-f-status\" maxlength=\"20\""
                + " placeholder=\"计划 / 定档 / 已取消…\"/></div>"
                + "<div class=\"thonly-edit-wide\"><label for=\"thonly-f-region\">省份 / 地区</label>"
                + "<select id=\"thonly-f-region\"></select></div>"
                + "<div class=\"thonly-edit-wide\"><label for=\"thonly-f-date\">活动日期</label>"
                + "<div class=\"thonly-date-field\"><input type=\"date\" id=\"thonly-f-date\"/>"
                + "<label class=\"thonly-date-none\"><input type=\"checkbox\" id=\"thonly-f-date-none\"/>日期待定</label>"
                + "<span class=\"thonly-date-hint\" id=\"thonly-f-date-hint\" hidden></span></div></div>"
                + "<div class=\"thonly-edit-wide\"><label for=\"thonly-f-qq\">QQ群 / 联络信息（可选）</label>"
                + "<input type=\"text\" id=\"thonly-f-qq\" maxlength=\"255\"/></div></div>"
                + "<p class=\"thonly-edit-message\" id=\"thonly-edit-message\" role=\"alert\" hidden></p></div>"
                + "<div class=\"wiki-dialog-actions\">"
                + "<button type=\"button\" id=\"thonly-delete\" class=\"wiki-button wiki-button-quiet\" hidden>删除活动</button>"
                + "<button type=\"button\" class=\"wiki-button thonly-dialog-cancel\">取消</button>"
                + "<button type=\"submit\" id=\"thonly-save\" class=\"wiki-button wiki-button-primary\">保存活动</button>"
                + "</div></form></div></div>";
    }

    /** 变更历史弹窗（列表 + 每条一个「回滚」按钮，由 activity.js 填充）。 */
    private static String historyDialog() {
        return "<div class=\"wiki-dialog-overlay thonly-modal\" id=\"thonly-history-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"thonly-history-title\">"
                + "<div class=\"wiki-dialog-title\"><span id=\"thonly-history-title\">变更历史</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<div class=\"wiki-dialog-body\"><ul class=\"thonly-history-list\" id=\"thonly-history-list\"></ul></div>"
                + "</div></div>";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String attr(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static int intOf(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
