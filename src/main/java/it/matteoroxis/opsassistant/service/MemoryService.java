package it.matteoroxis.opsassistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.matteoroxis.opsassistant.domain.MemoryRecord;
import it.matteoroxis.opsassistant.domain.MemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the long-term memory layer of the Operations Assistant.
 *
 * <p>Long-term memories are stored in the {@code memories} MongoDB collection via a dedicated
 * {@link VectorStore} instance, enabling semantic recall across sessions. Each memory is a
 * document with text {@code content}, an {@code embedding} vector, and structured metadata
 * ({@code userId}, {@code memoryType}, {@code importanceScore}, etc.).
 *
 * <p>The {@link #consolidate} method calls the LLM to extract durable facts and preferences
 * from a completed conversation and saves them as typed memory entries.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final String COLLECTION = "memories";

    private static final String CONSOLIDATE_SYSTEM = """
            You are a memory extraction assistant for an IT Operations tool.
            Given a conversation, extract important facts, preferences, decisions, or summaries
            worth remembering across future sessions.
            Return a JSON array. Each element must have:
              - "memoryType": one of PREFERENCE, FACT, SUMMARY, EPISODE, DECISION
              - "content": a concise, self-contained statement of what to remember
              - "importanceScore": a float 0.0–1.0 (0 = trivial, 1 = critical)
            Only include genuinely reusable information. Skip conversational pleasantries.
            Return ONLY the JSON array, no other text or markdown fences.
            """;

    private final VectorStore memoriesVectorStore;
    private final MongoTemplate mongoTemplate;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public MemoryService(
            @Qualifier("memoriesVectorStore") VectorStore memoriesVectorStore,
            MongoTemplate mongoTemplate,
            ChatMemoryRepository chatMemoryRepository,
            ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.memoriesVectorStore = memoriesVectorStore;
        this.mongoTemplate = mongoTemplate;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Saves a single memory entry into the vector store.
     */
    public void save(String userId, String content, MemoryType memoryType,
                     double importanceScore, String sourceConversationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("memoryType", memoryType.name());
        metadata.put("importanceScore", importanceScore);
        metadata.put("sourceConversationId", sourceConversationId != null ? sourceConversationId : "");
        metadata.put("createdAt", Instant.now().toString());

        Document doc = new Document(content, metadata);
        memoriesVectorStore.add(List.of(doc));
        log.info("Saved memory [userId={}, type={}, score={}]", userId, memoryType, importanceScore);
    }

    /**
     * Performs a semantic similarity search in the memories collection, filtered by userId.
     *
     * @param userId    the user whose memories to search
     * @param queryText the current user message used as the search query
     * @param topK      maximum number of results
     * @return list of relevant memory records ordered by similarity
     */
    public List<MemoryRecord> findRelevant(String userId, String queryText, int topK) {
        if (userId == null || userId.isBlank()) return List.of();

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .filterExpression(b.eq("userId", userId).build())
                .build();

        List<Document> docs = memoriesVectorStore.similaritySearch(request);
        log.debug("findRelevant: {} result(s) for userId={}", docs.size(), userId);
        return docs.stream().map(MemoryService::toRecord).collect(Collectors.toList());
    }

    /**
     * Lists all memories for a user without vector search (used for the UI memory panel).
     *
     * @param userId the user whose memories to list
     */
    public List<MemoryRecord> findByUserId(String userId) {
        Query query = Query.query(Criteria.where("metadata.userId").is(userId));
        List<Map> rawDocs = mongoTemplate.find(query, Map.class, COLLECTION);
        return rawDocs.stream().map(MemoryService::toRecordFromMap).collect(Collectors.toList());
    }

    /**
     * Reads the specified conversation from short-term memory, sends it to the LLM to extract
     * durable facts, and saves the results as long-term memory entries.
     *
     * @param conversationId the conversation to consolidate
     * @param userId         owner of the memory entries
     * @return number of memory entries saved
     */
    public int consolidate(String conversationId, String userId) {
        List<Message> history = chatMemoryRepository.findByConversationId(conversationId);
        if (history.isEmpty()) {
            log.warn("consolidate: no messages found for conversationId={}", conversationId);
            return 0;
        }

        String conversationText = history.stream()
                .map(m -> m.getMessageType().getValue().toUpperCase() + ": " + m.getText())
                .collect(Collectors.joining("\n"));

        String userPrompt = "Conversation to analyse:\n\n" + conversationText;

        List<Message> llmMessages = List.of(
                new SystemMessage(CONSOLIDATE_SYSTEM),
                new UserMessage(userPrompt)
        );
        String json = chatModel.call(new Prompt(llmMessages)).getResult().getOutput().getText();

        List<Map<String, Object>> extracted = parseConsolidationResponse(json);
        int saved = 0;
        for (Map<String, Object> entry : extracted) {
            try {
                String content = (String) entry.get("content");
                String typeStr = (String) entry.getOrDefault("memoryType", "FACT");
                double score = ((Number) entry.getOrDefault("importanceScore", 0.5)).doubleValue();
                MemoryType type = parseMemoryType(typeStr);
                if (content != null && !content.isBlank()) {
                    save(userId, content, type, score, conversationId);
                    saved++;
                }
            } catch (Exception e) {
                log.warn("consolidate: skipping malformed entry: {}", entry, e);
            }
        }
        log.info("consolidate: saved {} memory entries for conversationId={}, userId={}", saved, conversationId, userId);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> parseConsolidationResponse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            // Strip markdown code fences if the model included them
            String cleaned = json.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("consolidate: failed to parse LLM JSON response: {}", json, e);
            return List.of();
        }
    }

    private static MemoryType parseMemoryType(String value) {
        try {
            return MemoryType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return MemoryType.FACT;
        }
    }

    @SuppressWarnings("unchecked")
    private static MemoryRecord toRecord(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        return new MemoryRecord(
                doc.getId(),
                getString(meta, "userId"),
                doc.getText(),
                getString(meta, "memoryType"),
                getDouble(meta, "importanceScore"),
                getString(meta, "sourceConversationId"),
                doc.getScore() != null ? doc.getScore() : 0.0
        );
    }

    @SuppressWarnings("unchecked")
    private static MemoryRecord toRecordFromMap(Map<String, Object> raw) {
        Object idObj = raw.get("_id");
        String id = idObj != null ? idObj.toString() : null;
        String content = (String) raw.getOrDefault("content", "");
        Map<String, Object> meta = (Map<String, Object>) raw.getOrDefault("metadata", Map.of());
        return new MemoryRecord(
                id,
                getString(meta, "userId"),
                content,
                getString(meta, "memoryType"),
                getDouble(meta, "importanceScore"),
                getString(meta, "sourceConversationId")
        );
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }
}
