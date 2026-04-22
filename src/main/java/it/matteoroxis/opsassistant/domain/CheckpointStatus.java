package it.matteoroxis.opsassistant.domain;

/**
 * Lifecycle states for a {@link Checkpoint}.
 *
 * <ul>
 *   <li>{@code RUNNING}          — the workflow is actively being processed</li>
 *   <li>{@code WAITING_INPUT}    — the workflow is paused, expecting additional user input</li>
 *   <li>{@code WAITING_APPROVAL} — a proposed action is waiting for human approval</li>
 *   <li>{@code COMPLETED}        — the workflow finished successfully</li>
 *   <li>{@code FAILED}           — the workflow finished with an error</li>
 * </ul>
 */
public enum CheckpointStatus {
    RUNNING,
    WAITING_INPUT,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED
}
