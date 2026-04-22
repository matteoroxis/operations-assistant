package it.matteoroxis.opsassistant.api;

import it.matteoroxis.opsassistant.advisor.LongTermMemoryAdvisor;
import it.matteoroxis.opsassistant.api.dto.ChatRequest;
import it.matteoroxis.opsassistant.api.dto.ChatResponse;
import it.matteoroxis.opsassistant.domain.Checkpoint;
import it.matteoroxis.opsassistant.domain.CheckpointStatus;
import it.matteoroxis.opsassistant.service.CheckpointService;
import it.matteoroxis.opsassistant.util.ConversationContextHolder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Article 3 — stateful chat with checkpoint/pause/resume support.
 *
 * <p>Every conversation is now backed by a {@link Checkpoint} document so that
 * a multi-step investigation workflow can be suspended and resumed exactly where
 * it left off, even after a browser close or network drop.
 *
 * <p>New endpoints:
 * <ul>
 *   <li>{@code GET  /api/ops/chat/{conversationId}/state}    — returns the current checkpoint</li>
 *   <li>{@code POST /api/ops/chat/{conversationId}/resume}   — re-hydrates state into prompt and continues</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ops")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // Only alphanumeric chars, hyphens, underscores and dots
    private static final String SAFE_VALUE_REGEX = "^[a-zA-Z0-9_\\-.]*$";

    private final ChatClient chatClient;
    private final CheckpointService checkpointService;

    public ChatController(ChatClient chatClient, CheckpointService checkpointService) {
        this.chatClient = chatClient;
        this.checkpointService = checkpointService;
    }

    /**
     * POST /api/ops/chat
     * Answers an operational question, maintaining conversation memory and checkpoint state.
     * Creates a new checkpoint on the first message of a conversation; subsequent messages
     * update the existing checkpoint status.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        validateFilterValue(request.system(), "system");
        validateFilterValue(request.environment(), "environment");
        validateFilterValue(request.userId(), "userId");

        String conversationId = resolveConversationId(request.conversationId());
        String userId = request.userId() != null ? request.userId() : "anonymous";
        String filterExpression = buildFilterExpression(request.system(), request.environment());

        // Create or mark checkpoint as running
        if (checkpointService.loadLatest(conversationId).isEmpty()) {
            checkpointService.create(conversationId, UUID.randomUUID().toString(), "incident-investigation");
        } else {
            checkpointService.updateStep(conversationId, "PROCESSING", CheckpointStatus.RUNNING, null);
        }

        MDC.put("conversationId", conversationId);
        MDC.put("userId", userId);
        ConversationContextHolder.set(conversationId);
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(request.message())
                    .advisors(spec -> {
                        spec.param(ChatMemory.CONVERSATION_ID, conversationId);
                        spec.param(LongTermMemoryAdvisor.USER_ID, userId);
                        if (filterExpression != null) {
                            spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression);
                        }
                    })
                    .call()
                    .content();
        } finally {
            ConversationContextHolder.clear();
            MDC.remove("conversationId");
            MDC.remove("userId");
        }

        // Persist the AI's response summary in the checkpoint so resume can reference it
        checkpointService.updateStep(conversationId, "AWAITING_APPROVAL",
                CheckpointStatus.WAITING_APPROVAL,
                Map.of(
                        "lastUserMessage", request.message(),
                        "lastAnswer", answer != null ? answer : "",
                        "updatedAt", Instant.now().toString()
                ));

        log.debug("Chat completed [conversationId={}, userId={}, filter='{}', answerLength={}]",
                conversationId, userId, filterExpression, answer != null ? answer.length() : 0);

        return ResponseEntity.ok(new ChatResponse(conversationId, answer));
    }

    /**
     * GET /api/ops/chat/{conversationId}/state
     * Returns the latest {@link Checkpoint} for the given conversation,
     * or {@code 404 Not Found} if no checkpoint exists.
     */
    @GetMapping("/chat/{conversationId}/state")
    public ResponseEntity<Checkpoint> getCheckpointState(@PathVariable String conversationId) {
        return checkpointService.loadLatest(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/ops/chat/{conversationId}/resume
     * Re-hydrates a paused workflow from its last checkpoint and continues the conversation.
     *
     * <p>The stored state (last user message, last AI answer, pending actions) is injected
     * into the prompt so the model can accurately summarise where the investigation left off
     * and propose the next steps without requiring the operator to repeat context.
     *
     * @param conversationId the conversation to resume
     * @param userId         the operator resuming the session (default: {@code anonymous})
     */
    @PostMapping("/chat/{conversationId}/resume")
    public ResponseEntity<ChatResponse> resumeWorkflow(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "anonymous") String userId) {

        Checkpoint checkpoint = checkpointService.loadLatest(conversationId)
                .orElse(null);
        if (checkpoint == null) {
            return ResponseEntity.notFound().build();
        }

        checkpointService.updateStep(conversationId, checkpoint.getCurrentStep(),
                CheckpointStatus.RUNNING, null);

        String resumePrompt = buildResumePrompt(checkpoint);

        MDC.put("conversationId", conversationId);
        MDC.put("userId", userId);
        ConversationContextHolder.set(conversationId);
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(resumePrompt)
                    .advisors(spec -> {
                        spec.param(ChatMemory.CONVERSATION_ID, conversationId);
                        spec.param(LongTermMemoryAdvisor.USER_ID, userId);
                    })
                    .call()
                    .content();
        } finally {
            ConversationContextHolder.clear();
            MDC.remove("conversationId");
            MDC.remove("userId");
        }

        checkpointService.updateStep(conversationId, "AWAITING_APPROVAL",
                CheckpointStatus.WAITING_APPROVAL,
                Map.of(
                        "lastAnswer", answer != null ? answer : "",
                        "resumedAt", Instant.now().toString()
                ));

        log.info("Workflow resumed [conversationId={}, userId={}]", conversationId, userId);
        return ResponseEntity.ok(new ChatResponse(conversationId, answer));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String resolveConversationId(String requested) {
        return (requested != null && !requested.isBlank()) ? requested : UUID.randomUUID().toString();
    }

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
     * Builds the resume prompt by re-injecting the stored checkpoint state so the
     * model can continue the investigation without losing context.
     */
    private String buildResumePrompt(Checkpoint checkpoint) {
        Map<String, Object> state = checkpoint.getStateData();
        String lastMessage = state != null ? (String) state.getOrDefault("lastUserMessage", "N/A") : "N/A";
        String lastAnswer  = state != null ? (String) state.getOrDefault("lastAnswer", "N/A") : "N/A";

        return """
                [WORKFLOW RESUME] The operator has re-joined an interrupted investigation.
                
                Workflow   : %s
                Last step  : %s
                Status     : %s
                
                What the operator last asked:
                "%s"
                
                What you last answered (summary):
                "%s"
                
                Please briefly recap where the investigation stands and propose the immediate next action.
                """.formatted(
                checkpoint.getWorkflowName(),
                checkpoint.getCurrentStep(),
                checkpoint.getStatus(),
                lastMessage,
                lastAnswer.length() > 300 ? lastAnswer.substring(0, 300) + "…" : lastAnswer
        );
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
