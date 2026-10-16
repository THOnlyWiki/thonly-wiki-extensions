package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.PageDisplayContext;
import cc.thonly.wikikernel.common.extension.PageDisplayHook;
import cc.thonly.wikikernel.common.service.CategoryMember;
import cc.thonly.wikikernel.common.service.CategoryStore;
import cc.thonly.wikikernel.common.web.WikiUrls;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「活动分布地图」页面：**接管正文渲染**（线上原版是 MediaWiki 页面里嵌 iframe 指向 {@code /map/}，
 * 这里不嵌 iframe）。
 * <p>
 * 数据源与线上一致：**Wiki 固定分类** {@code 分类:<地图名>活动}（例如 {@code 分类:福建活动}、
 * {@code 分类:广西活动}，成员是活动词条），通过 core 的分类索引桥
 * {@link CategoryStore#members(String, int)} 读取；与「线下活动速查表」的表格数据无关。
 * <ul>
 *     <li>本地内置 ECharts + china.js 画中国地图（资源在插件里
 *         {@code /plugins/thonly-wiki-extensions/map/}）；</li>
 *     <li>34 个省级区域按分类词条数着色（visualMap；没有分类词的省份为 0）；</li>
 *     <li>点击省份 → 弹窗列出 {@code 分类:X活动} 的词条（链接到各词条；与线上一样**排除模板命名空间**，
 *         否则「模板:X活动」会混进列表），标题链到分类页；</li>
 *     <li>「全屏预览」按钮：Fullscreen API 全屏看地图（失败退回 CSS 假全屏），进入 / 退出自动 resize。</li>
 * </ul>
 * 数据一次性随页面渲染进 {@code <script type="application/json" id="thonly-map-data">}，
 * 前端不再发请求（原版每次点省份都要调一次 MediaWiki 分类 API）。
 */
@Component
public class MapPageHook implements PageDisplayHook {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.MAP_PAGE_TITLE;

    /** ECharts {@code china.js} 里的 34 个区域名（就是分类名前缀：{@code 分类:<名字>活动}）。 */
    private static final List<String> MAP_NAMES = List.of(
            "内蒙古", "黑龙江", "台湾", "河北", "山西", "辽宁", "吉林", "江苏", "浙江", "安徽", "福建",
            "江西", "山东", "河南", "湖北", "湖南", "广东", "广西", "海南", "四川", "贵州", "云南",
            "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "北京", "天津", "上海", "重庆", "香港", "澳门");

    /** 模板命名空间 id（与线上一致：列表里排除「模板:X活动」）。 */
    private static final int TEMPLATE_NAMESPACE = 10;

    @Autowired
    private CategoryStore categoryStore;

    @Override
    public int order() {
        return 61;
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
        List<Map<String, Object>> provinces = new ArrayList<>();
        int totalMembers = 0;
        for (String name : MAP_NAMES) {
            String category = "分类:" + name + "活动";
            List<Map<String, Object>> members = membersOf(category);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", name);
            view.put("category", category);
            view.put("categoryUrl", WikiUrls.pagePath(category));
            view.put("value", members.size());
            view.put("members", members);
            provinces.add(view);
            totalMembers += members.size();
        }

        StringBuilder html = new StringBuilder(32768);
        html.append("<div class=\"thonly-map-app\">");
        html.append("<div class=\"thonly-map-bar\">")
                .append("<span class=\"thonly-map-hint\">滚轮缩放 / 拖拽平移；点击省份查看该地区的活动词条")
                .append("（数据来自分类，如 分类:福建活动；共 ").append(totalMembers).append(" 个词条）。</span>")
                .append("<button type=\"button\" id=\"thonly-map-fullscreen\">全屏预览</button>")
                .append("</div>");
        html.append("<div class=\"thonly-map-wrap\" id=\"thonly-map-wrap\">")
                .append("<div class=\"thonly-map-canvas\" id=\"thonly-map-canvas\"></div>")
                .append("</div>");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", totalMembers);
        payload.put("provinces", provinces);
        html.append("<script type=\"application/json\" id=\"thonly-map-data\">")
                .append(json(payload)).append("</script>");

        // 「全屏预览」= 大弹窗（不是浏览器真全屏）：地图画布移进大弹窗，点省份照样弹分类内容
        html.append("<div class=\"wiki-dialog-overlay thonly-map-modal\" id=\"thonly-map-modal\" hidden>")
                .append("<div class=\"wiki-dialog thonly-map-modal-box\" role=\"dialog\" aria-modal=\"true\">")
                .append("<div class=\"wiki-dialog-title\"><span>活动分布地图</span>")
                .append("<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>")
                .append("<div class=\"thonly-map-modal-body\" id=\"thonly-map-modal-body\"></div>")
                .append("</div></div>");

        // 省份词条弹窗（复用 core 的 .wiki-dialog 样式；z-index 高于大弹窗）
        html.append("<div class=\"wiki-dialog-overlay thonly-map-dialog-overlay\" id=\"thonly-map-dialog\" hidden>")
                .append("<div class=\"wiki-dialog\" role=\"dialog\" aria-modal=\"true\">")
                .append("<div class=\"wiki-dialog-title\"><span id=\"thonly-map-dialog-title\">活动</span>")
                .append("<button type=\"button\" class=\"wiki-dialog-close\" aria-label=\"关闭\">×</button></div>")
                .append("<div class=\"wiki-dialog-body\"><ul class=\"thonly-map-list\" id=\"thonly-map-dialog-list\"></ul></div>")
                .append("</div></div>");
        html.append("</div>");
        return html.toString();
    }

    /** {@code 分类:X活动} 的词条（排除模板命名空间；分类索引不可用时返回空列表，不影响地图）。 */
    private List<Map<String, Object>> membersOf(String category) {
        List<Map<String, Object>> members = new ArrayList<>();
        try {
            for (CategoryMember member : categoryStore.members(category, 500)) {
                if (member.namespaceId() == TEMPLATE_NAMESPACE) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", member.title());
                item.put("url", WikiUrls.pagePath(member.title()));
                members.add(item);
            }
        } catch (RuntimeException exception) {
            // 分类索引异常时地图照常渲染（该省份显示 0）
        }
        return members;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 极简 JSON 序列化（String / Number / Boolean / Map / List；{@code < >&} 转义避免提前闭合 script）。 */
    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(json(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                out.append(json(list.get(index)));
            }
            return out.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16).append('"');
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
