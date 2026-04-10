package it.matteoroxis.opsassistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
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

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(5).similarityThreshold(0.5).build())
                                .build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}
