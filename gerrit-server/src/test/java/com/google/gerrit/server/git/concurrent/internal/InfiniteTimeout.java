package org.jmock.lib.concurrent.internal;

import java.util.concurrent.TimeoutException;

/**
 * A Timeout that never times out.
 * 
 * @author nat
 */
public class InfiniteTimeout implements Timeout {
    public long timeRemaining() throws TimeoutException {
        return 0L;
    }
}
