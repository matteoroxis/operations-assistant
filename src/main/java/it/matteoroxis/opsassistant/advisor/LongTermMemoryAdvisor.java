package it.matteoroxis.opsassistant.advisor;

import it.matteoroxis.opsassistant.domain.MemoryRecord;
import it.matteoroxis.opsassistant.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI {@link CallAdvisor} that injects relevant long-term memories into the system
 * message before the request reaches the LLM.
 *
 * <p>Usage: register as a default advisor in {@code ChatClient.Builder} and pass the
 * {@value #USER_ID} parameter at runtime:
 * <pre>{@code
 * chatClient.prompt()
 *     .advisors(spec -> spec.param(LongTermMemoryAdvisor.USER_ID, "alice"))
 *     .user("...")
 *     .call().content();
 * }</pre>
 *
 * <p>The advisor runs <em>before</em> the {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}
 * so that the recalled memories are available when the chat history is assembled.
 * Assign a {@link Ordered#HIGHEST_PRECEDENCE} + 5 order value to guarantee this.
 */
public class LongTermMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    /** Advisor parameter key for the user identifier. */
    public static final String USER_ID = "opsassistant.memory.userId";

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryAdvisor.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    private final MemoryService memoryService;
    private final int topK;

    public LongTermMemoryAdvisor(MemoryService memoryService) {
        this(memoryService, DEFAULT_TOP_K);
    }

    public LongTermMemoryAdvisor(MemoryService memoryService, int topK) {
        this.memoryService = memoryService;
        this.topK = topK;
    }

    @Override
    public String getName() {
        return LongTermMemoryAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    // -------------------------------------------------------------------------
    // CallAdvisor (non-streaming)
    // -------------------------------------------------------------------------

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(augmentWithMemories(request));
    }

    // -------------------------------------------------------------------------
    // StreamAdvisor (streaming)
    // -------------------------------------------------------------------------

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Mono.just(request)
                .publishOn(Schedulers.boundedElastic())
                .map(this::augmentWithMemories)
                .flatMapMany(chain::nextStream);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ChatClientRequest augmentWithMemories(ChatClientRequest request) {
        String userId = (String) request.context().get(USER_ID);
        if (userId == null || userId.isBlank()) {
            return request;
        }

        String userText = extractUserText(request);
        if (userText == null || userText.isBlank()) {
            return request;
        }

        List<MemoryRecord> memories = memoryService.findRelevant(userId, userText, topK);
        if (memories.isEmpty()) {
            log.debug("LongTermMemoryAdvisor: no memories found for userId={}", userId);
            return request;
        }

        log.debug("LongTermMemoryAdvisor: injecting {} memories for userId={}", memories.size(), userId);
        String memoryBlock = formatMemories(memories);
        return injectIntoSystemMessage(request, memoryBlock);
    }

    private String extractUserText(ChatClientRequest request) {
        try {
            Message userMsg = request.prompt().getUserMessage();
            return userMsg != null ? userMsg.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatMemories(List<MemoryRecord> memories) {
        return memories.stream()
                .map(m -> "- [" + m.memoryType() + "] " + m.content())
                .collect(Collectors.joining("\n"));
    }

    private ChatClientRequest injectIntoSystemMessage(ChatClientRequest request, String memoryBlock) {
        String memorySection = "\n\n## Relevant memories from past sessions:\n" + memoryBlock;
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());

        boolean hasSystem = false;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage sm) {
                messages.set(i, new SystemMessage(sm.getText() + memorySection));
                hasSystem = true;
                break;
            }
        }
        if (!hasSystem) {
            messages.add(0, new SystemMessage(memorySection.strip()));
        }

        Prompt augmented = prompt.getOptions() != null
                ? new Prompt(messages, prompt.getOptions())
                : new Prompt(messages);

        return request.mutate().prompt(augmented).build();
    }
}
