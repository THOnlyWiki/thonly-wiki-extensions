package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.PageDisplayContext;
import cc.thonly.wikikernel.common.extension.PageDisplayHook;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.common.web.WikiUrls;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 两个页面生成器帮助页的正文接管：
 * <ul>
 *     <li>{@code 快速创建页面}：活动系列主页；</li>
 *     <li>{@code 快速创建子页面}：某一届活动子页面。</li>
 * </ul>
 * 页面标题保持线上 URL 不变，但不再渲染原来的 iframe/Firefox 兼容占位，而是原生 Codex 表单。
 * 另外给生成器创建过的普通页面插入「编辑活动资料」入口。
 */
@Component
public class PageGeneratorPageHook implements PageDisplayHook {

    private final PageGeneratorStore store;
    private final AccessCheck accessCheck;

    public PageGeneratorPageHook(PageGeneratorStore store, AccessCheck accessCheck) {
        this.store = store;
        this.accessCheck = accessCheck;
    }

    @Override
    public int order() {
        return 65;
    }

    @Override
    public String renderBody(PageDisplayContext context) {
        return form(context);
    }

    @Override
    public String renderMissingPage(PageDisplayContext context) {
        return form(context);
    }

    @Override
    public String beforeContent(PageDisplayContext context) {
        if (context == null || context.oldRevision() || !context.viewer().loggedIn()) {
            return null;
        }
        Optional<PageGeneratorStore.Document> document = store.find(context.title());
        if (document.isEmpty()) {
            return null;
        }
        String title = context.title();
        if (!accessCheck.can(AccessAction.EDIT, title, context.viewer())) {
            return null;
        }
        String help = PageGeneratorService.KIND_SUB.equals(document.get().kind())
                ? PageGeneratorService.HELP_SUB : PageGeneratorService.HELP_MAIN;
        String target = WikiUrls.pagePath(help) + "?title="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
        return "<div class=\"page-generator-edit-banner\">"
                + "<span>这个页面由页面生成器创建</span>"
                + "<a class=\"wiki-button wiki-button-primary\" href=\"" + escape(target)
                + "\">编辑活动资料</a>"
                + "<a class=\"wiki-button\" href=\"" + escape(WikiUrls.pagePath(title))
                + "?action=edit\">编辑源代码</a>"
                + "</div>";
    }

    private String form(PageDisplayContext context) {
        String title = context == null ? "" : context.title();
        String kind;
        if (PageGeneratorService.isSubHelp(title)) {
            kind = PageGeneratorService.KIND_SUB;
        } else if (PageGeneratorService.isMainHelp(title)) {
            kind = PageGeneratorService.KIND_MAIN;
        } else {
            return null;
        }
        if (context.viewer() == null || !context.viewer().loggedIn()) {
            String login = WikiUrls.pagePath("特殊:登录") + "?returnto="
                    + URLEncoder.encode(WikiUrls.pagePath(title), StandardCharsets.UTF_8);
            return "<div class=\"page-generator-login\">"
                    + "<h2>请先登录</h2>"
                    + "<p>页面生成器需要登录后使用；登录完成后会回到这里。</p>"
                    + "<a class=\"wiki-button wiki-button-primary\" href=\"" + escape(login)
                    + "\">登录</a>"
                    + "</div>";
        }
        String initialTitle = context.query() == null ? ""
                : context.query().getOrDefault("title", "");
        return "<div class=\"page-generator-app\" data-kind=\"" + kind + "\""
                + " data-initial-title=\"" + escape(initialTitle) + "\">"
                + "<div class=\"page-generator-loading\">正在加载页面生成器…</div>"
                + "</div>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

