package it.matteoroxis.opsassistant.api;

import it.matteoroxis.opsassistant.domain.MemoryRecord;
import it.matteoroxis.opsassistant.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
@Validated
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * GET /api/ops/memories/{userId}
     * Returns all long-term memory entries stored for a given user.
     */
    @GetMapping("/memories/{userId}")
    public ResponseEntity<List<MemoryRecord>> getMemories(@PathVariable String userId) {
        List<MemoryRecord> memories = memoryService.findByUserId(userId);
        log.debug("getMemories: {} entries for userId={}", memories.size(), userId);
        return ResponseEntity.ok(memories);
    }

    /**
     * POST /api/ops/chat/{conversationId}/consolidate?userId=...
     * Extracts durable facts from the specified conversation and saves them to long-term memory.
     */
    @PostMapping("/chat/{conversationId}/consolidate")
    public ResponseEntity<Map<String, Object>> consolidate(
            @PathVariable String conversationId,
            @RequestParam String userId) {
        int saved = memoryService.consolidate(conversationId, userId);
        log.info("consolidate: {} memories saved for conversationId={}, userId={}", saved, conversationId, userId);
        return ResponseEntity.ok(Map.of(
                "conversationId", conversationId,
                "userId", userId,
                "memoriesSaved", saved
        ));
    }
}
