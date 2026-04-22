package it.matteoroxis.opsassistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted state for a multi-step operational workflow.
 *
 * <p>A {@code Checkpoint} is created when an investigation workflow begins and is updated
 * after each meaningful step — tool call, AI response, or human decision. This allows the
 * workflow to be paused (e.g. browser closed) and resumed later from exactly the same point.
 *
 * <p>The {@code expiresAt} field carries a MongoDB TTL index ({@code expireAfterSeconds=0})
 * so stale checkpoints are automatically cleaned up after 7 days.
 */
@Document(collection = "checkpoints")
public class Checkpoint {

    @Id
    private String checkpointId;
    private String conversationId;
    private String taskId;
    private String workflowName;
    private String currentStep;
    private CheckpointStatus status;
    private Map<String, Object> stateData;
    private List<String> pendingActions;
    private List<String> toolExecutionRefs;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * TTL: MongoDB will delete this document once the current time exceeds this value.
     * Create a TTL index manually in Atlas UI on field {@code expiresAt}:
     * go to Collections → checkpoints → Indexes → Create Index →
     * {@code { "expiresAt": 1 }} with expiration set to {@code 0} seconds after field value.
     * On Atlas M10+ with {@code auto-index-creation=true} the {@code @Indexed} is sufficient.
     */
    private Instant expiresAt;

    public Checkpoint() {}

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getCheckpointId() { return checkpointId; }
    public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public CheckpointStatus getStatus() { return status; }
    public void setStatus(CheckpointStatus status) { this.status = status; }

    public Map<String, Object> getStateData() { return stateData; }
    public void setStateData(Map<String, Object> stateData) { this.stateData = stateData; }

    public List<String> getPendingActions() { return pendingActions; }
    public void setPendingActions(List<String> pendingActions) { this.pendingActions = pendingActions; }

    public List<String> getToolExecutionRefs() { return toolExecutionRefs; }
    public void setToolExecutionRefs(List<String> toolExecutionRefs) { this.toolExecutionRefs = toolExecutionRefs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
