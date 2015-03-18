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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Singleton
public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<List<Change>, MergeEntry> active = new HashMap<>();
  private final Map<List<Change>, RecheckJob> recheck = new HashMap<>();

  private final WorkQueue workQueue;
  private final Provider<MergeOp.Factory> bgFactory;
  private final PerThreadRequestScope.Scoper threadScoper;

  @Inject
  ChangeMergeQueue(final WorkQueue wq, Injector parent) {
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
    bgFactory = child.getProvider(MergeOp.Factory.class);
    threadScoper = child.getInstance(PerThreadRequestScope.Scoper.class);
  }

  @Override
  public void merge(List<Change> changes) {
    if (start(changes)) {
      mergeImpl(changes);
    }
  }

  private synchronized boolean start(List<Change> changes) {
    final MergeEntry e = active.get(changes);
    if (e == null) {
      // Let the caller attempt this merge, its the only one interested
      // in processing this branch right now.
      //
      active.put(changes, new MergeEntry(changes));
      return true;
    } else {
      // Request that the job queue handle this merge later.
      //
      e.needMerge = true;
      return false;
    }
  }

  @Override
  public synchronized void schedule(final List<Change> changes) {
    MergeEntry e = active.get(changes);
    if (e == null) {
      e = new MergeEntry(changes);
      active.put(changes, e);
      e.needMerge = true;
      scheduleJob(e);
    } else {
      e.needMerge = true;
    }
  }

  @Override
  public synchronized void recheckAfter(List<Change> changes,
      final long delay, final TimeUnit delayUnit) {
    final long now = TimeUtil.nowMs();
    final long at = now + MILLISECONDS.convert(delay, delayUnit);
    RecheckJob e = recheck.get(changes);
    if (e == null) {
      e = new RecheckJob(changes);
      workQueue.getDefaultQueue().schedule(e, now - at, MILLISECONDS);
      recheck.put(changes, e);
    }
    e.recheckAt = Math.max(at, e.recheckAt);
  }

  private synchronized void finish(List<Change> changes) {
    final MergeEntry e = active.get(changes);
    if (e == null) {
      // Not registered? Shouldn't happen but ignore it.
      //
      return;
    }

    if (!e.needMerge) {
      // No additional merges are in progress, we can delete it.
      //
      active.remove(changes);
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

  private void mergeImpl(final List<Change> changes) {
    // TODO(sbeller): We need to see if we can parallelize here
    for (final Change c : changes) {
      final NameKey branch = c.getDest();
      try {
        threadScoper.scope(new Callable<Void>(){
          @Override
          public Void call() throws Exception {
            bgFactory.get().create(branch).merge(changes);
            return null;
          }
        }).call();
      } catch (Throwable e) {
        log.error("Merge attempt for " + branch + " failed", e);
      } finally {
        finish(changes);
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
      schedule(e.changes);
    }
  }

  private class MergeEntry implements Runnable {
    final List<Change> changes;
    boolean needMerge;
    boolean jobScheduled;

    MergeEntry(final List<Change> changes) {
      this.changes = changes;
    }

    @Override
    public void run() {
      unschedule(this);
      mergeImpl(changes);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Change c : changes) {
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
    final List<Change> changes;
    long recheckAt;

    RecheckJob(final List<Change> changes) {
      this.changes = changes;
    }

    @Override
    public void run() {
      recheck(this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Change c : changes) {
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
