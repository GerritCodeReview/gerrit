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
 * This class manages the merging of sets of changes by keeping a lock
 * for each branch which is currently being operated on.
 * <p>
 * The merge is only performed if all branches could be locked.
 */
@Singleton
public class ChangeMergeQueue implements MergeQueue {

  private final PriorityLockedQueue<Branch.NameKey, ChangeSetTask> queue;

  private ChangeSetMergeOp changeSetMerger;

  @Inject
  ChangeMergeQueue(Injector inj) {
    queue = new ChangePriorityLockedQueue(Executors.newScheduledThreadPool(0));
    changeSetMerger = new ChangeSetMergeOp(inj);
  }

  @Override
  public synchronized void merge(ChangeSet changes) {
    ChangeSetTask task = new ChangeSetTask(changes);
    if (!queue.process(task)) {
      queue.schedule(task, 0);
    }
  }

  @Override
  public synchronized void schedule(ChangeSet changes) {
    queue.schedule(new ChangeSetTask(changes), 0);
  }

  @Override
  public synchronized void recheckAfter(ChangeSet changes,
      long delay, TimeUnit delayUnit) {
    queue.schedule(new ChangeSetTask(changes), MILLISECONDS.convert(delay, delayUnit));
  }

  private class ChangeSetTask implements PriorityLockedQueue.Task<Branch.NameKey> {
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

  private class ChangePriorityLockedQueue extends
      PriorityLockedQueue<Branch.NameKey, ChangeSetTask> {
    ChangePriorityLockedQueue(ScheduledExecutorService sched) {
      super(sched, 2500);
    }

    @Override
    void processTask(ChangeSetTask task) {
      changeSetMerger.merge(task.changes());
    }

    @Override
    void processAsyncTask(ChangeSetTask task) {
      // TODO(sbeller): put this in the background worker thread pool
      changeSetMerger.merge(task.changes());
      processingAsyncTaskDone(task);
    }
  }
}
