package com.google.gerrit.sshd;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.ThreadFactory;

public class CommandExecutorQueueProvider implements QueueProvider {

  private final int poolSize;
  private final WorkQueue.Executor interactiveExecutor;
  private final WorkQueue.Executor batchExecutor;

  @Inject
  public CommandExecutorQueueProvider(@GerritServerConfig final Config config,
      final WorkQueue queues) {
    final int cores = Runtime.getRuntime().availableProcessors();
    poolSize = config.getInt("sshd", "threads", 3 * cores / 2);
    int interactiveThreads = config.getInt("sshd", "interactive",
        (int) 0.75f * poolSize);
    int batchThreads = poolSize - interactiveThreads;
    interactiveExecutor = queues.createQueue(interactiveThreads, "SSH-Worker");
    batchExecutor = queues.createQueue(batchThreads, "SSH-Batch-Worker");
    setThreadFactory(interactiveExecutor);
    setThreadFactory(batchExecutor);
  }

  private void setThreadFactory(WorkQueue.Executor executor) {
    final ThreadFactory parent = executor.getThreadFactory();
    executor.setThreadFactory(new ThreadFactory() {
      @Override
      public Thread newThread(final Runnable task) {
        final Thread t = parent.newThread(task);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
      }
    });
  }

  @Override
  public WorkQueue.Executor getInteractiveQueue() {
    return interactiveExecutor;
  }

  @Override
  public WorkQueue.Executor getBatchQueue() {
    return batchExecutor;
  }

}
