package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import cc.thonly.wikikernel.common.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 页面生成器 API：加载表单、预览、保存。
 * <p>
 * 所有接口都要求登录；保存还会按目标页面校验 {@code edit/create} 权限并经过 core 的
 * {@code EditSubmitter}（编辑钩子、滥用过滤器、审核队列都会生效）。
 */
@RestController
@RequestMapping("/api/page-generator")
public class PageGeneratorApiController {

    private final PageGeneratorService service;
    private final CurrentUser currentUser;

    public PageGeneratorApiController(PageGeneratorService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/load")
    public Map<String, Object> load(@RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "kind", required = false) String kind) {
        return service.load(title, kind, currentUser.viewer());
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody(required = false) Map<String, Object> payload) {
        return service.preview(payload == null ? Map.of() : payload, currentUser.viewer());
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody(required = false) Map<String, Object> payload) {
        return service.save(payload == null ? Map.of() : payload, currentUser.viewer());
    }
}
