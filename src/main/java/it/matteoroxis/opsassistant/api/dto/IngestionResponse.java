package it.matteoroxis.opsassistant.api.dto;

/**
 * Response envelope returned after an ingestion request.
 *
 * @param chunks number of chunks stored in the vector store
 * @param status human-readable status message
 */
public record IngestionResponse(int chunks, String status) {}
