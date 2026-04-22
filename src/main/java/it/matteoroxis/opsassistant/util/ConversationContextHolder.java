package it.matteoroxis.opsassistant.util;

/**
 * Thread-local holder that propagates the active {@code conversationId} from the
 * HTTP request thread into Spring AI {@code @Tool} implementations.
 *
 * <p>Spring AI calls tool methods on the same thread that initiated the {@code ChatClient}
 * call, so a {@link ThreadLocal} is a safe and lightweight mechanism to pass the current
 * conversation context without polluting method signatures with infrastructure concerns.
 *
 * <p>Usage in controllers:
 * <pre>{@code
 * ConversationContextHolder.set(conversationId);
 * try {
 *     chatClient.prompt()...call();
 * } finally {
 *     ConversationContextHolder.clear(); // always clean up to prevent leaks
 * }
 * }</pre>
 */
public final class ConversationContextHolder {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();

    private ConversationContextHolder() {}

    /** Sets the active conversation ID for the current thread. */
    public static void set(String id) {
        CONVERSATION_ID.set(id);
    }

    /**
     * Returns the active conversation ID, or an empty string if none has been set.
     * Tool implementations should treat an empty string as "unknown".
     */
    public static String get() {
        String id = CONVERSATION_ID.get();
        return id != null ? id : "";
    }

    /**
     * Clears the thread-local value. <strong>Must</strong> be called in a {@code finally}
     * block after the {@code ChatClient} call to prevent id leakage across thread-pool reuse.
     */
    public static void clear() {
        CONVERSATION_ID.remove();
    }
}
