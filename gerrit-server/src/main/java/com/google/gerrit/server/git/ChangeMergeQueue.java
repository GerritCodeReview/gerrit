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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import autovalue.shaded.com.google.common.common.collect.Sets;

/**
 * This class is managing the merging of sets of changes by keeping a lock
 * for each branch which is currently being operated on.
 */
@Singleton
public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<Iterable<Change>, WaitingEntry> active = new HashMap<>();
  private final Map<Iterable<Change>, WaitingEntry> recheck = new HashMap<>();

  private final Set<Branch.NameKey> currentBranches = new HashSet<>();

  private static final long CONGESTED_DELAY =
      MILLISECONDS.convert(1, SECONDS);

  private final WorkQueue workQueue;
  private final Provider<MergeOpMapper.Factory> bgFactory;

  @Inject
  ChangeMergeQueue(
      final WorkQueue wq,
      Provider<MergeOpMapper.Factory> bg) {
    workQueue = wq;
    bgFactory = bg;
  }

  @Override
  public void merge(Iterable<Change> changes) {
    WaitingEntry e = new WaitingEntry(changes);
    if (!mergeIfPossible(e)) {
      // We cannot merge this right now as at least one branch is locked
      // TODO(sbeller): throw an CongestedException and leave the decision up to
      // the caller or reschedule?
      schedule(changes);
    }
  }

  /**
   *
   * @param w the WaitingEntry job to be run
   * @return if currently all locks are free
   */
  private synchronized boolean canMerge(WaitingEntry w) {
    Set<Branch.NameKey> intersect = w.getBranches();
    intersect.retainAll(currentBranches);
    return intersect.isEmpty();
  }

  /**
   * Merges the WaitingEntry if all locks required can be locked.
   * @param w the WaitingEntry to be used
   * @return if the merge was performed
   */
  private synchronized boolean mergeIfPossible(WaitingEntry w) {
    if (canMerge(w)) {
      mergeImpl(w.changes);
      return true;
    }
    return false;
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
    WaitingEntry e = recheck.get(changes);
    if (e == null) {
      e = new WaitingEntry(changes);
      workQueue.getDefaultQueue().schedule(e, now - at, MILLISECONDS);
      recheck.put(changes, e);
    }
    e.recheckAt = Math.max(at, e.recheckAt);
  }

  private synchronized void finish(Iterable<Change> changes) {
    final WaitingEntry e = active.get(changes);
    if (e == null) {
      // Not registered? Shouldn't happen but ignore it.
      log.debug("finish was called for an unknown change set");
      return;
    }
    if (!e.needMerge) {
      currentBranches.removeAll(getBranches(changes));
    } else {
      if (!e.jobScheduled) {
        // No job has been scheduled to execute this set of changes,
        // but it needs to run a merge again.
        //
        e.jobScheduled = true;
        workQueue.getDefaultQueue().schedule(e, 0, TimeUnit.SECONDS);
      }
    }
  }

  private synchronized void unschedule(final WaitingEntry e) {
    e.jobScheduled = false;
    e.needMerge = false;
  }

  private void mergeImpl(Iterable<Change> changes) {
    try {
      bgFactory.get().create(changes).merge();
    } catch (Throwable e) {
      log.error("Merge attempt for " + changes.toString() + " failed", e);
    } finally {
      finish(changes);
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
          unschedule(this);
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
