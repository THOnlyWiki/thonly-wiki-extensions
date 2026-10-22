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

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「线下活动速查表」页面：**接管正文渲染**（页面没建也不 404），并且是**唯一带编辑功能**的地方
 * （独立页 /excel 只读）。
 * <ul>
 *     <li>外观照原站 {@code D:\wwwroot\THONLY_CC_PROJECT\excel} 移植：表格 / 搜索框 / 两个下拉 /
 *         链接样式；wiki 页面里**不套白卡片、不放大标题**（皮肤正文区本身就是容器，标题由皮肤显示）、
 *         也不放「回到顶部」（皮肤自带）；</li>
 *     <li>有 {@code edit} 权限的人（默认登录用户，可在 特殊:权限配置 按用户组调整）在「操作」列看到
 *         **文字链接「编辑」**（点开是弹窗表单），底部还有「添加活动」（同一个弹窗）与「变更历史」
 *         （弹窗列表，可回滚）——增删改查全在这一页；</li>
 *     <li>每条活动「操作」列固定有「加入日历」（.ics 下载）；不再有 Google 链接。</li>
 * </ul>
 * 样式与脚本的注入见 {@link ActivityHeadProvider}（{@code headItems} 在「页面不存在」路径下不生效，踩过坑）。
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
        List<Map<String, Object>> rows = store.list(year);
        boolean canEdit = accessCheck.can(AccessAction.EDIT, PAGE_TITLE, context.viewer());
        boolean canAdmin = accessCheck.can(AccessAction.PROTECT, PAGE_TITLE, context.viewer());

        StringBuilder html = new StringBuilder(16384);
        html.append("<div class=\"thonly-activity-app thonly-app\">");

        html.append("<p class=\"thonly-tips\">Tips：登录后即可编辑（默认登录用户都有权限，可在 ")
                .append("特殊:权限配置 按用户组调整）；发现表格里信息有误，欢迎来群里捉虫。")
                .append("<a target=\"_blank\" rel=\"noopener\" style=\"color:#EC7063;\"")
                .append(" href=\"https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&amp;k=yI4hzduOvfoisCSfcG7RRD5O5daf_UDi\">")
                .append("东方Project线下活动维基群：868256565</a></p>");

        // 搜索 + 两个下拉（居中）
        html.append("<div class=\"thonly-search-container\">")
                .append("<input type=\"search\" class=\"thonly-search\" placeholder=\"搜索活动(可筛选省份)...\"/>")
                .append("</div>");

        html.append("<label class=\"thonly-condition\">");
        html.append("<select class=\"year\" id=\"thonly-year\" name=\"年份\">");
        for (int value : years) {
            html.append("<option value=\"").append(value).append("\"")
                    .append(value == year ? " selected" : "").append(">").append(value).append("</option>");
        }
        html.append("</select>");
        html.append("<select class=\"type\" id=\"thonly-type\" name=\"类型\">")
                .append("<option value=\"ALL\">全部</option>")
                .append("<option value=\"TOUHOU_ONLY\">THO</option>")
                .append("<option value=\"TOUHOU_PARTY\">THP&amp;茶会</option>")
                .append("<option value=\"SCHOOL_PARTY\">高校例会</option>")
                .append("<option value=\"LIVE_OR_MUSIC\">Live&amp;音乐会</option>")
                .append("</select></label>");

        // 表格：永远是文本（编辑在弹窗里做）
        html.append("<table class=\"thonly-excel\" data-year=\"").append(year).append("\">");
        html.append("<thead><tr>")
                .append("<th style=\"width:8%\">状态</th>")
                .append("<th style=\"width:29%\">名字</th>")
                .append("<th style=\"width:17%\">地区</th>")
                .append("<th style=\"width:17%\">日期</th>")
                .append("<th style=\"width:13%\">QQ群</th>")
                .append("<th style=\"width:16%\">操作</th>")
                .append("</tr></thead><tbody>");
        for (Map<String, Object> row : rows) {
            long id = row.get("id") instanceof Number number ? number.longValue() : 0;
            String status = text(row.get("status"));
            String name = text(row.get("name"));
            String region = text(row.get("region"));
            String date = text(row.get("date"));
            String qq = text(row.get("qq"));
            String category = text(row.get("category")).toUpperCase(Locale.ROOT);
            String icsUrl = CalendarLinks.icsUrl(year, id, date);
            html.append("<tr data-id=\"").append(id).append("\" data-category=\"").append(category)
                    .append("\" data-status=\"").append(attr(status))
                    .append("\" data-name=\"").append(attr(name))
                    .append("\" data-region=\"").append(attr(region))
                    .append("\" data-date=\"").append(attr(date))
                    .append("\" data-qq=\"").append(attr(qq)).append("\">");
            html.append("<td").append(status.contains("取消") ? " class=\"cancelled\"" : "").append(">")
                    .append(esc(status.isEmpty() ? "—" : status)).append("</td>");
            html.append("<td>").append(esc(name)).append("</td>");
            html.append("<td>").append(esc(region)).append("</td>");
            html.append("<td>").append(esc(date)).append("</td>");
            html.append("<td>").append(esc(qq)).append("</td>");
            html.append("<td class=\"thonly-actions\">");
            if (canEdit) {
                html.append("<a href=\"#\" class=\"thonly-edit\" role=\"button\">编辑</a>");
            }
            if (icsUrl != null) {
                html.append("<a href=\"").append(icsUrl)
                        .append("\" download=\"\" title=\"下载 .ics，加入手机 / 电脑日历\">加入日历</a>");
            }
            html.append("</td></tr>");
        }
        html.append("</tbody></table>");

        if (canEdit) {
            // 按钮不覆盖颜色：交给主站自己的 button 主题（避免「悬停变白」这类问题）
            html.append("<div class=\"thonly-editor-bar\">")
                    .append("<button type=\"button\" id=\"thonly-add\">添加活动</button>")
                    .append("<button type=\"button\" id=\"thonly-history\">变更历史</button>")
                    .append("</div>");
            html.append(editDialog());
            html.append(historyDialog());
        }
        if (canAdmin) {
            html.append(aiBar(aiConfig.settings()));
            html.append(aiConfigDialog());
            html.append(aiImportDialog());
        }

        html.append("<p class=\"thonly-message\" id=\"thonly-message\" hidden></p>");
        html.append("</div>");
        return html.toString();
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

    /** 编辑 / 添加弹窗（复用 core 的 {@code .wiki-dialog} 样式；字段由 activity.js 填 / 读）。 */
    private static String editDialog() {
        return "<div class=\"wiki-dialog-overlay\" id=\"thonly-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"thonly-dialog-title\">"
                + "<div class=\"wiki-dialog-title\"><span id=\"thonly-dialog-title\">编辑活动</span>"
                + "<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>"
                + "<div class=\"wiki-dialog-body\"><div class=\"wiki-dialog-grid\">"
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-status\">状态</label>"
                + "<input type=\"text\" id=\"thonly-f-status\" maxlength=\"20\" placeholder=\"计划 / 定档 / 已取消…\"/>"
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-category\">类型</label>"
                + "<select id=\"thonly-f-category\">"
                + "<option value=\"tho\">THO</option><option value=\"thp\">THP&amp;茶会</option>"
                + "<option value=\"school\">高校例会</option><option value=\"live\">Live&amp;音乐会</option>"
                + "</select>"
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-name\">名字</label>"
                + "<input type=\"text\" id=\"thonly-f-name\" style=\"grid-column: 2 / -1\"/>"
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-region\">地区</label>"
                + "<input type=\"text\" id=\"thonly-f-region\"/>"
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-qq\">QQ群</label>"
                + "<input type=\"text\" id=\"thonly-f-qq\"/>"
                // 日期单独整行：日期选择器 + 「暂无」勾选（+ 旧数据非标准日期的提示）
                + "<label class=\"wiki-dialog-label\" for=\"thonly-f-date\">日期</label>"
                + "<div class=\"thonly-date-field\">"
                + "<input type=\"date\" id=\"thonly-f-date\"/>"
                + "<label class=\"wiki-dialog-check thonly-date-none\" title=\"日期未定，按「暂无」保存\">"
                + "<input type=\"checkbox\" id=\"thonly-f-date-none\"/>暂无</label>"
                + "<span class=\"thonly-date-hint\" id=\"thonly-f-date-hint\" hidden></span>"
                + "</div>"
                + "</div></div>"
                // 按钮两行：第一行 保存 / 删除，第二行整行「取消」
                + "<div class=\"wiki-dialog-actions thonly-dialog-actions\">"
                + "<div class=\"thonly-dialog-main\">"
                + "<button type=\"button\" id=\"thonly-save\" class=\"wiki-button wiki-button-primary\">保存</button>"
                + "<button type=\"button\" id=\"thonly-delete\" class=\"wiki-button wiki-button-danger\" hidden>删除</button>"
                + "</div>"
                + "<button type=\"button\" class=\"wiki-button thonly-dialog-cancel\">取消</button>"
                + "</div></div></div>";
    }

    /** 变更历史弹窗（列表 + 每条一个「回滚」按钮，由 activity.js 填充）。 */
    private static String historyDialog() {
        return "<div class=\"wiki-dialog-overlay\" id=\"thonly-history-dialog\" hidden>"
                + "<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\">"
                + "<div class=\"wiki-dialog-title\"><span>变更历史</span>"
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
