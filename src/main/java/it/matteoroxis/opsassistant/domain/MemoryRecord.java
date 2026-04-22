package it.matteoroxis.opsassistant.domain;

/**
 * Lightweight projection of a long-term memory entry as stored in the MongoDB
 * {@code memories} collection by the {@link org.springframework.ai.vectorstore.VectorStore}.
 *
 * <p>The VectorStore persists documents in the format:
 * <pre>{id, content, metadata: {userId, memoryType, importanceScore, sourceConversationId, createdAt}, embedding}</pre>
 * This record maps that structure for in-application usage without duplicating storage.
 *
 * @param id                    MongoDB document id
 * @param userId                owner of this memory
 * @param content               the memory text
 * @param memoryType            semantic type of the memory (see {@link MemoryType})
 * @param importanceScore       0.0–1.0 relevance/importance score
 * @param sourceConversationId  conversation that originated this memory (nullable)
 * @param score                 similarity score returned by the vector search (0 if loaded from repo)
 */
public record MemoryRecord(
        String id,
        String userId,
        String content,
        String memoryType,
        double importanceScore,
        String sourceConversationId,
        double score
) {
    /** Convenience constructor used when loading from a non-vector query (score unknown). */
    public MemoryRecord(String id, String userId, String content,
                        String memoryType, double importanceScore, String sourceConversationId) {
        this(id, userId, content, memoryType, importanceScore, sourceConversationId, 0.0);
    }
}
