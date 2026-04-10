package it.matteoroxis.opsassistant.api.dto;

/**
 * Response envelope for a single chat turn.
 *
 * @param answer the assistant's reply
 */
public record ChatResponse(String answer) {}
