package it.matteoroxis.opsassistant.config;

import it.matteoroxis.opsassistant.service.CheckpointService;
import it.matteoroxis.opsassistant.tool.ServiceStatusTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Registers Spring AI tools on the application context.
 *
 * <p>Tool beans are picked up by {@link ChatConfig#chatClient} via
 * {@code ChatClient.Builder.defaultTools()} so the model can call them during
 * any conversation without per-request registration.
 *
 * <p>Adding a new tool:
 * <ol>
 *   <li>Create a class with one or more {@code @Tool}-annotated methods.</li>
 *   <li>Declare it as a {@code @Bean} here.</li>
 *   <li>Inject the bean into {@link ChatConfig#chatClient} and include it in
 *       {@code defaultTools(...)}.</li>
 * </ol>
 */
@Configuration
public class ToolConfig {

    /**
     * Provides the {@link ServiceStatusTool} bean that queries service health metrics.
     * Declared here (rather than as {@code @Component}) to keep all tool registration
     * in one place and to make dependencies explicit.
     */
    @Bean
    public ServiceStatusTool serviceStatusTool(MongoTemplate mongoTemplate,
                                               CheckpointService checkpointService) {
        return new ServiceStatusTool(mongoTemplate, checkpointService);
    }
}
