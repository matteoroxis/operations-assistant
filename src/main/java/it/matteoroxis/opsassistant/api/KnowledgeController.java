package it.matteoroxis.opsassistant.api;

import it.matteoroxis.opsassistant.api.dto.IngestionRequest;
import it.matteoroxis.opsassistant.api.dto.IngestionResponse;
import it.matteoroxis.opsassistant.service.IngestionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/ops/knowledge")
@Validated
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final IngestionService ingestionService;

    public KnowledgeController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * POST /api/ops/knowledge/ingest
     * Ingests a single document into the knowledge base.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResponse> ingest(@Valid @RequestBody IngestionRequest request) {
        int chunks = ingestionService.ingest(request);
        log.debug("Ingest completed: {} chunks", chunks);
        return ResponseEntity.ok(new IngestionResponse(chunks, "ingested"));
    }

    /**
     * POST /api/ops/knowledge/ingest/samples
     * Loads and ingests the bundled sample runbooks from classpath:runbooks/.
     * Useful for demo setup — call once after the Atlas Vector Search index is ready.
     */
    @PostMapping("/ingest/samples")
    public ResponseEntity<IngestionResponse> ingestSamples() throws IOException {
        int chunks = ingestionService.ingestSamples();
        log.info("Sample ingestion completed: {} total chunks", chunks);
        return ResponseEntity.ok(new IngestionResponse(chunks, "samples ingested"));
    }
}
