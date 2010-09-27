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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
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

/** This class performs the deletion of an empty (has no changes) project. */
public class DeleteProject extends Handler<List<NameKey>> {

  interface Factory {
    DeleteProject create(@Assisted List<NameKey> projectsList);
  }

  private static final Logger log =
      LoggerFactory.getLogger(DeleteProject.class);

  private final ProjectControl.Factory projectControlFactory;
  private final ReviewDb db;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final List<NameKey> projectsList;

  @Inject
  DeleteProject(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager repoManager,
      final ReplicationQueue replication,
      final ReviewDb db, final ProjectCache projectCache,
      @Assisted List<NameKey> projectsList) {
    this.projectControlFactory = projectControlFactory;
    this.repoManager = repoManager;
    this.replication = replication;
    this.projectsList = projectsList;
    this.db = db;
    this.projectCache = projectCache;
  }

  @Override
  public List<NameKey> call() {
    final List<NameKey> failedProjects = new ArrayList<NameKey>();

    for (NameKey p : projectsList) {
      try {
        final ProjectControl projectControl =
            projectControlFactory.controlFor(p);
        if (projectControl.isOwner()) {
          // Double Check if project is truly empty.
          final Repository r = repoManager.openRepository(p.get());
          Map<String, Ref> refs;
          refs = r.getAllRefs();
          r.close();

          if (refs.isEmpty()) {
            // Remove Git Repository.
            repoManager.deleteRepository(p.get());

            // Update parent relationship.
            for (final Project c : db.projects().getChildren(p.get())) {
              c.setParent(projectControl.getProject().getParent());
              db.projects().update(Collections.singleton(c));
              projectCache.evict(c);
            }

            // Remove all added RefRights.
            db.refRights().delete(db.refRights().byProject(p));

            // Remove all interests in this project.
            db.accountProjectWatches().delete(
                db.accountProjectWatches().byProject(p));

            // Remove project record.
            db.projects().deleteKeys(Collections.singleton(p));

            replication.replicateProjectDeletion(p);

            projectCache.evict(projectControl.getProject());
          } else {
            failedProjects.add(p);
            log.error(p.get() + " is not empty.");
          }
        } else {
          failedProjects.add(p);
          log.error("User has no rights to delete " + p.get());
        }
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
        log.error("Could not update/delete " + p.get() + "from database.", e);
      } catch (NoSuchProjectException e) {
        failedProjects.add(p);
        log.error("Project " + p.get() + " was not found.");
      }
    }
    return failedProjects;
  }
}
