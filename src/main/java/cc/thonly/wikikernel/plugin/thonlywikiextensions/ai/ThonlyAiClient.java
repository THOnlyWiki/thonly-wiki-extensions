package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

import cc.thonly.wikikernel.common.ai.AiTasks;
import cc.thonly.wikikernel.common.service.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class ThonlyAiClient {
    private final AiTasks tasks;
    private final CurrentUser current;

    public ThonlyAiClient(AiTasks tasks, CurrentUser current) {
        this.tasks = tasks;
        this.current = current;
    }

    public String complete(String endpoint, boolean allowPrivate, String apiKey, String model,
                           String systemPrompt, String userPrompt) {
        try {
            return tasks.complete("activity", current.viewer(), systemPrompt, userPrompt);
        } catch (RuntimeException exception) {
            throw new ThonlyAiException("AI 整理失败，请检查 AI 管理中的活动整理场景。", exception);
        }
    }
}
