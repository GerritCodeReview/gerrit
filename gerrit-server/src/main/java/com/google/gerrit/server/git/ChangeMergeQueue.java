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

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import com.jcraft.jsch.HostKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<Set<Branch.NameKey>, NormalizedLockRequest> requests =
      new HashMap<Set<Branch.NameKey>, NormalizedLockRequest>();
  private final Map<Branch.NameKey, NormalizedLockRequest> active =
      new HashMap<Branch.NameKey, NormalizedLockRequest>();

  private final WorkQueue workQueue;
  private final Provider<MergeOp.Factory> bgFactory;

  @Inject
  ChangeMergeQueue(final WorkQueue wq, Injector parent) {
    workQueue = wq;

    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindScope(RequestScoped.class, PerThreadRequestScope.REQUEST);
        install(new GerritRequestModule());

        bind(CurrentUser.class).to(IdentifiedUser.class);
        bind(IdentifiedUser.class).toProvider(new Provider<IdentifiedUser>() {
          @Override
          public IdentifiedUser get() {
            throw new OutOfScopeException("No user on merge thread");
          }
        });
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
    });
    bgFactory = child.getProvider(MergeOp.Factory.class);
  }

  @Override
  public void schedule(Set<Branch.NameKey> branches) {
    // Attempt to go now, as start will reschedule us if necessary
    if (start(branches)) {
      mergeImpl(branches);
    }
  }

  @Override
  public void merge(MergeOp.Factory mof, Set<Branch.NameKey> branches) {
    if (start(branches)) {
      mergeImpl(mof, branches);
    }
  }

  /** A merge can be started if we can acquire all the locks in order. If we can't
   * then we get rescheduled to try again later. This method should only be called
   * once. Use restart for rescheduled jobs. */
  private synchronized boolean start(final Set<Branch.NameKey> branches) {
    final NormalizedLockRequest req = new NormalizedLockRequest(branches);
    if (requests.get(branches) != null) {
      // whoa, another group composed of the exact same branches was requested
      // to be merged while we were in flight. This is super rare, so we'll
      // take the easy way out and reschedule it.
      reschedule(req, 2, TimeUnit.MINUTES);
      return false;
    }

    // Any other set of the same branches is necessarily excluded until we're
    // done
    requests.put(branches, req);

    if (req.requestAgainst(active)) {
      // Let the caller attempt this merge, its the only one interested
      // in processing these branches right now.
      //
      return true;
    } else {
      // Request that the job queue handle this merge later.
      //
      reschedule(req, 2, TimeUnit.MINUTES);
      return false;
    }
  }

  private synchronized boolean restart(final NormalizedLockRequest req) {
    if (req.requestAgainst(active)) {
      // Let the caller attempt this merge, its the only one interested
      // in processing these branches right now.
      //
      return true;
    } else {
      // Request that the job queue handle this merge later.
      //
      reschedule(req, 2, TimeUnit.MINUTES);
      return false;
    }
  }

  private synchronized void reschedule(final NormalizedLockRequest req, final long delay, final TimeUnit delayUnit) {
    final long now = System.currentTimeMillis();
    final long at = now + MILLISECONDS.convert(delay, delayUnit);

    workQueue.getDefaultQueue().schedule(req, now - at, MILLISECONDS);
    req.recheckAt = Math.max(at, req.recheckAt);
  }

  private synchronized void finish(final Set<Branch.NameKey> branches) {
    final NormalizedLockRequest req = requests.get(branches);
    if (req == null) {
      // Not registered? Shouldn't happen but ignore it.
      //
      return;
    }

    req.releaseAgainst(active);
    requests.remove(branches);
  }

  /** Precondition: all relevant locks have been acquired */
  private void mergeImpl(MergeOp.Factory opFactory, Set<Branch.NameKey> branches) {
    try {
      for (Branch.NameKey b : branches) {
        opFactory.create(b).merge();
      }
    } catch (Throwable e) {
      log.error("Merge attempt for " + branchNames(branches) + " failed", e);
    } finally {
      finish(branches);
    }
  }

  private void mergeImpl(Set<Branch.NameKey> branches) {
    try {
      PerThreadRequestScope ctx = new PerThreadRequestScope();
      PerThreadRequestScope old = PerThreadRequestScope.set(ctx);
      try {
        try {
          for (Branch.NameKey b : branches) {
            bgFactory.get().create(b).merge();
          }
        } finally {
          ctx.cleanup.run();
        }
      } finally {
        PerThreadRequestScope.set(old);
      }
    } catch (Throwable e) {
      log.error("Merge attempt for " + branchNames(branches) + " failed", e);
    } finally {
      finish(branches);
    }
  }

  private static String branchNames(Set<Branch.NameKey> branches) {
    StringBuilder ret = new StringBuilder();
    for (Branch.NameKey branch : branches) {
      ret.append(branch).append(", ");
    }
    return ret.reverse().deleteCharAt(0).deleteCharAt(0).reverse().toString();
  }

  /** Represents a sequence of branch locks that are requested simultaneously,
   * but which are automatically normalized to a known sequence and acquired in
   * order to prevent deadlocks.
   */
  private class NormalizedLockRequest implements Runnable {
    final Set<Branch.NameKey> targets;
    final List<Branch.NameKey> acquired;
    final List<Branch.NameKey> pending;
    long recheckAt;

    NormalizedLockRequest(final Set<Branch.NameKey> branches) {
      targets = branches;
      acquired = new ArrayList<Branch.NameKey>();
      pending = new ArrayList<Branch.NameKey>();

      final SortedSet<Branch.NameKey> normalized = new TreeSet<Branch.NameKey>(
          new Comparator<Branch.NameKey>() {
            @Override
            public int compare(Branch.NameKey b1, Branch.NameKey b2) {
              return (b1.getParentKey().get() + b1.get()).compareTo(b2.getParentKey().get() + b2.get());
            }
          });

      for (Branch.NameKey branch : normalized) {
        pending.add(branch);
      }
    }

    /** Returns true iff all the locks were acquired in order. Subsequent calls
     * continue to try to acquire pending locks where the previous call left off.
     */
    public boolean requestAgainst(Map<Branch.NameKey, NormalizedLockRequest> currentRequests) {
      for (Branch.NameKey b : pending) {
        if (currentRequests.get(b) == null) {
          acquired.add(b);
          pending.remove(0);
          currentRequests.put(b, this);
        }
        else {
          return false;
        }
      }
      return true;
    }

    // release all requests we have in the reverse order
    public void releaseAgainst(Map<Branch.NameKey, NormalizedLockRequest> currentRequests) {
      final List<Branch.NameKey> reverse = new ArrayList<Branch.NameKey>(acquired);
      Collections.reverse(reverse);
      for (Branch.NameKey b : reverse) {
        currentRequests.remove(b);
        pending.add(0, b);
      }
    }

    public void run() {
      if (restart(this)) {
        mergeImpl(targets);
      }
    }
  }
}
