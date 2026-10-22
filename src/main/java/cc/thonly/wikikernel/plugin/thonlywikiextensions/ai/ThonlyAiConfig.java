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

    public ThonlyAiConfig(SiteConfig siteConfig) {
        this.siteConfig = siteConfig;
    }

    public Settings settings() {
        String label = siteConfig.get(LABEL_KEY, DEFAULT_LABEL);
        if (label == null || label.isBlank()) {
            label = DEFAULT_LABEL;
        }
        String key = siteConfig.get(API_KEY_KEY, "");
        return new Settings(
                siteConfig.getBoolean(ENABLED_KEY, false),
                label.length() > 60 ? label.substring(0, 60) : label,
                siteConfig.get(BASE_URL_KEY, ""),
                siteConfig.get(MODEL_KEY, DEFAULT_MODEL),
                key != null && !key.isBlank(),
                siteConfig.getBoolean(ALLOW_PRIVATE_KEY, false));
    }

    public String apiKey() {
        return siteConfig.get(API_KEY_KEY, "");
    }

    public String endpoint() {
        return ThonlyAiEndpointPolicy.chatCompletionsUrl(siteConfig.get(BASE_URL_KEY, ""));
    }

    /**
     * 保存配置。{@code apiKey} 为 {@code null} 或空白表示不修改已保存的密钥；
     * {@code clearKey} 为 true 时清空。
     */
    public void save(boolean enabled, String label, String baseUrl, String model,
                     String apiKey, boolean clearKey, boolean allowPrivate) {
        String labelValue = clean(label);
        if (labelValue.isEmpty()) {
            labelValue = DEFAULT_LABEL;
        }
        if (labelValue.length() > 60) {
            labelValue = labelValue.substring(0, 60);
        }
        String baseValue = clean(baseUrl);
        String modelValue = clean(model);
        if (enabled && baseValue.isEmpty()) {
            throw new IllegalArgumentException("启用 AI 前请填写 OpenAI 兼容接口地址。");
        }
        if (enabled && modelValue.isEmpty()) {
            throw new IllegalArgumentException("启用 AI 前请填写模型名称。");
        }
        if (!baseValue.isEmpty()) {
            ThonlyAiEndpointPolicy.validate(baseValue, allowPrivate);
        }
        siteConfig.set(ENABLED_KEY, Boolean.toString(enabled));
        siteConfig.set(LABEL_KEY, labelValue);
        siteConfig.set(BASE_URL_KEY, baseValue);
        siteConfig.set(MODEL_KEY, modelValue);
        siteConfig.set(ALLOW_PRIVATE_KEY, Boolean.toString(allowPrivate));
        if (clearKey) {
            siteConfig.set(API_KEY_KEY, "");
        } else if (apiKey != null && !apiKey.isBlank()) {
            siteConfig.set(API_KEY_KEY, apiKey.trim());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** 给前端 / 页面渲染用的安全视图：不包含 API Key。 */
    public record Settings(boolean enabled, String buttonLabel, String baseUrl, String model,
                           boolean keyConfigured, boolean allowPrivate) {
    }
}
