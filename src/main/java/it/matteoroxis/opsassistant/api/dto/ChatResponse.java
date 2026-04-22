package it.matteoroxis.opsassistant.api.dto;

/**
 * Response envelope for a single chat turn.
 *
 * @param conversationId the conversation identifier (echoed back or newly generated)
 * @param answer         the assistant's reply
 */
public record ChatResponse(String conversationId, String answer) {}
