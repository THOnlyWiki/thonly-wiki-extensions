package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从已有的活动页面 wikitext 中读取生成器表单需要的字段。
 * <p>
 * 只支持标准模板页面：
 * <ul>
 *     <li>主页面：{@code 信息集合框}、{@code 活动信息}、{@code 单届信息}、
 *         {@code 历届宣传画廊}；</li>
 *     <li>子页面：{@code 具体展会/LOGO}、{@code 具体展会/简介/日程}、{@code gallery}。</li>
 * </ul>
 * 潮汕 THONLY 那种用手写 wikitable 代替 {@code 单届信息} 的页面会解析失败，
 * 由上层回退到普通编辑；这是有意为之，避免自动重建时改坏手工内容。
 */
public final class PageGeneratorParser {

    private PageGeneratorParser() {
    }

    public static Optional<Map<String, Object>> parseMain(String title, String content) {
        if (content == null || !content.contains("{{信息集合框")) {
            return Optional.empty();
        }
        Optional<Block> infoBlock = block(content, "信息集合框");
        if (infoBlock.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> info = params(infoBlock.get().text());
        Map<String, String> activity = block(content, "活动信息")
                .map(block -> params(block.text())).orElse(Map.of());

        List<Map<String, Object>> editions = new ArrayList<>();
        for (Block block : blocks(content, "单届信息")) {
            List<String> values = positional(block.text());
            if (values.size() < 4) {
                continue;
            }
            String link = values.get(0);
            String name = linkName(link, title);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("date", values.get(1));
            row.put("place", values.get(2));
            row.put("booths", values.get(3));
            editions.add(row);
        }
        // 手工表格页面不在自动导入范围内。
        if (editions.isEmpty() && !content.contains("{{单届信息")) {
            return Optional.empty();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", PageGeneratorService.KIND_MAIN);
        payload.put("title", title);
        payload.put("displayName", first(info.get("活动名称"), activity.get("活动名称"), title));
        payload.put("shortName", first(activity.get("活动简称"), info.get("活动别称"), ""));
        payload.put("type", first(activity.get("类型"), ""));
        payload.put("region", first(activity.get("地区"), info.get("地区"), ""));
        payload.put("host", first(activity.get("主办方"), ""));
        payload.put("groupNo", first(activity.get("QQ群"), info.get("活动群号"), ""));
        payload.put("bilibili", first(activity.get("官方哔哩哔哩"), info.get("哔哩哔哩"), ""));
        payload.put("weibo", first(activity.get("官方微博"), ""));
        payload.put("website", first(activity.get("官方网站"), info.get("官方网站"), ""));
        payload.put("market", first(activity.get("东方市场"), ""));
        payload.put("logo", first(info.get("图片"), ""));
        // 只读取「历届宣传画廊」，不要把页面后面的「历代LOGO」等手动画廊吞进来。
        String gallerySource = block(content, "历届宣传画廊").map(Block::text).orElse("");
        payload.put("gallery", gallery(gallerySource));
        payload.put("editions", editions);
        payload.put("customContent", "");
        payload.put("needsWork", content.contains("{{需要补充}}"));
        payload.put("imported", true);
        return Optional.of(payload);
    }

    public static Optional<Map<String, Object>> parseSub(String title, String content) {
        if (content == null || !content.contains("{{具体展会")) {
            return Optional.empty();
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (Block block : blocks(content, "具体展会/简介/日程")) {
            Map<String, String> params = params(block.text());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", first(params.get("标题"), ""));
            row.put("type", first(params.get("类型"), ""));
            row.put("date", first(params.get("日期"), ""));
            row.put("address", first(params.get("地址"), ""));
            row.put("booths", first(params.get("摊位数"), ""));
            days.add(row);
        }
        if (days.isEmpty() && !content.contains("{{具体展会/简介/日程")) {
            return Optional.empty();
        }
        String logo = block(content, "具体展会/LOGO")
                .map(block -> inlineArgument(block.text(), "具体展会/LOGO")).orElse("");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", PageGeneratorService.KIND_SUB);
        payload.put("parent", parentOf(title));
        payload.put("subName", subNameOf(title));
        payload.put("logo", logo);
        payload.put("gallery", gallery(content));
        payload.put("days", days);
        payload.put("customContent", "");
        payload.put("needsWork", content.contains("{{需要补充}}"));
        payload.put("imported", true);
        return Optional.of(payload);
    }

    /** 主页面的生成器受管区块分别替换；不认识的源码原样保留。 */
    public static String patchMain(String title, String source, Map<String, Object> payload) {
        String result = syncNeedsWork(source, "{{展会}}", booleanValue(payload.get("needsWork")));
        result = replace(result, "信息集合框", PageGeneratorService.mainInfoBox(payload));
        result = replace(result, "活动信息", PageGeneratorService.mainActivityInfo(payload));
        result = replace(result, "历届信息", PageGeneratorService.mainSchedule(payload, title));
        result = replace(result, "历届宣传画廊", PageGeneratorService.mainGallery(payload));
        return result;
    }

    /** 子页面只替换 Logo / 日程 / 返图三个受管区域，其它手写内容保留。 */
    public static String patchSub(String title, String source, Map<String, Object> payload) {
        String result = syncNeedsWork(source, "{{具体展会}}", booleanValue(payload.get("needsWork")));
        result = replace(result, "具体展会/LOGO", PageGeneratorService.subLogoBlock(payload));
        result = replaceAll(result, "具体展会/简介/日程", PageGeneratorService.subDayBlocks(payload));
        if (result.contains("== 返图 ==") || result.contains("==返图==")) {
            result = replaceSection(result, "返图", PageGeneratorService.subGalleryBlock(payload));
        }
        return result;
    }

    private static String syncNeedsWork(String source, String topMarker, boolean needed) {
        String result = source;
        if (needed) {
            if (!result.contains("{{需要补充}}")) {
                int index = result.indexOf(topMarker);
                if (index >= 0) {
                    int lineEnd = result.indexOf('\n', index);
                    if (lineEnd >= 0) {
                        result = result.substring(0, lineEnd + 1)
                                + "{{需要补充}}\n" + result.substring(lineEnd + 1);
                    }
                }
            }
        } else {
            result = result.replace("{{需要补充}}\n", "").replace("{{需要补充}}", "");
        }
        return result;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    // ==================== wikitext 基础解析 ====================

    private static Optional<Block> block(String source, String name) {
        List<Block> all = blocks(source, name);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    private static List<Block> blocks(String source, String name) {
        List<Block> result = new ArrayList<>();
        String marker = "{{" + name;
        int from = 0;
        while (from < source.length()) {
            int start = source.indexOf(marker, from);
            if (start < 0) {
                break;
            }
            int after = start + marker.length();
            if (after < source.length()) {
                char next = source.charAt(after);
                if (!(Character.isWhitespace(next) || next == '|' || next == '}' || next == '\n')) {
                    from = after;
                    continue;
                }
            }
            int end = templateEnd(source, start);
            if (end <= start) {
                from = after;
                continue;
            }
            result.add(new Block(start, end, source.substring(start, end)));
            from = end;
        }
        return result;
    }

    private static String replace(String source, String name, String replacement) {
        Optional<Block> block = block(source, name);
        if (block.isEmpty()) {
            return source;
        }
        Block value = block.get();
        return source.substring(0, value.start()) + replacement + source.substring(value.end());
    }

    private static String replaceAll(String source, String name, String replacement) {
        List<Block> found = blocks(source, name);
        if (found.isEmpty()) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length() + replacement.length());
        int cursor = 0;
        for (int index = 0; index < found.size(); index++) {
            Block block = found.get(index);
            out.append(source, cursor, block.start());
            if (index == 0) {
                out.append(replacement);
            }
            cursor = block.end();
        }
        out.append(source.substring(cursor));
        return out.toString();
    }

    private static String replaceSection(String source, String heading, String replacement) {
        String[] markers = {"== " + heading + " ==", "==" + heading + "=="};
        int start = -1;
        int markerLength = 0;
        for (String marker : markers) {
            start = source.indexOf(marker);
            if (start >= 0) {
                markerLength = marker.length();
                break;
            }
        }
        if (start < 0) {
            return source;
        }
        // 只找同级或更高级的标题（== ...），不要把 === Day 1 === 当成下一节。
        java.util.regex.Matcher next = java.util.regex.Pattern.compile("(?m)^==(?!=)")
                .matcher(source);
        int end = source.length();
        if (next.find(start + markerLength)) {
            end = next.start();
        }
        int box = source.indexOf("{{页面注释", start + markerLength);
        if (box >= 0 && box < end) {
            end = box;
        }
        return source.substring(0, start) + "== " + heading + " ==\n" + replacement + "\n"
                + source.substring(end);
    }

    private static int templateEnd(String source, int start) {
        int depth = 0;
        for (int index = start; index < source.length() - 1; index++) {
            if (source.charAt(index) == '{' && source.charAt(index + 1) == '{') {
                depth++;
                index++;
                continue;
            }
            if (source.charAt(index) == '}' && source.charAt(index + 1) == '}') {
                depth--;
                index++;
                if (depth == 0) {
                    return index + 1;
                }
            }
        }
        return source.length();
    }

    private static Map<String, String> params(String block) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : block.split("\\R")) {
            String text = line.trim();
            if (!text.startsWith("|")) {
                continue;
            }
            text = text.substring(1);
            int equal = text.indexOf('=');
            if (equal <= 0) {
                continue;
            }
            values.put(text.substring(0, equal).trim(), text.substring(equal + 1).trim());
        }
        return values;
    }

    private static List<String> positional(String block) {
        List<String> values = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("|")) {
                values.add(text.substring(1).trim());
            }
        }
        return values;
    }

    private static String inlineArgument(String block, String name) {
        String text = block.trim();
        String prefix = "{{" + name;
        if (text.startsWith(prefix)) {
            text = text.substring(prefix.length()).trim();
        }
        if (text.startsWith("|")) {
            text = text.substring(1).trim();
        }
        if (text.endsWith("}}")) {
            text = text.substring(0, text.length() - 2).trim();
        }
        return text;
    }

    private static List<String> gallery(String content) {
        List<String> files = new ArrayList<>();
        int from = 0;
        while (true) {
            int open = content.indexOf("<gallery", from);
            if (open < 0) {
                break;
            }
            int end = content.indexOf("</gallery>", open);
            if (end < 0) {
                break;
            }
            String body = content.substring(content.indexOf('>', open) + 1, end);
            for (String line : body.split("\\R")) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("<!--")) {
                    continue;
                }
                // 保留完整的 `文件|说明` 行，避免编辑已有页面时丢画廊说明。
                if (!text.isEmpty()) {
                    files.add(text);
                }
            }
            from = end + "</gallery>".length();
        }
        return files;
    }

    private static String linkName(String value, String title) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("[[") && text.endsWith("]]")) {
            text = text.substring(2, text.length() - 2);
            int pipe = text.indexOf('|');
            if (pipe >= 0) {
                text = text.substring(pipe + 1);
            }
        }
        String prefix = title + "/";
        if (text.startsWith(prefix)) {
            text = text.substring(prefix.length());
        }
        return text.trim();
    }

    private static String parentOf(String title) {
        int slash = title.lastIndexOf('/');
        return slash < 0 ? "" : title.substring(0, slash);
    }

    private static String subNameOf(String title) {
        int slash = title.lastIndexOf('/');
        return slash < 0 ? title : title.substring(slash + 1);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record Block(int start, int end, String text) {
    }
}
