package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import cc.thonly.wikikernel.common.service.SiteConfig;
import org.springframework.stereotype.Service;

/**
 * 「线下活动速查表」的 AI 整理配置（站点级，只有管理员能改）。
 *
 * <p>所有值都存在 core 的站点设置里，与安装向导 / 站点设置页同一存储；
 * API Key 只由服务端在出站请求里使用，GET 配置接口永远不会回显它。</p>
 */
@Service
public class ThonlyAiConfig {

    public static final String ENABLED_KEY = "thonly-ai.enabled";
    public static final String LABEL_KEY = "thonly-ai.button-label";
    public static final String BASE_URL_KEY = "thonly-ai.base-url";
    public static final String MODEL_KEY = "thonly-ai.model";
    public static final String API_KEY_KEY = "thonly-ai.api-key";
    public static final String ALLOW_PRIVATE_KEY = "thonly-ai.allow-private";

    public static final String DEFAULT_LABEL = "AI 整理导入";
    public static final String DEFAULT_MODEL = "";

    private final SiteConfig siteConfig;
    private final cc.thonly.wikikernel.common.ai.AiConfig aiConfig;
    private final cc.thonly.wikikernel.common.ai.AiTasks tasks;

    public ThonlyAiConfig(SiteConfig siteConfig, cc.thonly.wikikernel.common.ai.AiConfig aiConfig, cc.thonly.wikikernel.common.ai.AiTasks tasks) {
        this.siteConfig = siteConfig;
        this.aiConfig = aiConfig;
        this.tasks = tasks;
    }

    public Settings settings() {
        String label = siteConfig.get(LABEL_KEY, DEFAULT_LABEL);
        if (label == null || label.isBlank()) {
            label = DEFAULT_LABEL;
        }
        boolean ready = tasks.configured("activity");
        return new Settings(
                ready,
                label.length() > 60 ? label.substring(0, 60) : label,
                ready ? "core-ai:activity" : "",
                ready ? tasks.model("activity") : "",
                ready,
                false);
    }

    public String apiKey() { return ""; }
    public String endpoint() { return "core-ai:activity"; }

    /** Compatibility for older activity clients. New clients configure AI in core. */
    public void save(boolean enabled, String label, String baseUrl, String model,
                     String apiKey, boolean clearKey, boolean allowPrivate) {
        String id = "thonly-legacy";
        ThonlyAiEndpointPolicy.validate(baseUrl, allowPrivate);
        aiConfig.saveSite(id, apiKey == null || apiKey.isBlank() ? null : apiKey,
                baseUrl.replaceAll("/chat/completions/?$", ""), model, "", clearKey);
        siteConfig.set("ai.provider." + id + ".type", "openai");
        siteConfig.set("ai.provider." + id + ".private", String.valueOf(allowPrivate));
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(siteConfig.get("ai.profiles", "").split(",")));
        ids.remove(""); ids.add(id);
        siteConfig.set("ai.profiles", String.join(",", ids));
        siteConfig.set("ai.scene.activity.provider", id);
        siteConfig.set("ai.scene.activity.enabled", String.valueOf(enabled));
        siteConfig.set(LABEL_KEY, clean(label).isBlank() ? DEFAULT_LABEL : clean(label));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** 给前端 / 页面渲染用的安全视图：不包含 API Key。 */
    public record Settings(boolean enabled, String buttonLabel, String baseUrl, String model,
                           boolean keyConfigured, boolean allowPrivate) {
    }
}
