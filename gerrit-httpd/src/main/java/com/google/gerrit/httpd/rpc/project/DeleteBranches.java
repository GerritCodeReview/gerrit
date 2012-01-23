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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class DeleteBranches extends Handler<Set<Branch.NameKey>> {
  private static final Logger log =
      LoggerFactory.getLogger(DeleteBranches.class);

  interface Factory {
    DeleteBranches create(@Assisted Project.NameKey name,
        @Assisted Set<Branch.NameKey> toRemove);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final IdentifiedUser identifiedUser;
  private final ChangeHooks hooks;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final Set<Branch.NameKey> toRemove;

  @Inject
  DeleteBranches(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager repoManager,
      final ReplicationQueue replication,
      final IdentifiedUser identifiedUser,
      final ChangeHooks hooks,
      final ReviewDb db,

      @Assisted Project.NameKey name, @Assisted Set<Branch.NameKey> toRemove) {
    this.projectControlFactory = projectControlFactory;
    this.repoManager = repoManager;
    this.replication = replication;
    this.identifiedUser = identifiedUser;
    this.hooks = hooks;
    this.db = db;

    this.projectName = name;
    this.toRemove = toRemove;
  }

  @Override
  public Set<Branch.NameKey> call() throws NoSuchProjectException,
      RepositoryNotFoundException, OrmException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    final Iterator<Branch.NameKey> branchIt = toRemove.iterator();
    while (branchIt.hasNext()) {
      final Branch.NameKey k = branchIt.next();
      if (!projectName.equals(k.getParentKey())) {
        throw new IllegalArgumentException("All keys must be from same project");
      }
      if (!projectControl.controlForRef(k).canDelete()) {
        throw new IllegalStateException("Cannot delete " + k.getShortName());
      }

      if (!db.changes().byBranchOpenAll(k).toList().isEmpty()) {
        branchIt.remove();
      }
    }

    final Set<Branch.NameKey> deleted = new HashSet<Branch.NameKey>();
    final Repository r = repoManager.openRepository(projectName);
    try {
      for (final Branch.NameKey branchKey : toRemove) {
        final String refname = branchKey.get();
        final RefUpdate.Result result;
        final RefUpdate u;
        try {
          u = r.updateRef(refname);
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
            deleted.add(branchKey);
            replication.scheduleUpdate(projectName, refname);
            hooks.doRefUpdatedHook(branchKey, u, identifiedUser.getAccount());
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
