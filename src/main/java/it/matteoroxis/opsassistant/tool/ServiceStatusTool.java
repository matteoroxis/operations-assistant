package it.matteoroxis.opsassistant.tool;

import it.matteoroxis.opsassistant.domain.ToolExecution;
import it.matteoroxis.opsassistant.service.CheckpointService;
import it.matteoroxis.opsassistant.util.ConversationContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spring AI tool that retrieves the operational status of a named service.
 *
 * <p>In this demo the metrics are mocked; in production this class would call a
 * monitoring API (Datadog, Prometheus, etc.) to fetch real CPU, memory, and health data.
 *
 * <p>Every invocation writes a {@link ToolExecution} document to {@code tool_executions}
 * and registers the execution reference on the active checkpoint, providing a full audit
 * trail of what the model observed during a workflow.
 *
 * <p>The bean is created by {@link it.matteoroxis.opsassistant.config.ToolConfig} and
 * registered on the {@code ChatClient} via {@code defaultTools(serviceStatusTool)}.
 */
public class ServiceStatusTool {

    private static final Logger log = LoggerFactory.getLogger(ServiceStatusTool.class);

    private final MongoTemplate mongoTemplate;
    private final CheckpointService checkpointService;

    public ServiceStatusTool(MongoTemplate mongoTemplate, CheckpointService checkpointService) {
        this.mongoTemplate = mongoTemplate;
        this.checkpointService = checkpointService;
    }

    /**
     * Returns the current CPU usage, memory usage, and health status of the named service.
     *
     * @param serviceName name of the service to inspect (e.g. {@code payment-service})
     * @param environment deployment environment: {@code prod}, {@code staging}, or {@code dev}
     * @return a map of metric key-value pairs
     */
    @Tool(description = """
            Retrieves the current CPU usage, memory usage, error rate, and health status \
            of a named service in a given environment. \
            Use this tool when investigating alerts related to service degradation or high resource usage. \
            Returns mocked metrics for demonstration purposes.""")
    public Map<String, Object> getServiceStatus(String serviceName, String environment) {
        Instant startedAt = Instant.now();
        String convId = ConversationContextHolder.get();
        String resolvedService = serviceName != null ? serviceName : "unknown";
        String resolvedEnv = environment != null ? environment : "prod";

        // Mocked metrics — in production, call your monitoring API here
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("serviceName", resolvedService);
        response.put("environment", resolvedEnv);
        response.put("cpuUsage", "87%");
        response.put("memoryUsage", "62%");
        response.put("healthStatus", "DEGRADED");
        response.put("activeThreads", 320);
        response.put("errorRate", "2.3%");
        response.put("p99LatencyMs", 1240);
        response.put("lastChecked", Instant.now().toString());

        // Persist the tool execution audit record
        String executionId = UUID.randomUUID().toString();
        Map<String, Object> requestPayload = Map.of(
                "serviceName", resolvedService,
                "environment", resolvedEnv);

        ToolExecution execution = new ToolExecution(
                executionId, convId, null,
                "getServiceStatus", requestPayload, response,
                "COMPLETED", startedAt, Instant.now());
        mongoTemplate.save(execution);

        // Link this execution to the active checkpoint (best-effort — no-op if none exists)
        if (!convId.isEmpty()) {
            checkpointService.addToolExecutionRef(convId, executionId);
        }

        log.info("getServiceStatus called [service={}, env={}, convId={}, executionId={}]",
                resolvedService, resolvedEnv, convId, executionId);
        return response;
    }
}
