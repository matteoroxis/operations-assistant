package it.matteoroxis.opsassistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a chat turn.
 * Article 2+: includes conversationId (nullable → new conversation) and userId.
 *
 * @param conversationId optional conversation identifier; if null a new UUID is generated
 * @param userId         identifier of the operator; used for long-term memory recall
 * @param message        the operator's question or description
 * @param system         optional: filter retrieved context to this system/service
 * @param environment    optional: filter retrieved context to this environment
 */
public record ChatRequest(
        String conversationId,
        String userId,
        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,
        String system,
        String environment
) {}
