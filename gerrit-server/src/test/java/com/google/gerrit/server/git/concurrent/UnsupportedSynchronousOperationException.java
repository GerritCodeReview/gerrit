package org.jmock.lib.concurrent;

/**
 * Thrown to report that a {@link DeterministicScheduler} has been asked to perform
 * a blocking wait, which is not supported.
 * 
 * @author nat
 *
 */
public class UnsupportedSynchronousOperationException extends UnsupportedOperationException {
    public UnsupportedSynchronousOperationException(String message) {
        super(message);
    }
}
