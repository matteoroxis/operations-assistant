package it.matteoroxis.opsassistant.api;

import it.matteoroxis.opsassistant.api.dto.ChatRequest;
import it.matteoroxis.opsassistant.api.dto.ChatResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Article 1 — stateless RAG chat endpoint.
 * Each request is independent; no conversation history yet.
 */
@RestController
@RequestMapping("/api/ops")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // Only alphanumeric chars, hyphens, underscores and dots
    private static final String SAFE_VALUE_REGEX = "^[a-zA-Z0-9_\\-.]*$";

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * POST /api/ops/chat
     * Answers an operational question using context retrieved from the knowledge base.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        validateFilterValue(request.system(), "system");
        validateFilterValue(request.environment(), "environment");

        String filterExpression = buildFilterExpression(request.system(), request.environment());

        String answer = chatClient.prompt()
                .user(request.message())
                .advisors(spec -> {
                    if (filterExpression != null) {
                        spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression);
                    }
                })
                .call()
                .content();

        log.debug("Chat completed [filter='{}', answerLength={}]", filterExpression,
                answer != null ? answer.length() : 0);

        return ResponseEntity.ok(new ChatResponse(answer));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a simple EL-style filter expression string for the QuestionAnswerAdvisor.
     * Returns null when no filter is requested.
     */
    private String buildFilterExpression(String system, String environment) {
        StringBuilder sb = new StringBuilder();
        if (isPresent(system)) {
            sb.append("system == '").append(system).append("'");
        }
        if (isPresent(environment)) {
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append("environment == '").append(environment).append("'");
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Guards against injection in filter expression values.
     * Accepts only alphanumeric characters, hyphens, underscores and dots.
     */
    private void validateFilterValue(String value, String fieldName) {
        if (isPresent(value) && !value.matches(SAFE_VALUE_REGEX)) {
            throw new IllegalArgumentException(
                    "Invalid value for '" + fieldName + "': only alphanumeric, hyphens, underscores and dots are allowed.");
        }
    }
}
