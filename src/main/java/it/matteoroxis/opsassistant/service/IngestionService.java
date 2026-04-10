package it.matteoroxis.opsassistant.service;

import it.matteoroxis.opsassistant.api.dto.IngestionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourcePatternResolver;
    private final TokenTextSplitter splitter;

    public IngestionService(VectorStore vectorStore, ResourcePatternResolver resourcePatternResolver) {
        this.vectorStore = vectorStore;
        this.resourcePatternResolver = resourcePatternResolver;
        // TokenTextSplitter arguments:
        // 1) chunk size: target number of tokens per chunk
        // 2) minimum chunk size: minimum text length before splitting
        // 3) chunk overlap: number of tokens shared between adjacent chunks
        // 4) max tokens: upper bound used while processing a document
        // 5) preserve separators: keep text separators in the resulting chunks
        this.splitter = new TokenTextSplitter(
                800,
                350,
                5,
                10_000,
                true
        );
    }

    /**
     * Chunks the incoming text, generates embeddings and stores them in the vector store.
     *
     * @return number of chunks stored
     */
    public int ingest(IngestionRequest request) {
        Map<String, Object> metadata = buildMetadata(request);
        Document doc = new Document(request.content(), metadata);
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.add(chunks);
        log.info("Ingested {} chunk(s) [sourceType={}, system={}]",
                chunks.size(), request.sourceType(), request.system());
        return chunks.size();
    }

    /**
     * Loads all Markdown runbooks bundled under {@code classpath:runbooks/} and ingests them.
     * Useful for demo setup without manual API calls.
     *
     * @return total number of chunks stored across all runbooks
     */
    public int ingestSamples() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:runbooks/*.md");
        if (resources.length == 0) {
            log.warn("No sample runbooks found under classpath:runbooks/");
            return 0;
        }
        int totalChunks = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> metadata = Map.of(
                    "sourceType", "runbook",
                    "system", extractSystemFromFilename(filename),
                    "environment", "all",
                    "team", "operations"
            );
            Document doc = new Document(content, metadata);
            List<Document> chunks = splitter.apply(List.of(doc));
            vectorStore.add(chunks);
            totalChunks += chunks.size();
            log.info("Ingested sample runbook '{}' — {} chunk(s)", filename, chunks.size());
        }
        return totalChunks;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildMetadata(IngestionRequest request) {
        Map<String, Object> meta = new HashMap<>();
        putIfPresent(meta, "sourceType", request.sourceType());
        putIfPresent(meta, "system", request.system());
        putIfPresent(meta, "environment", request.environment());
        putIfPresent(meta, "severity", request.severity());
        putIfPresent(meta, "team", request.team());
        putIfPresent(meta, "tags", request.tags());
        return meta;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * Derives a short system name from a runbook filename.
     * e.g. "runbook-cpu-investigation.md" → "cpu"
     */
    private String extractSystemFromFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replace("runbook-", "")
                       .replace(".md", "")
                       .split("-")[0];
    }
}
