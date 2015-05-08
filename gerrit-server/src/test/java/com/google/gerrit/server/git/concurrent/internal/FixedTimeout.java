package org.jmock.lib.concurrent.internal;

import java.util.concurrent.TimeoutException;


/**
 * A Timeout of fixed duration from the time the FixedTimeout object is
 * instantiated.
 * 
 * @author nat
 */
public class FixedTimeout implements Timeout {
    private final long duration;
    private final long start;

    public FixedTimeout(long duration) {
        this.duration = duration;
        this.start = System.currentTimeMillis();
    }

    public long timeRemaining() throws TimeoutException {
        long now = System.currentTimeMillis();
        long timeLeft = duration - (now - start);

        if (timeLeft <= 0) {
            throw new TimeoutException("timed out after " + duration + " ms");
        }
        
        return timeLeft;
    }
}
