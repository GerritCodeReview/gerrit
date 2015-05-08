package org.jmock.lib.concurrent.internal;

import java.util.concurrent.TimeoutException;

public interface Timeout {
    /**
     * Returns the time remaining, to be passed to {@link Object#wait(long) wait}
     * or throws TimeoutException if the timeout has expired.
     */
    public abstract long timeRemaining() throws TimeoutException;
}
