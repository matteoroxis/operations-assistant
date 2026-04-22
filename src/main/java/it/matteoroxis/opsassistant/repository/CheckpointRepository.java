package it.matteoroxis.opsassistant.repository;

import it.matteoroxis.opsassistant.domain.Checkpoint;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Checkpoint} documents stored in the
 * {@code checkpoints} MongoDB collection.
 *
 * <p>The derived query {@link #findTopByConversationIdOrderByUpdatedAtDesc} returns the
 * most recently updated checkpoint for a conversation, which is the authoritative state
 * used during workflow resume.
 */
public interface CheckpointRepository extends MongoRepository<Checkpoint, String> {

    /**
     * Returns the latest checkpoint for the given conversation, ordered by
     * {@code updatedAt} descending (most recent first).
     */
    Optional<Checkpoint> findTopByConversationIdOrderByUpdatedAtDesc(String conversationId);
}
