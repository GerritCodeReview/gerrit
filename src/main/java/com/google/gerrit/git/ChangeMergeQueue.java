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

package com.google.gerrit.git;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ChangeMergeQueue implements MergeQueue {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeMergeQueue.class);

  private final Map<Branch.NameKey, MergeEntry> active =
      new HashMap<Branch.NameKey, MergeEntry>();

  private final GerritServer server;
  private final SchemaFactory<ReviewDb> schema;
  private final ReplicationQueue replication;

  @Inject
  ChangeMergeQueue(final GerritServer gs, final SchemaFactory<ReviewDb> sf,
      final ReplicationQueue rq) {
    server = gs;
    schema = sf;
    replication = rq;
  }

  @Override
  public void merge(final Branch.NameKey branch) {
    if (start(branch)) {
      try {
        mergeImpl(branch);
      } finally {
        finish(branch);
      }
    }
  }

  private synchronized boolean start(final Branch.NameKey branch) {
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

  @Override
  public synchronized void schedule(final Branch.NameKey branch) {
    MergeEntry e = active.get(branch);
    if (e == null) {
      e = new MergeEntry(branch);
      active.put(branch, e);
    }
    e.needMerge = true;
    scheduleJob(e);
  }

  private synchronized void finish(final Branch.NameKey branch) {
    final MergeEntry e = active.get(branch);
    if (e == null) {
      // Not registered? Shouldn't happen but ignore it.
      //
      return;
    }

    if (!e.needMerge) {
      // No additional merges are in progress, we can delete it.
      //
      active.remove(branch);
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
      WorkQueue.schedule(new Runnable() {
        public void run() {
          unschedule(e);
          try {
            mergeImpl(e.dest);
          } finally {
            finish(e.dest);
          }
        }

        @Override
        public String toString() {
          final Branch.NameKey branch = e.dest;
          final Project.NameKey project = branch.getParentKey();
          return "submit " + project.get() + " " + branch.getShortName();
        }
      }, 0, TimeUnit.SECONDS);
    }
  }

  private synchronized void unschedule(final MergeEntry e) {
    e.jobScheduled = false;
    e.needMerge = false;
  }

  private void mergeImpl(final Branch.NameKey branch) {
    try {
      new MergeOp(server, schema, replication, branch).merge();
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
