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
import com.google.gwtorm.client.IntKey;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is managing the merging of changes.
 */
@Singleton
public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<ChangeSet.Id, MergeEntry> active = new HashMap<>();
  private final Map<ChangeSet.Id, RecheckJob> recheck = new HashMap<>();

  private final HashSet<Branch.NameKey> currentBranches = new HashSet<>();

  private final WorkQueue workQueue;
  private final Provider<MergeOpMapper.Factory> bgFactory;
  private final MessageDigest hasher;

  @Inject
  ChangeMergeQueue(
      final WorkQueue wq,
      Provider<MergeOpMapper.Factory> bg)
      throws NoSuchAlgorithmException {
    workQueue = wq;
    bgFactory = bg;
    // todo where to catch the NoSuchAlgorithm exception?
    // we want to have the error message at startup
    hasher = MessageDigest.getInstance("MD5");
  }

  @Override
  public void merge(Iterable<Change> changes) {
    ChangeSet set = new ChangeSet(changes);
    if (start(set)) {
      mergeImpl(set);
    }
  }

  /**
   *
   * @param changes list of changes which should be merged
   * @return true if the changes are put in the active queue, blocking the
   *        branches being changed by the list of changes
   */
  private synchronized boolean start(ChangeSet changes) {
    MergeEntry e = active.get(changes.hash);
    if (e == null) {
      e = new MergeEntry(changes);
      HashSet<Branch.NameKey> intersect = new HashSet<>(e.getBranches());
      intersect.retainAll(currentBranches);
      if (intersect.isEmpty()) {
        // Let the caller attempt this merge, its the only one interested
        // in processing these branches right now.
        active.put(changes.hash, new MergeEntry(changes));
        currentBranches.addAll(e.getBranches());
        return true;
      } else {
        // We cannot merge this right now as at least one branch is locked
        // TODO(sbeller): throw an CongestedException or reschedule?
        schedule(changes);
        return false;
      }
    } else {
      // We have submitted this change set for merge already.
      // Request that the job queue handle this merge later.
      e.needMerge = true;
      return false;
    }
  }
  private synchronized void schedule(final ChangeSet set) {
    MergeEntry e = active.get(set.hash);
    HashSet<Branch.NameKey> intersect = new HashSet<>(e.getBranches());
    intersect.retainAll(currentBranches);
    if (intersect.isEmpty()) {
      e = new MergeEntry(set);
      e.needMerge = true;
      active.put(set.hash, e);
      scheduleJob(e);
    } else {
      e.needMerge = true;
    }
  }

  @Override
  public synchronized void schedule(Iterable<Change> changes) {
    ChangeSet set = new ChangeSet(changes);
    schedule(set);
  }

  @Override
  public synchronized void recheckAfter(Iterable<Change> changes,
      long delay, TimeUnit delayUnit) {
    ChangeSet set = new ChangeSet(changes);
    final long now = TimeUtil.nowMs();
    final long at = now + MILLISECONDS.convert(delay, delayUnit);
    RecheckJob e = recheck.get(changes);
    if (e == null) {
      e = new RecheckJob(set);
      workQueue.getDefaultQueue().schedule(e, now - at, MILLISECONDS);
      recheck.put(set.hash, e);
    }
    e.recheckAt = Math.max(at, e.recheckAt);
  }

  private synchronized void finish(ChangeSet changeSet) {
    final MergeEntry e = active.get(changeSet.hash);
    if (e == null) {
      // Not registered? Shouldn't happen but ignore it.
      log.debug("finish was called for an unknown change set");
      return;
    }

    if (!e.needMerge) {
      // No additional merges are in progress, we can delete it.
      active.remove(changeSet.hash);
      currentBranches.removeAll(changeSet.getBranches());
      return;
    }

    scheduleJob(e);
  }

  private void scheduleJob(final MergeEntry e) {
    if (!e.jobScheduled) {
      // No job has been scheduled to execute this set of changes,
      // but it needs to run a merge again.
      //
      e.jobScheduled = true;
      workQueue.getDefaultQueue().schedule(e, 0, TimeUnit.SECONDS);
    }
  }

  private synchronized void unschedule(final MergeEntry e) {
    e.jobScheduled = false;
    e.needMerge = false;
  }

  private void mergeImpl(final ChangeSet changeSet) {
    try {
      bgFactory.get().create(changeSet.changes).merge();
    } catch (Throwable e) {
      log.error("Merge attempt for " + changeSet.toString() + " failed", e);
    } finally {
      finish(changeSet);
    }
  }

  private synchronized void recheck(final RecheckJob e) {
    final long remainingDelay = e.recheckAt - TimeUtil.nowMs();
    if (MILLISECONDS.convert(10, SECONDS) < remainingDelay) {
      // Woke up too early, the job deadline was pushed back.
      // Reschedule for the new deadline. We allow for a small
      // amount of fuzz due to multiple reschedule attempts in
      // a short period of time being caused by MergeOp.
      //
      workQueue.getDefaultQueue().schedule(e, remainingDelay, MILLISECONDS);
    } else {
      // Schedule a merge attempt on this branch to see if we can
      // actually complete it this time.
      //
      schedule(e.changeSet);
    }
  }

  private class ChangeSet {
    class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
      private static final long serialVersionUID = 1L;

      protected int id;

      public Id(final int id) {
        this.id = id;
      }

      @Override
      public int get() {
        return id;
      }

      @Override
      protected void set(int newValue) {
        id = newValue;
      }
    }

    /*
     * The hash of the changes contained in this set of changes
     * It should be unique or the merge doesn't trigger. Chosing an int
     * is considered good enough, as the birthday problem hints that we can
     * have up to 77k of change sets in the merge queue at the same time
     * before we'd be likely to run into a collision.
     */
    final Id hash;
    final Iterable<Change> changes;
    HashSet<Branch.NameKey> touchedBranches;

    ChangeSet(Iterable<Change> changes) {
      this.changes = changes;
      this.touchedBranches = null;

      MessageDigest md = hasher;
      ByteBuffer b = ByteBuffer.allocate(4);
      for (Change c : changes) {
        md.update(b.putInt(0, c.getChangeId()).array());
      }
      hash = new Id(ByteBuffer.wrap(md.digest()).getInt());
    }

    HashSet<Branch.NameKey> getBranches() {
      if (touchedBranches == null) {
        touchedBranches = new HashSet<>();
        for (Change c : changes) {
          touchedBranches.add(c.getDest());
        }
      }
      return touchedBranches;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Change c : changes) {
        sb.append("(");
        sb.append(c.getId());
        sb.append(":");
        sb.append(c.getDest());
        sb.append(")");
      }
      return "changeset " + sb.toString();
    }
  }

  private class MergeEntry implements Runnable {
    final ChangeSet changeSet;
    boolean needMerge;
    boolean jobScheduled;

    MergeEntry(final ChangeSet changes) {
      this.changeSet = changes;
    }

    @Override
    public void run() {
      unschedule(this);
      mergeImpl(changeSet);
    }

    HashSet<Branch.NameKey> getBranches() {
      return changeSet.getBranches();
    }

    @Override
    public String toString() {
      return "submit " + changeSet.toString();
    }
  }

  private class RecheckJob implements Runnable {
    final ChangeSet changeSet;
    long recheckAt;

    RecheckJob(final ChangeSet changes) {
      this.changeSet = changes;
    }

    @Override
    public void run() {
      recheck(this);
    }

    @Override
    public String toString() {
      return "recheck " + changeSet.toString();
    }
  }
}
