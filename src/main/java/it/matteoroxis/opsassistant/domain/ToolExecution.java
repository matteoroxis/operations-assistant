package it.matteoroxis.opsassistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Audit record of a single Spring AI tool invocation.
 *
 * <p>Every call to a {@code @Tool}-annotated method writes one document to the
 * {@code tool_executions} collection, providing a full audit trail of what was
 * requested and what was returned — essential for debugging and postmortem analysis.
 */
@Document(collection = "tool_executions")
public class ToolExecution {

    @Id
    private String executionId;
    private String conversationId;
    private String taskId;
    private String toolName;
    private Map<String, Object> requestPayload;
    private Map<String, Object> responsePayload;
    private String status;
    private Instant startedAt;
    private Instant completedAt;

    public ToolExecution() {}

    public ToolExecution(String executionId, String conversationId, String taskId,
                         String toolName, Map<String, Object> requestPayload,
                         Map<String, Object> responsePayload, String status,
                         Instant startedAt, Instant completedAt) {
        this.executionId = executionId;
        this.conversationId = conversationId;
        this.taskId = taskId;
        this.toolName = toolName;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Map<String, Object> requestPayload) { this.requestPayload = requestPayload; }

    public Map<String, Object> getResponsePayload() { return responsePayload; }
    public void setResponsePayload(Map<String, Object> responsePayload) { this.responsePayload = responsePayload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
