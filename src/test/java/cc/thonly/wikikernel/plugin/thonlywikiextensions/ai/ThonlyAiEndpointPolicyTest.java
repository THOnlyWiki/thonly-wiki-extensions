package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThonlyAiEndpointPolicyTest {

    @Test
    void publicHttpsIsAllowed() {
        assertDoesNotThrow(() -> ThonlyAiEndpointPolicy.validate("https://1.1.1.1/v1", false));
    }

    @Test
    void privateAndPlainHttpRequireExplicitOptIn() {
        assertThrows(IllegalArgumentException.class,
                () -> ThonlyAiEndpointPolicy.validate("http://1.1.1.1/v1", false));
        assertThrows(IllegalArgumentException.class,
                () -> ThonlyAiEndpointPolicy.validate("http://127.0.0.1:11434/v1", false));
        assertDoesNotThrow(() -> ThonlyAiEndpointPolicy.validate("http://127.0.0.1:11434/v1", true));
    }

    @Test
    void metadataIsNeverAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> ThonlyAiEndpointPolicy.validate("http://169.254.169.254/latest/meta-data", true));
    }

    @Test
    void buildsChatCompletionsUrl() {
        assertEquals("https://api.example.com/v1/chat/completions",
                ThonlyAiEndpointPolicy.chatCompletionsUrl("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/v1/chat/completions",
                ThonlyAiEndpointPolicy.chatCompletionsUrl("https://api.example.com/v1/chat/completions"));
    }
}
