package it.matteoroxis.opsassistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a single (stateless) chat turn.
 * Article 1 — RAG only; no conversationId yet.
 *
 * @param message     the operator's question or description
 * @param system      optional: filter retrieved context to this system/service
 * @param environment optional: filter retrieved context to this environment
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,
        String system,
        String environment
) {}
