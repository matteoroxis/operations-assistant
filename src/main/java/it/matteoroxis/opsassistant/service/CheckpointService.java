package it.matteoroxis.opsassistant.service;

import it.matteoroxis.opsassistant.domain.Checkpoint;
import it.matteoroxis.opsassistant.domain.CheckpointStatus;
import it.matteoroxis.opsassistant.repository.CheckpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of {@link Checkpoint} documents.
 *
 * <p>A checkpoint represents the persisted state of an in-progress operational workflow,
 * enabling it to be suspended and resumed across sessions. Every meaningful step in the
 * workflow — user message, AI response, tool call, human approval — is reflected by a
 * call to {@link #updateStep}.
 */
@Service
public class CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

    /** Default TTL: checkpoints expire after 7 days of inactivity. */
    private static final long TTL_SECONDS = 7L * 24 * 60 * 60;

    private final CheckpointRepository checkpointRepository;

    public CheckpointService(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new checkpoint for the given conversation / task.
     *
     * @param conversationId the Spring AI conversation identifier
     * @param taskId         an application-level task identifier (free-form)
     * @param workflowName   a human-readable name for the workflow being tracked
     * @return the persisted {@link Checkpoint}
     */
    public Checkpoint create(String conversationId, String taskId, String workflowName) {
        Instant now = Instant.now();
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setCheckpointId(UUID.randomUUID().toString());
        checkpoint.setConversationId(conversationId);
        checkpoint.setTaskId(taskId);
        checkpoint.setWorkflowName(workflowName);
        checkpoint.setCurrentStep("INIT");
        checkpoint.setStatus(CheckpointStatus.RUNNING);
        checkpoint.setStateData(Map.of());
        checkpoint.setPendingActions(List.of());
        checkpoint.setToolExecutionRefs(List.of());
        checkpoint.setCreatedAt(now);
        checkpoint.setUpdatedAt(now);
        checkpoint.setExpiresAt(now.plusSeconds(TTL_SECONDS));
        Checkpoint saved = checkpointRepository.save(checkpoint);
        log.info("Checkpoint created [conversationId={}, taskId={}, workflow={}]",
                conversationId, taskId, workflowName);
        return saved;
    }

    /**
     * Advances the checkpoint to a new step and optionally replaces the state data.
     *
     * @param conversationId the conversation whose latest checkpoint to update
     * @param newStep        the name of the step now being entered
     * @param status         the new lifecycle status
     * @param stateData      merged state data (pass {@code null} to leave existing data unchanged)
     * @return the updated {@link Checkpoint}
     * @throws IllegalStateException if no checkpoint exists for the conversation
     */
    public Checkpoint updateStep(String conversationId, String newStep,
                                 CheckpointStatus status, Map<String, Object> stateData) {
        Checkpoint checkpoint = requireLatest(conversationId);
        checkpoint.setCurrentStep(newStep);
        checkpoint.setStatus(status);
        if (stateData != null) {
            // Merge incoming data over existing state so previous keys are retained
            Map<String, Object> merged = new HashMap<>(
                    checkpoint.getStateData() != null ? checkpoint.getStateData() : Map.of());
            merged.putAll(stateData);
            checkpoint.setStateData(merged);
        }
        checkpoint.setUpdatedAt(Instant.now());
        checkpoint.setExpiresAt(Instant.now().plusSeconds(TTL_SECONDS)); // slide expiry on activity
        Checkpoint saved = checkpointRepository.save(checkpoint);
        log.debug("Checkpoint updated [conversationId={}, step={}, status={}]",
                conversationId, newStep, status);
        return saved;
    }

    /**
     * Returns the most recently updated checkpoint for a conversation, if any.
     */
    public Optional<Checkpoint> loadLatest(String conversationId) {
        return checkpointRepository.findTopByConversationIdOrderByUpdatedAtDesc(conversationId);
    }

    /**
     * Records a tool execution reference on the latest checkpoint.
     *
     * @param conversationId the conversation that triggered the tool call
     * @param executionId    the {@code ToolExecution.executionId} to record
     */
    public void addToolExecutionRef(String conversationId, String executionId) {
        loadLatest(conversationId).ifPresent(cp -> {
            List<String> refs = new ArrayList<>(
                    cp.getToolExecutionRefs() != null ? cp.getToolExecutionRefs() : List.of());
            refs.add(executionId);
            cp.setToolExecutionRefs(refs);
            cp.setUpdatedAt(Instant.now());
            checkpointRepository.save(cp);
        });
    }

    /**
     * Marks the latest checkpoint for a conversation as {@link CheckpointStatus#COMPLETED}.
     */
    public Checkpoint markCompleted(String conversationId) {
        return updateStep(conversationId, "COMPLETED", CheckpointStatus.COMPLETED, null);
    }

    /**
     * Marks the latest checkpoint for a conversation as {@link CheckpointStatus#FAILED}
     * and records the failure reason in state data.
     *
     * @param conversationId the conversation that failed
     * @param reason         a short human-readable description of the failure
     */
    public Checkpoint markFailed(String conversationId, String reason) {
        return updateStep(conversationId, "FAILED", CheckpointStatus.FAILED,
                Map.of("failureReason", reason != null ? reason : "unknown"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Checkpoint requireLatest(String conversationId) {
        return checkpointRepository.findTopByConversationIdOrderByUpdatedAtDesc(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No checkpoint found for conversationId: " + conversationId));
    }
}
