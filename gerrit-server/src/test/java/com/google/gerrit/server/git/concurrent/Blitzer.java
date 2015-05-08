package org.jmock.lib.concurrent;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;


/**
 * A class that "blitzes" an object by calling it many times, from multiple
 * threads. Used for stress-testing synchronisation.
 * 
 * @author nat
 */
public class Blitzer {
    /**
     * The default number of threads to run concurrently.
     */
    public static final int DEFAULT_THREAD_COUNT = 2;

    private final ExecutorService executorService;
    private final int actionCount;
    
    public Blitzer(int actionCount) {
        this(actionCount, DEFAULT_THREAD_COUNT);
    }
    
    public Blitzer(int actionCount, int threadCount) {
        this(actionCount, threadCount, Executors.defaultThreadFactory());
    }

    public Blitzer(int actionCount, int threadCount, ThreadFactory threadFactory) {
        this.actionCount = actionCount;
        this.executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
    }

    public Blitzer(int actionCount, ExecutorService executorService) {
        this.actionCount = actionCount;
        this.executorService = executorService;
    }

    public int totalActionCount() {
        return actionCount;
    }

    public void blitz(final Runnable action) throws InterruptedException {
        spawnThreads(action).await();
    }

    public void blitz(long timeoutMs, final Runnable action) throws InterruptedException, TimeoutException {
        if (!spawnThreads(action).await(timeoutMs, MILLISECONDS)) {
            throw new TimeoutException("timed out waiting for blitzed actions to complete successfully");
        }
    }

    private CountDownLatch spawnThreads(final Runnable action) {
        final CountDownLatch finished = new CountDownLatch(actionCount);
        
        for (int i = 0; i < actionCount; i++) {
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        action.run();
                    }
                    finally {
                        finished.countDown();
                    }
                }
            });
        }
        
        return finished;
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
