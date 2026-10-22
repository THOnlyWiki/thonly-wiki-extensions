package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import cc.thonly.wikikernel.common.access.AccessAction;
import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.AccessCheck;
import cc.thonly.wikikernel.common.service.CurrentUser;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * AI 整理接口（全部只允许管理员 / 行政员；默认使用页面的 PROTECT 权限）。
 *
 * <p>注意：这里没有“传入任意 URL / API Key / model 就由服务器代理请求”的接口。
 * 前端只能提交宣传文案、年份和已生成的候选操作；endpoint、model、key 全部来自管理员配置。</p>
 */
@RestController
@RequestMapping("/api/thonly-activity/ai")
public class ThonlyAiApiController {

    private static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.PAGE_TITLE;

    private final ThonlyAiConfig config;
    private final ThonlyAiService service;
    private final CurrentUser currentUser;
    private final AccessCheck accessCheck;

    public ThonlyAiApiController(ThonlyAiConfig config, ThonlyAiService service,
                                 CurrentUser currentUser, AccessCheck accessCheck) {
        this.config = config;
        this.service = service;
        this.currentUser = currentUser;
        this.accessCheck = accessCheck;
    }

    @GetMapping("/config")
    public ResponseEntity<Object> config() {
        if (!isAdmin()) {
            return forbidden();
        }
        return ResponseEntity.ok(config.settings());
    }

    @PostMapping("/config")
    public ResponseEntity<Object> saveConfig(@RequestParam Map<String, String> params) {
        if (!isAdmin()) {
            return forbidden();
        }
        try {
            config.save(checked(params.get("enabled")),
                    params.get("buttonLabel"),
                    params.get("baseUrl"),
                    params.get("model"),
                    params.get("apiKey"),
                    checked(params.get("clearKey")),
                    checked(params.get("allowPrivate")));
            return ResponseEntity.ok(config.settings());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/organize")
    public ResponseEntity<Object> organize(@RequestBody JsonNode body) {
        if (!isAdmin()) {
            return forbidden();
        }
        int year = body == null ? 0 : body.path("year").asInt(0);
        if (year < 2000 || year > 2100) {
            return ResponseEntity.badRequest().body(Map.of("message", "年份不合法。"));
        }
        String text = body == null ? "" : body.path("text").asText("");
        try {
            return ResponseEntity.ok(service.organize(year, text));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (ThonlyAiException exception) {
            return ResponseEntity.status(502).body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping("/apply")
    public ResponseEntity<Object> apply(@RequestBody JsonNode body) {
        if (!isAdmin()) {
            return forbidden();
        }
        int year = body == null ? 0 : body.path("year").asInt(0);
        if (year < 2000 || year > 2100) {
            return ResponseEntity.badRequest().body(Map.of("message", "年份不合法。"));
        }
        JsonNode operations = body == null ? null : body.path("operations");
        try {
            return ResponseEntity.ok(service.apply(currentUser.viewer(), year, operations));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    private boolean isAdmin() {
        Viewer viewer = currentUser.viewer();
        return viewer.loggedIn()
                && accessCheck.can(AccessAction.PROTECT, PAGE_TITLE, viewer)
                && accessCheck.blockNotice(viewer).isEmpty();
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "只有管理员可以使用 AI 整理功能。"));
    }

    private static boolean checked(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value);
    }
}
