// Copyright 2009 Google Inc.
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

package com.google.gerrit.git;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.server.GerritServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MergeQueue {
  private static final Logger log = LoggerFactory.getLogger(MergeQueue.class);
  private static ScheduledThreadPoolExecutor pool;
  private static final Map<Branch.NameKey, MergeEntry> active =
      new HashMap<Branch.NameKey, MergeEntry>();

  public static void terminate() {
    final ScheduledThreadPoolExecutor p = shutdown();
    if (p != null) {
      boolean isTerminated;
      do {
        try {
          isTerminated = p.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          isTerminated = false;
        }
      } while (!isTerminated);
    }
  }

  private static synchronized ScheduledThreadPoolExecutor shutdown() {
    final ScheduledThreadPoolExecutor p = pool;
    if (p != null) {
      p.shutdown();
      pool = null;
      return p;
    }
    return null;
  }

  public static void merge(final Branch.NameKey branch) {
    if (start(branch)) {
      try {
        mergeImpl(branch);
      } finally {
        finish(branch);
      }
    }
  }

  public static synchronized boolean start(final Branch.NameKey branch) {
    final MergeEntry e = active.get(branch);
    if (e == null) {
      // Let the caller attempt this merge, its the only one interested
      // in processing this branch right now.
      //
      active.put(branch, new MergeEntry(branch));
      return true;
    } else {
      // Request that the job queue handle this merge later.
      //
      e.needMerge = true;
      return false;
    }
  }

  public static synchronized void finish(final Branch.NameKey branch) {
    final MergeEntry e = active.get(branch);
    if (e == null) {
      // Not registered for a build? Shouldn't happen but ignore it.
      //
      return;
    }

    if (!e.needMerge) {
      // No additional merges are in progress, we can delete it.
      //
      active.remove(branch);
      return;
    }

    if (!e.jobScheduled) {
      // No job has been scheduled to execute this branch, but it needs
      // to run a merge again.
      //
      if (pool == null) {
        pool = new ScheduledThreadPoolExecutor(1);
      }

      e.jobScheduled = true;
      pool.schedule(new Runnable() {
        public void run() {
          unschedule(e);
          try {
            mergeImpl(e.dest);
          } finally {
            finish(e.dest);
          }
        }
      }, 0, TimeUnit.SECONDS);
    }
  }

  private static synchronized void unschedule(final MergeEntry e) {
    e.jobScheduled = false;
    e.needMerge = false;
  }

  private static void mergeImpl(final Branch.NameKey branch) {
    try {
      new MergeOp(GerritServer.getInstance(), branch).merge();
    } catch (Throwable e) {
      log.error("Merge attempt for " + branch + " failed", e);
    }
  }

  private static class MergeEntry {
    final Branch.NameKey dest;
    boolean needMerge;
    boolean jobScheduled;

    MergeEntry(final Branch.NameKey d) {
      dest = d;
    }
  }
}
