package com.capstone.exception;

/**
 * Thrown when a concurrent modification is detected via optimistic locking.
 * This occurs when the version number at update time doesn't match the expected version.
 * The second writer is refused with ORD-409 status.
 */
public final class ConcurrentModificationException extends DomainException {

    public ConcurrentModificationException(long accountId, long expectedVersion) {
        super("ORD-409", String.format(
            "Account %d was modified concurrently (expected version %d)",
            accountId, expectedVersion
        ));
    }
}
