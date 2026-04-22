package it.matteoroxis.opsassistant.config;

import it.matteoroxis.opsassistant.advisor.LongTermMemoryAdvisor;
import it.matteoroxis.opsassistant.service.MemoryService;
import it.matteoroxis.opsassistant.tool.ServiceStatusTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    private static final String SYSTEM_PROMPT = """
            You are an experienced IT Operations Assistant. Your role is to help operations \
            teams investigate alerts, find relevant runbooks, explain procedures, and suggest \
            next steps for incident resolution.

            When answering:
            - Be concise and actionable.
            - Reference specific steps from the provided context when available.
            - If the context does not contain enough information, say so clearly and ask for more details.
            - Use numbered lists for multi-step procedures.
            - Always prioritise safety: flag any action that could cause downtime.
            """;

    /**
     * Short-term memory backed by MongoDB (MongoChatMemoryRepository auto-configured).
     * Keeps a sliding window of the last 20 messages per conversationId.
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * Long-term memory advisor — injects recalled memories into the system message.
     */
    @Bean
    public LongTermMemoryAdvisor longTermMemoryAdvisor(MemoryService memoryService) {
        return new LongTermMemoryAdvisor(memoryService);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 VectorStore vectorStore,
                                 ChatMemory chatMemory,
                                 LongTermMemoryAdvisor longTermMemoryAdvisor,
                                 ServiceStatusTool serviceStatusTool) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(serviceStatusTool)
                .defaultAdvisors(
                        // Order matters: long-term first (HIGHEST_PRECEDENCE + 5),
                        // then short-term memory, then RAG retrieval, then logging.
                        longTermMemoryAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(5).similarityThreshold(0.5).build())
                                .build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
