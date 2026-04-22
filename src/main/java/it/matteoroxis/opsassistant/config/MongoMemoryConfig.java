package it.matteoroxis.opsassistant.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Declares a second {@link VectorStore} bean backed by the {@code memories} MongoDB collection.
 *
 * <p>The primary VectorStore (auto-configured by Spring AI) uses the {@code knowledge_chunks}
 * collection for RAG retrieval. This bean uses the {@code memories} collection for long-term
 * memory semantic recall, and is injected into {@link it.matteoroxis.opsassistant.service.MemoryService}
 * via {@code @Qualifier("memoriesVectorStore")}.
 *
 * <p>Prerequisites (create manually in Atlas UI → Atlas Search → Create Search Index):
 * <pre>
 * Collection: memories
 * Index name: memories_vector_index
 * Type: Vector Search
 * Fields:
 *   { "type": "vector", "path": "embedding", "numDimensions": 1536, "similarity": "cosine" }
 *   { "type": "filter", "path": "userId" }
 *   { "type": "filter", "path": "memoryType" }
 * </pre>
 */
@Configuration
public class MongoMemoryConfig {

    @Value("${ops-assistant.memory.collection-name:memories}")
    private String collectionName;

    @Value("${ops-assistant.memory.index-name:memories_vector_index}")
    private String indexName;

    @Bean("memoriesVectorStore")
    public VectorStore memoriesVectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel) {
        return MongoDBAtlasVectorStore.builder(mongoTemplate, embeddingModel)
                .collectionName(collectionName)
                .vectorIndexName(indexName)
                .pathName("embedding")
                .metadataFieldsToFilter(List.of("userId", "memoryType"))
                .initializeSchema(false)
                .build();
    }
}
