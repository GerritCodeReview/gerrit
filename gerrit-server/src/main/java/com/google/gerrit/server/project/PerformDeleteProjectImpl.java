// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PerformDeleteProjectImpl implements PerformDeleteProject {
  public interface Factory {
    PerformDeleteProjectImpl create(@Assisted List<NameKey> projectsList);
  }

  private static final Logger log =
      LoggerFactory.getLogger(PerformDeleteProjectImpl.class);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final List<NameKey> projectsList;

  @Inject
  PerformDeleteProjectImpl(final GitRepositoryManager repoManager,
      final ReplicationQueue replication, final SchemaFactory<ReviewDb> sf,
      @Assisted List<NameKey> projectsList) {
    this.repoManager = repoManager;
    this.replication = replication;
    this.projectsList = projectsList;
    this.schemaFactory = sf;
  }

  @Override
  public List<NameKey> deleteProjects() {
    final List<NameKey> failedProjects = new ArrayList<NameKey>();

    try {
      if (db == null) {
        db = schemaFactory.open();
      }

      for (NameKey p : projectsList) {
        try {
          // Double Check if project is truly empty.
          final Repository r = repoManager.openRepository(p);
          Map<String, Ref> refs;
          refs = r.getAllRefs();
          r.close();

          if (!refs.isEmpty()) {
            deleteNonEmptyProject(p);
          }

          // Remove Git Repository.
          repoManager.deleteRepository(p.get());

          // Update parent relationship.
          for (final Project c : db.projects().getChildren(p.get())) {
            c.setParent(db.projects().get(p).getParent());
            db.projects().update(Collections.singleton(c));
          }

          // Remove all added RefRights.
          db.refRights().delete(db.refRights().byProject(p));

          // Remove all interests in this project.
          db.accountProjectWatches().delete(
              db.accountProjectWatches().byProject(p));

          // Remove project record.
          db.projects().deleteKeys(Collections.singleton(p));

          replication.replicateProjectDeletion(p);
        } catch (RepositoryNotFoundException e) {
          failedProjects.add(p);
          log.error("Repository " + p.get() + " was not found");
        } catch (IOException e) {
          failedProjects.add(p);
          log.error(e.getMessage());
        } catch (SecurityException e) {
          failedProjects.add(p);
          log.error(p.get() + " repository deletion is not allowed.", e);
        } catch (OrmException e) {
          failedProjects.add(p);
          log
              .error("Could not update/delete " + p.get() + " from database.",
                  e);
        }
      }
    } catch (OrmException e) {
      log.error("Could not open database.");
    } finally {
      db.close();
      db = null;
    }

    return failedProjects;
  }

  @SuppressWarnings("deprecation")
  private void deleteNonEmptyProject(final NameKey projectName)
      throws OrmException {
    // Remove data associated with this project.
    for (Change c : db.changes().byProject(projectName)) {
      final Transaction txn = db.beginTransaction();

      db.accountPatchReviews().delete(
          db.accountPatchReviews().byChange(c.getId()), txn);

      db.patchComments().delete(db.patchComments().byChange(c.getId()), txn);
      db.patchSetAncestors().delete(db.patchSetAncestors().byChange(c.getId()),
          txn);
      db.patchSetApprovals().delete(db.patchSetApprovals().byChange(c.getId()),
          txn);
      db.patchSets().delete(db.patchSets().byChange(c.getId()), txn);

      db.starredChanges().delete(db.starredChanges().byChange(c.getId()), txn);
      db.trackingIds().delete(db.trackingIds().byChange(c.getId()), txn);

      db.changeMessages().delete(db.changeMessages().byChange(c.getId()), txn);
      db.changes().delete(db.changes().byProject(projectName), txn);

      txn.commit();
    }
  }
}
