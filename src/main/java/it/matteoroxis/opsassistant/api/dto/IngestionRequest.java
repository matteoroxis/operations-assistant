package it.matteoroxis.opsassistant.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for ingesting a document into the knowledge base.
 *
 * @param content     raw text content to be chunked and embedded
 * @param sourceType  document type: runbook | SOP | FAQ | postmortem | alert-note
 * @param system      target system or service name (used for metadata filtering)
 * @param environment deployment environment: prod | staging | dev | all
 * @param severity    relevant severity level: critical | high | medium | low
 * @param team        owning team name
 * @param tags        comma-separated freeform tags
 */
public record IngestionRequest(
        @NotBlank(message = "content must not be blank") String content,
        @NotBlank(message = "sourceType must not be blank") String sourceType,
        String system,
        String environment,
        String severity,
        String team,
        String tags
) {}
