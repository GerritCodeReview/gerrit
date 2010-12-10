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


import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.Status;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class VisibleProjects extends Handler<List<ProjectData>> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager repoManager;
  private final ReviewDb db;

  private static final Logger log =
      LoggerFactory.getLogger(VisibleProjects.class);

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager repoManager, final CurrentUser user,
      final ReviewDb db) {
    this.projectControlFactory = projectControlFactory;
    this.repoManager = repoManager;
    this.db = db;
  }

  @Override
  public List<ProjectData> call() throws OrmException,
      RepositoryNotFoundException {
    final List<ProjectData> result = new ArrayList<ProjectData>();

    for (final Project p : db.projects().all().toList()) {
      boolean isEmpty = false;
      boolean canBeUpdated = false;
      boolean canBeDeleted = false;

      try {
        final ProjectControl c =
            projectControlFactory.controlFor(p.getNameKey());
        // Administrators users are also considered in method "isOwner".
        if (c.isVisible() || c.isOwner()) {
          if (c.isOwner() && (!c.getProjectState().isSpecialWildProject())) {
            // Verifies if the project has any refs in the repository.
            // If it has any change or any commit.
            // If true it is not an empty project so it cannot be deleted.
            try {
              final Repository repo = repoManager.openRepository(p.getNameKey());
              final Map<String, Ref> refs = repo.getAllRefs();
              repo.close();
              if (refs.isEmpty()) {
                isEmpty = true;
              } else {
                canBeUpdated = true;
              }
            } catch (RepositoryNotFoundException e) {
              log.error("Repository " + p.getName() + " was not found");
            }
          }

          if (c.getCurrentUser().isAdministrator()) {
            canBeDeleted = true;
          }

          if (c.getProject().getStatus().equals(Status.DELETED)
              || c.getProject().getStatus().equals(Status.PRUNE)) {
            if (canBeDeleted) {
              result.add(new ProjectData(p.getNameKey(), p.getDescription(),
                  isEmpty, canBeUpdated, canBeDeleted, p.getStatus()));
            }
          } else {
            result.add(new ProjectData(p.getNameKey(), p.getDescription(),
                isEmpty, canBeUpdated, canBeDeleted, p.getStatus()));
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }
    Collections.sort(result, new Comparator<ProjectData>() {
      public int compare(final ProjectData a, final ProjectData b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return result;
  }
}
