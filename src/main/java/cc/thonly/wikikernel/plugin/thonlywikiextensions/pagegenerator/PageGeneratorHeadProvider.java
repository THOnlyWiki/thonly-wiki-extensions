package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import cc.thonly.wikikernel.common.extension.HeadContext;
import cc.thonly.wikikernel.common.extension.HtmlHeadProvider;

import java.util.List;

/**
 * 页面生成器表单的 CSS / JS：只在两个帮助页加载。
 * <p>
 * 生成器普通页面只需要 page-generator.css 里的顶部编辑条样式，但编辑条是服务器直接输出的，
 * 没有复杂交互；这里仍只对帮助页注入完整资源。
 */
public class PageGeneratorHeadProvider implements HtmlHeadProvider {

    @Override
    public int order() {
        return 640;
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
        java.util.List<String> items = new java.util.ArrayList<>();
        // 生成器创建过的普通页面需要顶部「编辑活动资料」条的样式；表单脚本只在两个帮助页加载。
        items.add("<link rel=\"stylesheet\" href=\"/plugins/thonly-wiki-extensions/page-generator.css\">");
        if (PageGeneratorService.isMainHelp(title)
                || PageGeneratorService.isSubHelp(title)) {
            items.add("<script defer src=\"/plugins/thonly-wiki-extensions/page-generator.js\"></script>");
        }
        return items;
    }
}
