package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 OpenAI 兼容 Chat Completions 客户端。
 *
 * <p>安全边界：</p>
 * <ul>
 *     <li>只向服务端配置的固定 endpoint 发请求，请求体里的地址 / 模型 / 密钥都不能由前端覆盖；</li>
 *     <li>不转发浏览器的 Cookie、Authorization、代理或其它请求头；</li>
 *     <li>不跟随 HTTP 重定向，避免被上游引向内网；</li>
 *     <li>限制响应大小与超时，错误信息截断，避免把大段上游内容带进页面。</li>
 * </ul>
 */
@Service
public class ThonlyAiClient {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(100);

    private final ObjectMapper mapper;
    private final HttpClient client;

    public ThonlyAiClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 调用 OpenAI 兼容接口，返回助手文本。
     */
    public String complete(String endpoint, boolean allowPrivate, String apiKey, String model,
                           String systemPrompt, String userPrompt) {
        String url = endpoint == null ? "" : endpoint.trim();
        ThonlyAiEndpointPolicy.validate(url, allowPrivate);
        if (model == null || model.isBlank()) {
            throw new ThonlyAiException("AI 模型名称未配置。");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.trim());
        body.put("temperature", 0.2);
        body.put("max_tokens", 4096);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);

        String payload;
        try {
            payload = mapper.writeValueAsString(body);
        } catch (RuntimeException exception) {
            throw new ThonlyAiException("AI 请求序列化失败。", exception);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("User-Agent", "WikiKernel-ThonlyAI/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpResponse<InputStream> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException exception) {
            throw new ThonlyAiException("无法连接 AI 服务：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ThonlyAiException("AI 请求被中断。");
        }

        String responseBody;
        try (InputStream stream = response.body()) {
            responseBody = readLimited(stream, MAX_RESPONSE_BYTES);
        } catch (IOException exception) {
            throw new ThonlyAiException("读取 AI 响应失败。", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ThonlyAiException("AI 服务返回 HTTP " + response.statusCode() + "：" + brief(responseBody));
        }

        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (RuntimeException exception) {
            throw new ThonlyAiException("AI 服务返回的不是合法 JSON：" + brief(responseBody));
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText("");
            throw new ThonlyAiException("AI 服务返回错误：" + (message.isBlank() ? brief(responseBody) : message));
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ThonlyAiException("AI 服务未返回 choices：" + brief(responseBody));
        }
        JsonNode content = choices.path(0).path("message").path("content");
        String text = textOf(content);
        if (text.isBlank()) {
            text = choices.path(0).path("text").asText("");
        }
        if (text.isBlank()) {
            throw new ThonlyAiException("AI 服务返回了空内容。");
        }
        return text;
    }

    private static String textOf(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual() || content.isNumber() || content.isBoolean()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    text.append(part.asText(""));
                } else {
                    String value = part.path("text").asText("");
                    if (!value.isEmpty()) {
                        text.append(value);
                    }
                }
            }
            return text.toString();
        }
        return content.asText("");
    }

    private static String readLimited(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IOException("AI 响应超过大小上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String brief(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "…";
    }
}
