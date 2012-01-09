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

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.ChangeUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Attempt to merge a change or a group of changes by merging the branch or
 * branches they represent.
 *
 * In order to prevent concurrency problems between groups of changes that
 * overlap in their branch requests, locks for the destination refs must be
 * acquired simultaneously or queued fairly. The bookkeeping used by this
 * {@link MergeQueue} is implemented by a map between refs and a list of
 * requests for that ref, satisfying the following invariants:
 *
 * <li>The list head for any given ref is the next in line to lock that ref</li>
 * <li>The union of all the refs represented by waiting requests is equal to the
 * union of all the refs currently existing as keys of the map; i.e. all refs
 * represented in merge requests are exactly those refs known to the map</li>
 * <li>A lock request is <em>satisfied</em> iff it is the head item of every
 * requested ref's list</li>
 * <li>The next request <code>R</code> that becomes unblocked as a result of a
 * satisfied request <code>S</code> finishing is defined as that request which
 * is next in line for the most contended lock affected by <code>S</code>,
 * assuming <code>R</code> would then be made satisfiable; otherwise, the next
 * request may be <code>null</code>. See {@link MergeRequest#next()} for an
 * illustrated example.</li>
 *
 */
public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<Branch.NameKey, List<MergeRequest>> active =
      new HashMap<Branch.NameKey, List<MergeRequest>>();
  private final Map<Change, RecheckJob> recheck = new HashMap<Change, RecheckJob>();

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
  public synchronized void merge(MergeOp.Factory mof, Change change) {
    merge(mof, Collections.singleton(change));
  }

  @Override
  public synchronized void merge(MergeOp.Factory mof, Set<Change> group) {
    final MergeRequest req = new MergeRequest(group);
    // If we can satisfy the request right now, then we don't have to wait
    if (req.acquireAll()) {
      mergeImpl(mof, req);
    }
  }

  @Override
  public synchronized void schedule(final Change change) {
    schedule(Collections.singleton(change));
  }

  @Override
  public synchronized void schedule(final Set<Change> group) {
    MergeRequest req = new MergeRequest(group);
    scheduleJob(req);
  }

  @Override
  public synchronized void recheckAfter(final Change change, final long delay,
      final TimeUnit delayUnit) {
    final long now = System.currentTimeMillis();
    final long at = now + MILLISECONDS.convert(delay, delayUnit);
    RecheckJob e = recheck.get(change);
    if (e == null) {
      e = new RecheckJob(change);
      workQueue.getDefaultQueue().schedule(e, now - at, MILLISECONDS);
      recheck.put(change, e);
    }
    e.recheckAt = Math.max(at, e.recheckAt);
  }

  private synchronized void finish(final MergeRequest req) {
    final MergeRequest next = req.next();
    if (next != null) {
      scheduleJob(next);
    }
    req.finish();
  }

  private synchronized void scheduleJob(final MergeRequest req) {
    if (req.acquireAll()) {
      workQueue.getDefaultQueue().schedule(req, 0, TimeUnit.SECONDS);
    }
    // If we couldn't request all the locks just now, we'll get picked up at
    // the earliest opportunity (i.e. when we become the next request after
    // a finished request) and no scheduling is necessary.
  }

  private void mergeImpl(MergeOp.Factory opFactory, final MergeRequest req) {
    // If only one ref matters, we can proceed as usual; otherwise, we
    // should ensure that the entire group is mergeable first.
    if (req.targets.size() == 1) {
      for (Branch.NameKey b : req.targets) {
        try {
          opFactory.create(b).merge();
        } catch (Throwable e) {
          log.error("Merge attempt for " + b.getShortName() + " failed", e);
        } finally {
          finish(req);
        }
      }
    } else {
      try {
        for (Change c : req.group) {
          ChangeUtil.testMerge(opFactory, c);
          if (!c.isMergeable()) {
            throw new MergeException("Group not mergeable due to change: " + c.getChangeId());
          }
        }

        for (Branch.NameKey b : req.targets) {
          opFactory.create(b).merge();
        }
      } catch (Throwable e) {
        log.error("Could not merge group with Group-Id: " + req.groupKey, e);
      } finally {
        finish(req);
      }
    }
  }

  private void mergeImpl(final MergeRequest req) {
    try {
      PerThreadRequestScope ctx = new PerThreadRequestScope();
      PerThreadRequestScope old = PerThreadRequestScope.set(ctx);
      try {
        try {
          mergeImpl(bgFactory.get(), req);
        } finally {
          ctx.cleanup.run();
        }
      } finally {
        PerThreadRequestScope.set(old);
      }
    } catch (Throwable e) {
      log.error("Rescheduled merge attempt " + req + " failed", e);
    }
  }

  private synchronized void recheck(final RecheckJob e) {
    final long remainingDelay = e.recheckAt - System.currentTimeMillis();
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
      MergeRequest req = new MergeRequest(Collections.singleton(e.change));
      scheduleJob(req);
    }
  }

  // A set of branches that we are requesting be merged
  private class MergeRequest implements Runnable {
    final Set<Branch.NameKey> targets;
    final SortedSet<Change> group;
    Change.GroupKey groupKey;
    boolean satisfied;

    MergeRequest(final Set<Change> g) {
      // Crude emulation of topological sort (assumes lower-id changes are
      // ancestors, but this is not strictly true)
      group = new TreeSet<Change>(new Comparator<Change>() {
        @Override
        public int compare(Change c1, Change c2) {
          return new Integer(c1.getId().get()).compareTo(c2.getId().get());
        }
      });
      for (Change c : g) {
        group.add(c);
        if (groupKey == null) {
          groupKey = c.getGroupKey();
        }
      }
      targets = new HashSet<Branch.NameKey>();
      for (Change c : group) {
        targets.add(c.getDest());
      }
      satisfied = false;

      // Declare/queue our requests
      for (Branch.NameKey b : targets) {
        final List<MergeRequest> currentReqs = active.get(b);
        if (currentReqs == null) {
          final List<MergeRequest> queue = new ArrayList<MergeRequest>();
          queue.add(this);
          active.put(b, queue);
        } else {
          currentReqs.add(this);
        }
      }
    }

    public boolean acquireAll() {
      for (Branch.NameKey b : targets) {
        final List<MergeRequest> reqs = active.get(b);
        if (reqs == null || reqs.get(0).equals(this)) {
          satisfied = false;
          return false;
        }
      }

      satisfied = true;
      return true;
    }

    /**
     * Returns the next MergeRequest (if any) that becomes unblocked as a result
     * of us finishing.
     *
     * Consider the following hypothetical sequence of events seen by the
     * request queue:
     *
     * <pre>
     *                        Refs
     *           A    B    C    D    E    F    G
     * Req X     X    X    X    X
     * Req Y                         Y    Y    Y
     * Req Z               Z    Z    Z
     * Req O                    O         O
     * Req P                    P
     * Req Q               Q
     * Req R          R
     * </pre>
     *
     * Then the request lists will look like this:
     *
     * <pre>
     *                        Refs
     *           A    B    C    D    E    F    G
     *           X    X    X    X    Y    Y    Y
     *                R    Z    Z    Z    O
     *                     Q    O
     *                          P
     * </pre>
     *
     * Assuming the requests came in the order listed above (X, Y, Z, O, P, Q,
     * R), and the current request that just finished is X, then the only X.next
     * candidates will be Z and R. Z will be tried first since it is on the most
     * contended Ref (D). Z is not guaranteed, however, since Y must have
     * finished first independently, allowing Z to be satisfied (Z is blocking
     * on Y for the Ref E). In this latter case, we unblock R and return R as
     * X.next, and let Z be returned by Y.next when Y finishes.
     */
    public MergeRequest next() {
      // Sort refs by order of contention
      final SortedSet<Branch.NameKey> refs = new TreeSet<Branch.NameKey>(
          new Comparator<Branch.NameKey>() {
            @Override
            public int compare(Branch.NameKey b1, Branch.NameKey b2) {
              // Sorts by list length DESC
              return new Integer(active.get(b2).size()).compareTo(active.get(b1).size());
            }
          });

      for (Branch.NameKey b : targets) {
        refs.add(b);
      }

      for (Branch.NameKey b : refs) {
        final List<MergeRequest> reqs = active.get(b);
        if (reqs.size() > 1) {
          final MergeRequest candidate = reqs.get(1); // reqs.get(0) is ours
          if (candidate.acquireAll()) {
            return candidate;
          }
        }
      }
      return null;
    }

    public void finish() {
      if (!satisfied) {
        return;
      }

      for (Branch.NameKey b : targets) {
        active.get(b).remove(0);
      }
      satisfied = false;
    }

    @Override
    public void run() {
      mergeImpl(this);
    }

    @Override
    public String toString() {
      if (groupKey != null) {
        return "submit " + groupKey;
      } else {
        return "submit " + targets.iterator().next().getShortName();
      }
    }

    @Override
    public boolean equals(Object other) {
      if (other.getClass() != MergeRequest.class) {
        return false;
      }
      return group.equals((MergeRequest)other);
    }
  }

  private class RecheckJob implements Runnable {
    final Change change;
    long recheckAt;

    RecheckJob(final Change c) {
      change = c;
    }

    @Override
    public void run() {
      recheck(this);
    }

    @Override
    public String toString() {
      final Project.NameKey project = change.getDest().getParentKey();
      return "recheck " + project.get() + " " + change.getDest().getShortName();
    }
  }
}
