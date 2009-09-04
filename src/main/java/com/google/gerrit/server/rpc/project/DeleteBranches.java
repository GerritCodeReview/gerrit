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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class DeleteBranches extends Handler<Set<Branch.NameKey>> {
  private static final Logger log =
      LoggerFactory.getLogger(DeleteBranches.class);

  interface Factory {
    DeleteBranches create(@Assisted Project.NameKey name,
        @Assisted Set<Branch.NameKey> toRemove);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GerritServer gerritServer;
  private final ReplicationQueue replication;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final Set<Branch.NameKey> toRemove;

  @Inject
  DeleteBranches(final ProjectControl.Factory projectControlFactory,
      final GerritServer gerritServer, final ReplicationQueue replication,
      final ReviewDb db,

      @Assisted Project.NameKey name, @Assisted Set<Branch.NameKey> toRemove) {
    this.projectControlFactory = projectControlFactory;
    this.gerritServer = gerritServer;
    this.replication = replication;
    this.db = db;

    this.projectName = name;
    this.toRemove = toRemove;
  }

  @Override
  public Set<Branch.NameKey> call() throws NoSuchProjectException,
      RepositoryNotFoundException, OrmException {
    final ProjectControl projectControl =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);

    for (Branch.NameKey k : toRemove) {
      if (!projectName.equals(k.getParentKey())) {
        throw new IllegalArgumentException("All keys must be from same project");
      }
      if (!projectControl.canDeleteRef(k.get())) {
        throw new IllegalStateException("Cannot delete " + k.getShortName());
      }
    }

    final Set<Branch.NameKey> deleted = new HashSet<Branch.NameKey>();
    final Repository r = gerritServer.openRepository(projectName.get());
    try {
      for (final Branch.NameKey branchKey : toRemove) {
        final Branch b = db.branches().get(branchKey);
        if (b == null) {
          continue;
        }

        final RefUpdate.Result result;
        try {
          final RefUpdate u = r.updateRef(b.getName());
          u.setForceUpdate(true);
          result = u.delete();
        } catch (IOException e) {
          log.error("Cannot delete " + branchKey, e);
          continue;
        }

        switch (result) {
          case NEW:
          case NO_CHANGE:
          case FAST_FORWARD:
          case FORCED:
            db.branches().delete(Collections.singleton(b));
            deleted.add(branchKey);
            replication.scheduleUpdate(projectName, b.getName());
            break;

          case REJECTED_CURRENT_BRANCH:
            log.warn("Cannot delete " + branchKey + ": " + result.name());
            break;

          default:
            log.error("Cannot delete " + branchKey + ": " + result.name());
            break;
        }
      }
    } finally {
      r.close();
    }
    return deleted;
  }
}
