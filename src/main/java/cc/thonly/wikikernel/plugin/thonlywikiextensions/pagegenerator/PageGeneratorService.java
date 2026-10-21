package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.common.service.EditSubmitter;
import cc.thonly.wikikernel.common.service.PageSnapshot;
import cc.thonly.wikikernel.common.service.WikiPageStore;
import cc.thonly.wikikernel.common.service.WikiText;
import cc.thonly.wikikernel.common.web.WikiUrls;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页面生成器：把结构化表单转换成 thonly 风格的活动页面 wikitext。
 * <p>
 * 当前只处理两类页面：
 * <ul>
 *     <li>{@code main}：活动系列主页（信息集合框 / 活动信息 / 历届信息 / 宣传画廊）；</li>
 *     <li>{@code sub}：某一届活动子页面（具体展会 / LOGO / 日程 / 返图）。</li>
 * </ul>
 * 创建后的页面仍是普通 wiki 页面；生成器保存的结构化 payload 存在
 * {@link PageGeneratorStore}，只用于再次打开表单编辑。
 */
@Service
public class PageGeneratorService {

    public static final String KIND_MAIN = "main";
    public static final String KIND_SUB = "sub";
    public static final String HELP_MAIN = "快速创建页面";
    public static final String HELP_SUB = "快速创建子页面";
    /** 旧帮助页路由：仍可用，但推荐使用上面的新入口（Sidebar 会迁移到新入口）。 */
    public static final String LEGACY_HELP_MAIN = "帮助:页面生成器";
    public static final String LEGACY_HELP_SUB = "帮助:子页面生成器";

    public static boolean isMainHelp(String title) {
        return HELP_MAIN.equals(title) || LEGACY_HELP_MAIN.equals(title);
    }

    public static boolean isSubHelp(String title) {
        return HELP_SUB.equals(title) || LEGACY_HELP_SUB.equals(title);
    }

    private static final Pattern INVALID_TITLE = Pattern.compile("[\\r\\n#<>\\[\\]{}|]");
    private static final Pattern PROVINCE_PREFIX = Pattern.compile(
            "^(.+?(?:壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市))");
    private static final Pattern PROVINCE_SUFFIX = Pattern.compile(
            "(壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市)$");

    private final PageGeneratorStore store;
    private final WikiPageStore pageStore;
    private final EditSubmitter editSubmitter;
    private final WikiText wikiText;
    private final AccessCheck accessCheck;
    private final ObjectMapper objectMapper;

    public PageGeneratorService(PageGeneratorStore store,
                                WikiPageStore pageStore,
                                EditSubmitter editSubmitter,
                                WikiText wikiText,
                                AccessCheck accessCheck,
                                ObjectMapper objectMapper) {
        this.store = store;
        this.pageStore = pageStore;
        this.editSubmitter = editSubmitter;
        this.wikiText = wikiText;
        this.accessCheck = accessCheck;
        this.objectMapper = objectMapper;
    }

    // ==================== 页面加载 ====================

    /** 新建表单的空白数据；{@code title} 为空表示不加载已有页面。 */
    public Map<String, Object> load(String requestedTitle, String requestedKind, Viewer viewer) {
        String kind = KIND_SUB.equals(requestedKind) ? KIND_SUB : KIND_MAIN;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("kind", kind);
        response.put("editing", false);
        response.put("payload", blankPayload(kind));
        String title;
        try {
            title = cleanTitle(requestedTitle);
        } catch (IllegalArgumentException exception) {
            return failure("badTitle", exception.getMessage());
        }
        if (title.isEmpty()) {
            return response;
        }
        if (!viewer.loggedIn()) {
            return failure("unauthorized", "请先登录后再使用页面生成器。");
        }
        Optional<PageGeneratorStore.Document> document = store.find(title);
        if (document.isEmpty()) {
            PageSnapshot existing = pageStore.byTitle(title).orElse(null);
            if (existing == null) {
                // 标题尚未创建：保留标题，直接进入创建流程。
                Map<String, Object> blank = blankPayload(kind);
                blank.put("title", title);
                response.put("payload", blank);
                return response;
            }
            if (!accessCheck.can(AccessAction.EDIT, title, viewer)) {
                return failure("forbidden", "你没有编辑这个页面的权限。");
            }
            String content = pageStore.content(existing.id()).orElse("");
            Optional<Map<String, Object>> parsed = KIND_SUB.equals(kind)
                    ? PageGeneratorParser.parseSub(title, content)
                    : PageGeneratorParser.parseMain(title, content);
            if (parsed.isEmpty()) {
                response.put("ok", false);
                response.put("error", "notManaged");
                response.put("message", "这个页面的结构还不能自动识别（可能使用了手工表格）。请使用普通编辑或可视化编辑。");
                response.put("editUrl", WikiUrls.pagePath(title) + "?action=edit");
                return response;
            }
            response.put("ok", true);
            response.put("kind", kind);
            response.put("editing", true);
            response.put("imported", true);
            response.put("title", title);
            response.put("payload", parsed.get());
            response.put("source", content);
            response.put("pageUrl", WikiUrls.pagePath(title));
            response.put("editUrl", WikiUrls.pagePath(title) + "?action=edit");
            return response;
        }
        PageGeneratorStore.Document doc = document.get();
        if (!accessCheck.can(AccessAction.EDIT, title, viewer)) {
            return failure("forbidden", "你没有编辑这个页面的权限。");
        }
        Map<String, Object> payload = parsePayload(doc.payload());
        if (payload.isEmpty()) {
            return failure("corrupt", "页面生成器保存的数据损坏，请改用普通编辑。");
        }
        response.put("ok", true);
        response.put("kind", doc.kind());
        response.put("editing", true);
        response.put("imported", bool(payload.get("imported")));
        response.put("title", title);
        response.put("payload", payload);
        response.put("source", doc.generatedContent());
        response.put("pageUrl", WikiUrls.pagePath(title));
        response.put("editUrl", WikiUrls.pagePath(title) + "?action=edit");
        return response;
    }

    // ==================== 预览 / 保存 ====================

    public Map<String, Object> preview(Map<String, Object> payload, Viewer viewer) {
        if (!viewer.loggedIn()) {
            return failure("unauthorized", "请先登录后再预览。");
        }
        String kind = kindOf(payload);
        String title;
        try {
            title = titleOf(payload, kind);
        } catch (IllegalArgumentException exception) {
            return failure("badTitle", exception.getMessage());
        }
        String source;
        try {
            boolean patchMode = bool(payload.get("imported")) && pageStore.byTitle(title).isPresent();
            if (patchMode) {
                String current = pageStore.byTitle(title)
                        .flatMap(snapshot -> pageStore.content(snapshot.id())).orElse("");
                source = KIND_SUB.equals(kind)
                        ? PageGeneratorParser.patchSub(title, current, payload)
                        : PageGeneratorParser.patchMain(title, current, payload);
            } else {
                source = generate(payload, kind, title);
            }
        } catch (IllegalArgumentException exception) {
            return failure("badRequest", exception.getMessage());
        }
        String html = "";
        try {
            html = wikiText.toHtml(source, title);
        } catch (RuntimeException ignored) {
            // 模板不存在 / 渲染器异常时仍返回源码，让用户至少能看到生成结果。
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("title", title);
        response.put("source", source);
        response.put("html", html);
        return response;
    }

    public Map<String, Object> save(Map<String, Object> payload, Viewer viewer) {
        if (!viewer.loggedIn()) {
            return failure("unauthorized", "请先登录后再保存页面。");
        }
        Optional<String> block = accessCheck.blockNotice(viewer);
        if (block.isPresent()) {
            return failure("blocked", block.get());
        }
        String kind = kindOf(payload);
        String title;
        try {
            title = titleOf(payload, kind);
        } catch (IllegalArgumentException exception) {
            return failure("badTitle", exception.getMessage());
        }
        if (title.length() > 200) {
            return failure("badTitle", "页面标题过长（最多 200 个字符）。");
        }

        PageSnapshot existing = pageStore.byTitle(title).orElse(null);
        Optional<PageGeneratorStore.Document> document = store.find(title);
        boolean imported = bool(payload.get("imported")) && existing != null;
        boolean patchMode = imported;
        Long baseRevisionId = null;
        if (document.isPresent()) {
            if (existing == null) {
                return failure("pageMissing", "页面已经不存在，无法继续更新。");
            }
            String current = pageStore.content(existing.id()).orElse("");
            if (!current.equals(document.get().generatedContent())) {
                return failure("conflict",
                        "这个页面在生成后被手动修改过，已停止自动覆盖。请使用普通编辑，或联系管理员处理。");
            }
            baseRevisionId = pageStore.latestRevisionId(existing.id()).orElse(null);
            if (!accessCheck.can(AccessAction.EDIT, title, viewer)) {
                return failure("forbidden", "你没有编辑这个页面的权限。");
            }
        } else if (existing != null) {
            if (!imported) {
                return failure("notManaged",
                        "同名页面已经存在，页面生成器不会静默覆盖它。请在生成器里加载这个已有页面，或使用普通编辑。");
            }
            if (!accessCheck.can(AccessAction.EDIT, title, viewer)) {
                return failure("forbidden", "你没有编辑这个页面的权限。");
            }
            baseRevisionId = pageStore.latestRevisionId(existing.id()).orElse(null);
        } else if (!accessCheck.can(AccessAction.CREATE, title, viewer)) {
            return failure("forbidden", "你没有创建这个页面的权限。");
        }

        String content;
        try {
            if (patchMode) {
                String current = pageStore.content(existing.id()).orElse("");
                content = KIND_SUB.equals(kind)
                        ? PageGeneratorParser.patchSub(title, current, payload)
                        : PageGeneratorParser.patchMain(title, current, payload);
            } else {
                content = generate(payload, kind, title);
            }
        } catch (IllegalArgumentException exception) {
            return failure("badRequest", exception.getMessage());
        }
        String summary = document.isPresent() ? "页面生成器更新"
                : imported ? "页面生成器导入并更新" : "页面生成器创建";
        EditSubmitter.Result result = editSubmitter.submit(new EditSubmitter.Request(
                viewer.id(), title, content, summary, false, baseRevisionId, false, null,
                EditSubmitter.SOURCE_WEB, "", "", Map.of("generator", kind)));
        if (!result.saved()) {
            String error = result.error() == null ? "saveFailed" : result.error();
            String message = result.message();
            if (message == null || message.isBlank()) {
                message = switch (error) {
                    case "conflict" -> "页面在预览后又被别人修改，请刷新后重试。";
                    case "blocked" -> "保存被编辑钩子阻止。";
                    case "handled" -> "这次修改已进入审核流程。";
                    default -> "保存失败，请稍后重试。";
                };
            }
            return failure(error, message);
        }
        String savedTitle = result.pageId() == null ? title
                : pageStore.byId(result.pageId()).map(PageSnapshot::title).orElse(title);
        String payloadJson = json(payload);
        store.save(savedTitle, kind, payloadJson, content, result.revisionId(), viewer.name());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("title", savedTitle);
        response.put("pageUrl", WikiUrls.pagePath(savedTitle));
        response.put("revisionId", result.revisionId());
        response.put("message", document.isPresent() || imported ? "页面已更新。" : "页面已创建。");
        return response;
    }

    // ==================== 表单与生成 ====================

    private Map<String, Object> blankPayload(String kind) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        if (KIND_SUB.equals(kind)) {
            payload.put("parent", "");
            payload.put("subName", "");
            payload.put("logo", "");
            payload.put("gallery", new ArrayList<String>());
            payload.put("days", new ArrayList<Map<String, Object>>());
            payload.put("customContent", "");
            payload.put("needsWork", false);
        } else {
            payload.put("title", "");
            payload.put("displayName", "");
            payload.put("shortName", "");
            payload.put("type", "");
            payload.put("region", "");
            payload.put("host", "");
            payload.put("groupNo", "");
            payload.put("bilibili", "");
            payload.put("weibo", "");
            payload.put("website", "");
            payload.put("market", "");
            payload.put("logo", "");
            payload.put("gallery", new ArrayList<String>());
            payload.put("editions", new ArrayList<Map<String, Object>>());
            payload.put("customContent", "");
            payload.put("needsWork", false);
        }
        return payload;
    }

    private String generate(Map<String, Object> payload, String kind, String title) {
        return KIND_SUB.equals(kind) ? generateSub(payload, title) : generateMain(payload, title);
    }

    static String mainInfoBox(Map<String, Object> payload) {
        String displayName = first(text(payload.get("displayName")), text(payload.get("title")));
        StringBuilder out = new StringBuilder("{{信息集合框\n");
        param(out, "titlename", displayName);
        param(out, "图片", text(payload.get("logo")));
        param(out, "活动名称", displayName);
        param(out, "活动别称", text(payload.get("shortName")));
        param(out, "活动群号", text(payload.get("groupNo")));
        param(out, "哔哩哔哩", text(payload.get("bilibili")));
        param(out, "官方网站", text(payload.get("website")));
        param(out, "地区", text(payload.get("region")));
        out.append("}}\n\n");
        return out.toString();
    }

    static String mainActivityInfo(Map<String, Object> payload) {
        String displayName = first(text(payload.get("displayName")), text(payload.get("title")));
        List<Map<String, Object>> editions = objects(payload.get("editions"));
        StringBuilder out = new StringBuilder("{{活动信息\n");
        param(out, "活动名称", displayName);
        param(out, "活动简称", text(payload.get("shortName")));
        param(out, "主办方", text(payload.get("host")));
        param(out, "地区", text(payload.get("region")));
        param(out, "类型", text(payload.get("type")));
        if (!editions.isEmpty()) {
            param(out, "举办次数", String.valueOf(editions.size()));
        }
        param(out, "QQ群", text(payload.get("groupNo")));
        param(out, "官方哔哩哔哩", text(payload.get("bilibili")));
        param(out, "官方微博", text(payload.get("weibo")));
        param(out, "东方市场", text(payload.get("market")));
        param(out, "官方网站", text(payload.get("website")));
        out.append("}}\n\n");
        return out.toString();
    }

    static String mainSchedule(Map<String, Object> payload, String title) {
        List<Map<String, Object>> editions = objects(payload.get("editions"));
        StringBuilder out = new StringBuilder("{{历届信息\n|\n");
        for (int index = 0; index < editions.size(); index++) {
            Map<String, Object> edition = editions.get(index);
            String name = text(edition.get("name"));
            if (name.isEmpty() && text(edition.get("date")).isEmpty()
                    && text(edition.get("place")).isEmpty()) {
                continue;
            }
            if (name.isEmpty()) {
                name = "第" + (index + 1) + "届";
            }
            // 用户有时会把父标题一起填进来；避免生成 [[广西THO/广西THO/xxx]]。
            String parentPrefix = title + "/";
            String link = name.startsWith(parentPrefix) ? name : parentPrefix + name;
            String display = name.startsWith(parentPrefix)
                    ? name.substring(parentPrefix.length()) : name;
            out.append("{{单届信息\n");
            out.append("|[[").append(link).append('|').append(display).append("]]\n");
            out.append("|").append(text(edition.get("date"))).append('\n');
            out.append("|").append(text(edition.get("place"))).append('\n');
            out.append("|").append(text(edition.get("booths"))).append('\n');
            out.append("}}\n\n");
        }
        out.append("}}\n\n");
        return out.toString();
    }

    static String mainGallery(Map<String, Object> payload) {
        StringBuilder out = new StringBuilder("{{历届宣传画廊|\n");
        appendGallery(out, strings(payload.get("gallery")));
        out.append("}}\n\n");
        return out.toString();
    }

    static String subLogoBlock(Map<String, Object> payload) {
        String logo = text(payload.get("logo"));
        return logo.isEmpty() ? "" : "{{具体展会/LOGO|" + logo + "}}\n";
    }

    static String subDayBlocks(Map<String, Object> payload) {
        List<Map<String, Object>> days = objects(payload.get("days"));
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < days.size(); index++) {
            Map<String, Object> day = days.get(index);
            String dayTitle = first(text(day.get("title")), "Day " + (index + 1));
            out.append("{{具体展会/简介/日程\n");
            param(out, "标题", dayTitle);
            param(out, "类型", text(day.get("type")));
            param(out, "日期", text(day.get("date")));
            param(out, "地址", text(day.get("address")));
            param(out, "摊位数", text(day.get("booths")));
            out.append("}}\n");
        }
        return out.toString();
    }

    static String subGalleryBlock(Map<String, Object> payload) {
        List<Map<String, Object>> days = objects(payload.get("days"));
        StringBuilder out = new StringBuilder();
        if (!days.isEmpty()) {
            out.append("=== Day 1 - Day ").append(days.size()).append(" ===\n");
        }
        appendGallery(out, strings(payload.get("gallery")));
        return out.toString();
    }

    private String generateMain(Map<String, Object> payload, String title) {
        StringBuilder out = new StringBuilder();
        out.append("{{展会}}\n");
        if (bool(payload.get("needsWork"))) {
            out.append("{{需要补充}}\n");
        }
        out.append('\n');
        out.append(mainInfoBox(payload));
        out.append(mainActivityInfo(payload));
        out.append(mainSchedule(payload, title));
        out.append(mainGallery(payload));
        String custom = block(payload.get("customContent"));
        if (!custom.isEmpty()) {
            out.append(custom).append("\n\n");
        }
        out.append("{{页面注释}}\n");
        String regionTemplate = regionTemplate(text(payload.get("region")));
        if (!regionTemplate.isEmpty()) {
            out.append(regionTemplate).append('\n');
        }
        return out.toString();
    }

    private String generateSub(Map<String, Object> payload, String title) {
        StringBuilder out = new StringBuilder();
        out.append("{{具体展会}}\n");
        if (bool(payload.get("needsWork"))) {
            out.append("{{需要补充}}\n");
        }
        out.append('\n');
        out.append(subLogoBlock(payload));
        out.append("{{具体展会/简介}}\n");
        out.append(subDayBlocks(payload));
        out.append('\n');
        out.append("== 返图 ==\n").append(subGalleryBlock(payload)).append('\n');
        String custom = block(payload.get("customContent"));
        if (!custom.isEmpty()) {
            out.append(custom).append("\n\n");
        }
        out.append("{{页面注释}}\n");
        return out.toString();
    }

    private static void appendGallery(StringBuilder out, List<String> gallery) {
        out.append("<gallery mode=\"packed\" heights=\"300px\">\n");
        for (String name : gallery) {
            if (!name.isBlank()) {
                out.append(name).append('\n');
            }
        }
        out.append("</gallery>\n");
    }

    private static void param(StringBuilder out, String key, String value) {
        if (value != null && !value.isBlank()) {
            out.append('|').append(key).append('=').append(value).append('\n');
        }
    }

    private static String regionTemplate(String region) {
        if (region == null || region.isBlank()) {
            return "";
        }
        String first = region.trim().split("\\s+")[0];
        Matcher matcher = PROVINCE_PREFIX.matcher(first);
        if (matcher.find()) {
            first = matcher.group(1);
        }
        String base = PROVINCE_SUFFIX.matcher(first).replaceFirst("").trim();
        if (base.isEmpty() || base.endsWith("活动")) {
            return base.isEmpty() ? "" : "{{" + base + "}}";
        }
        return "{{" + base + "活动}}";
    }

    // ==================== 取值与校验 ====================

    private String kindOf(Map<String, Object> payload) {
        String kind = text(payload.get("kind"));
        return KIND_SUB.equals(kind) ? KIND_SUB : KIND_MAIN;
    }

    private String titleOf(Map<String, Object> payload, String kind) {
        if (KIND_SUB.equals(kind)) {
            String parent = cleanTitle(text(payload.get("parent")));
            String subName = cleanTitle(text(payload.get("subName")));
            if (parent.isEmpty()) {
                throw new IllegalArgumentException("请填写父活动页面标题。");
            }
            if (subName.isEmpty()) {
                throw new IllegalArgumentException("请填写子页面标题。");
            }
            return cleanTitle(parent.replaceAll("/+$", "") + "/" + subName.replaceAll("^/+", ""));
        }
        String title = cleanTitle(text(payload.get("title")));
        if (title.isEmpty()) {
            throw new IllegalArgumentException("请填写页面标题。");
        }
        return title;
    }

    private static String cleanTitle(String value) {
        String title = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            return "";
        }
        if (INVALID_TITLE.matcher(title).find()) {
            throw new IllegalArgumentException("页面标题不能包含 # < > [ ] { } | 或换行。");
        }
        return title;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String block(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String first(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((key, val) -> row.put(String.valueOf(key), val));
                    result.add(row);
                }
            }
        }
        return result;
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = text(item);
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = objectMapper.readValue(json, Map.class);
            return value == null ? Map.of() : value;
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    private static Map<String, Object> failure(String error, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("error", error);
        response.put("message", message);
        return response;
    }
}

