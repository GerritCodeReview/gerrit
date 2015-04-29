// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.git;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class is managing the merging of sets of changes by keeping a lock
 * for each branch which is currently being operated on. The merge is only
 * performed if all branches could be locked.
 */
@Singleton
public class ChangeMergeQueue implements MergeQueue {

  private final PriorityLockedQueue<Branch.NameKey, ChangeSetTask> queue;

  private final WorkQueue workQueue;
  private ChangeSetMergeOp changeSetMerger;

  @Inject
  ChangeMergeQueue(final WorkQueue wq, Injector inj) {
    workQueue = wq;
    queue = new ChangePriorityLockedQueue(Executors.newScheduledThreadPool(0));
    changeSetMerger = new ChangeSetMergeOp(inj);
  }

  @Override
  public synchronized void merge(ChangeSet changes) {
    try {
      ChangeSetTask task = new ChangeSetTask(changes);
      if (!queue.processTask(task)) {
        // TODO(sbeller): try to some backoff and retries, then tell the user
        // if it fails.
        queue.schedule(task, 0);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void schedule(ChangeSet changes) {
    recheckAfter(changes, 0, MILLISECONDS);
    queue.schedule(new ChangeSetTask(changes), 0);
  }

  @Override
  public synchronized void recheckAfter(ChangeSet changes,
      long delay, TimeUnit delayUnit) {
    queue.schedule(new ChangeSetTask(changes), MILLISECONDS.convert(delay, delayUnit));
  }

  class ChangeSetTask implements ResourceTask<Branch.NameKey> {

    private final ChangeSet changeSet;
    public ChangeSetTask(ChangeSet changes) {
      changeSet = changes;
    }

    @Override
    public Set<NameKey> resources() {
      return changeSet.branches();
    }

    public ChangeSet changes() {
      return changeSet;
    }
  }

  private class ChangePriorityLockedQueue extends PriorityLockedQueue<Branch.NameKey, ChangeSetTask> {
    ChangePriorityLockedQueue(ScheduledExecutorService sched) {
      super(sched);
    }

    @Override
    void process(ChangeSetTask task) {
      changeSetMerger.merge(task.changes());
      processed(task);
    }
  }
}
