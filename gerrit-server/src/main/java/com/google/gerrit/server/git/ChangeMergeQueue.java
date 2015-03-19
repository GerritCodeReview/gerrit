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
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.client.IntKey;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.RequestScoped;

import com.jcraft.jsch.HostKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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
  private final PerThreadRequestScope.Scoper threadScoper;
  private final MessageDigest hasher;

  @Inject
  ChangeMergeQueue(final WorkQueue wq, Injector parent)
      throws NoSuchAlgorithmException {
    workQueue = wq;

    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindScope(RequestScoped.class, PerThreadRequestScope.REQUEST);
        bind(RequestScopePropagator.class)
            .to(PerThreadRequestScope.Propagator.class);
        bind(PerThreadRequestScope.Propagator.class);
        install(new GerritRequestModule());

        bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
            new Provider<SocketAddress>() {
              @Override
              public SocketAddress get() {
                throw new OutOfScopeException("No remote peer on merge thread");
              }
            });
        bind(SshInfo.class).toInstance(new SshInfo() {
          @Override
          public List<HostKey> getHostKeys() {
            return Collections.emptyList();
          }
        });
      }

      @Provides
      public PerThreadRequestScope.Scoper provideScoper(
          final PerThreadRequestScope.Propagator propagator,
          final Provider<RequestScopedReviewDbProvider> dbProvider) {
        final RequestContext requestContext = new RequestContext() {
          @Override
          public CurrentUser getCurrentUser() {
            throw new OutOfScopeException("No user on merge thread");
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return dbProvider.get();
          }
        };
        return new PerThreadRequestScope.Scoper() {
          @Override
          public <T> Callable<T> scope(Callable<T> callable) {
            return propagator.scope(requestContext, callable);
          }
        };
      }
    });
    bgFactory = child.getProvider(MergeOpMapper.Factory.class);
    threadScoper = child.getInstance(PerThreadRequestScope.Scoper.class);

    // todo where to catch the NoSuchAlgorithm exception?
    // we want to have the error message at startup
    hasher = MessageDigest.getInstance("MD5");
  }

  @Override
  public void merge(Set<Change> changes) {
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
        // We cannot merge this right now as the branches are locked
        // TODO(sbeller): throw an CongestedException or reschedule?
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
  public synchronized void schedule(final Set<Change> changes) {
    ChangeSet set = new ChangeSet(changes);
    schedule(set);
  }

  @Override
  public synchronized void recheckAfter(Set<Change> changes,
      final long delay, final TimeUnit delayUnit) {
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
      //
      return;
    }

    if (!e.needMerge) {
      // No additional merges are in progress, we can delete it.
      //
      active.remove(changeSet.hash);
      currentBranches.removeAll(changeSet.getBranches());
      return;
    }

    scheduleJob(e);
  }

  private void scheduleJob(final MergeEntry e) {
    if (!e.jobScheduled) {
      // No job has been scheduled to execute this branch, but it needs
      // to run a merge again.
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
    // TODO(sbeller): We need to see if we can parallelize here
    for (final Change c : changeSet.changes) {
      final NameKey branch = c.getDest();
      try {
        threadScoper.scope(new Callable<Void>(){
          @Override
          public Void call() throws Exception {
            bgFactory.get().create(changeSet.changes).merge();
            return null;
          }
        }).call();
      } catch (Throwable e) {
        log.error("Merge attempt for " + branch + " failed", e);
      } finally {
        finish(changeSet);
      }
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
    final Set<Change> changes;
    HashSet<Branch.NameKey> touchedBranches;

    ChangeSet(Set<Change> changes) {
      this.changes = changes;
      this.touchedBranches = null;

      MessageDigest md = hasher;
      ByteBuffer b = ByteBuffer.allocate(4 * changes.size());

      for (Change c : changes) {
        md.update(b.putInt(c.getChangeId()).array());
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
      StringBuilder sb = new StringBuilder();
      for (Change c : changeSet.changes) {
        sb.append("(");
        sb.append(c.getId());
        sb.append(" ");
        sb.append(c.getDest());
        sb.append(")");
      }
      return "submit " + sb.toString();
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
      StringBuilder sb = new StringBuilder();
      for (Change c : changeSet.changes) {
        sb.append("(");
        sb.append(c.getId());
        sb.append(" ");
        sb.append(c.getDest());
        sb.append(")");
      }
      return "recheck " + sb.toString();
    }
  }
}
