package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThonlyAiClientTest {

    @Test
    void callsConfiguredChatCompletionsEndpointAndReadsContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(read(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/chat/completions";
            ThonlyAiClient client = new ThonlyAiClient(new ObjectMapper());
            String content = client.complete(endpoint, true, "test-key", "test-model", "system", "user");
            assertEquals("OK", content);
            assertTrue(requestBody.get().contains("\"model\":\"test-model\""));
            assertTrue(requestBody.get().contains("\"system\""));
            assertEquals("Bearer test-key", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
}
