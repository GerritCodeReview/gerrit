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

import com.google.common.collect.Sets;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class is managing the merging of sets of changes by keeping a lock
 * for each branch which is currently being operated on. The merge is only
 * performed if all branches could be locked.
 */
@Singleton
public class ChangeMergeQueue implements MergeQueue {

  private final Map<Set<Change.Id>, WaitingEntry> recheck = new HashMap<>();

  private final Set<Branch.NameKey> currentBranches = Sets.newHashSet();

  private static final long CONGESTED_DELAY =
      MILLISECONDS.convert(1, SECONDS);

  private final WorkQueue workQueue;
  private final Injector injector;

  @Inject
  ChangeMergeQueue(
      final WorkQueue wq,
      Injector inj) {
    workQueue = wq;
    injector = inj;
  }

  @Override
  public synchronized void merge(Iterable<Change> changes) {
    WaitingEntry e = new WaitingEntry(changes);
    if (!mergeIfPossible(e)) {
      // We cannot merge this right now as at least one branch is locked
      // TODO(sbeller): throw an CongestedException and leave the decision up to
      // the caller or reschedule?
      schedule(changes);
    }
  }

  @Override
  public synchronized void schedule(Iterable<Change> changes) {
    recheckAfter(changes, 0, MILLISECONDS);
  }

  @Override
  public synchronized void recheckAfter(Iterable<Change> changes,
      long delay, TimeUnit delayUnit) {
    final long now = TimeUtil.nowMs();
    final long at = now + MILLISECONDS.convert(delay, delayUnit);
    WaitingEntry e = getOrCreateWaitingEntry(changes);
    e.recheckAt = Math.max(at, e.recheckAt);
  }

  /**
   * Merges the WaitingEntry if all locks required can be locked.
   *
   * @param w the WaitingEntry to be used
   * @return if the merge was performed
   */
  private synchronized boolean mergeIfPossible(WaitingEntry w) {
    if (canMerge(w)) {
      mergeImpl(w);
      return true;
    }
    return false;
  }

  Set<Change.Id> getId(Iterable<Change> changes) {
    HashSet<Change.Id> ret = Sets.newHashSet();
    for (Change c : changes) {
      ret.add(c.getId());
    }
    return ret;
  }

  /**
   * Returns a WaitingEntry from the recheck set containing the given changes.
   * If these changes are in no WaitingEntry in the recheck set, this creates
   * a new entry and adds it to the recheck set.
   *
   * @param changes
   * @return a WaitingEntry which is scheduled and put in recheck
   */
  private synchronized WaitingEntry getOrCreateWaitingEntry(
      Iterable<Change> changes) {

    Set<Change.Id> setId = getId(changes);
    WaitingEntry w = recheck.get(setId);

    if (w == null) {
      w = new WaitingEntry(changes);
      recheck.put(setId, w);
    }
    return w;
  }

  /**
   * Checks if all locks required for merging are available.
   *
   * @param w the WaitingEntry job to be run
   * @return if currently all locks are free
   */
  private synchronized boolean canMerge(WaitingEntry w) {
    return Sets.intersection(w.getBranches(), currentBranches).isEmpty();
  }

  /**
   * Merges
   *
   * @param w the WaitingEntry containing the changes to be merged
   */
  private synchronized void mergeImpl(WaitingEntry w) {
    try {
      currentBranches.addAll(w.getBranches());
      new MergeOpMapper(injector, w.changes).merge();
    } finally {
      if (!w.needMerge) {
        currentBranches.removeAll(w.getBranches());
      } else {
        if (!w.jobScheduled) {
          // No job has been scheduled to execute this set of changes,
          // but it needs to run a merge again.
          //
          w.jobScheduled = true;
          workQueue.getDefaultQueue().schedule(w, 0, TimeUnit.SECONDS);
        }
      }
    }
  }

  private static Set<Branch.NameKey> getBranches(Iterable<Change> changes) {
    // TODO(sbeller): add submodule subscription propagation as well
    Set<Branch.NameKey> ret = Sets.newHashSet();
    for (Change c : changes) {
      ret.add(c.getDest());
    }
    return ret;
  }

  private class WaitingEntry implements Runnable {
    final Iterable<Change> changes;
    boolean needMerge;
    boolean jobScheduled;
    long recheckAt;

    WaitingEntry(Iterable<Change> changes) {
      this.changes = changes;
    }

    @Override
    public void run() {
      final long remainingDelay = recheckAt - TimeUtil.nowMs();
      if (MILLISECONDS.convert(10, SECONDS) < remainingDelay) {
        // Woke up too early, the job deadline was pushed back.
        // Reschedule for the new deadline. We allow for a small
        // amount of fuzz due to multiple reschedule attempts in
        // a short period of time being caused by MergeOp.
        //
        workQueue.getDefaultQueue().schedule(this, remainingDelay, MILLISECONDS);
      } else {
        // See if we can actually complete it this time.
        if (ChangeMergeQueue.this.mergeIfPossible(this)) {
          ChangeMergeQueue.this.recheck.remove(getId(changes));
        } else {
          workQueue.getDefaultQueue().schedule(this, CONGESTED_DELAY, MILLISECONDS);
        }
      }
    }

    public Set<Branch.NameKey> getBranches() {
      return ChangeMergeQueue.getBranches(changes);
    }

    @Override
    public String toString() {
      return "waiting for merge " + changes.toString();
    }
  }
}
