package com.google.gerrit.sshd;

import com.google.gerrit.server.git.WorkQueue;

public interface QueueProvider {

  public WorkQueue.Executor getInteractiveQueue();

  public WorkQueue.Executor getBatchQueue();

}
