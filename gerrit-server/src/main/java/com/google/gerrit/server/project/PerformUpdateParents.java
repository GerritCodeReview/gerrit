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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PerformUpdateParents {

  public interface Factory {
    PerformUpdateParents create();
  }

  private final ReviewDb db;
  private final ProjectCache projectCache;
  private final Project.NameKey wildProject;

  @Inject
  PerformUpdateParents(
      final ReviewDb db, final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wildProject) {
    this.db = db;
    this.projectCache = projectCache;
    this.wildProject = wildProject;
  }

  /**
   * Sets the given parent project as new parent for all given child projects.
   *
   * @param children the child projects for which the given parent project
   *        should be set as new parent
   * @param newParent the project which should be set as new parent, if
   *        <code>null</code> the wild project will be set as new parent
   * @throws OrmException
   * @throws UpdateParentsFailedException thrown in case setting the new parent
   *         has failed for any child project
   */
  public void updateParents(final Collection<ProjectControl> children,
      final ProjectControl newParent) throws OrmException,
      UpdateParentsFailedException {
    final StringBuilder err = new StringBuilder();
    final Set<Project.NameKey> grandParents = new HashSet<Project.NameKey>();
    Project.NameKey newParentKey;

    grandParents.add(wildProject);

    if (newParent != null) {
      newParentKey = newParent.getProject().getNameKey();

      // Catalog all grandparents of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      Project.NameKey gp = newParent.getProject().getParent();
      while (gp != null && grandParents.add(gp)) {
        final ProjectState s = projectCache.get(gp);
        if (s != null) {
          gp = s.getProject().getParent();
        } else {
          break;
        }
      }
    } else {
      // If no parent was selected, set to NULL to use the default.
      //
      newParentKey = null;
    }

    for (final ProjectControl pc : children) {
      final Project.NameKey key = pc.getProject().getNameKey();
      final String name = pc.getProject().getName();

      if (wildProject.equals(key)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        continue;
      }

      if (grandParents.contains(key)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (newParentKey != null ? newParentKey.get() : wildProject.get())
            + "'\n");
        continue;
      }

      final Project child = db.projects().get(key);
      if (child == null) {
        // Race condition? Its in the cache, but not the database.
        //
        err.append("error: Project '" + name + "' not found\n");
        continue;
      }

      child.setParent(newParentKey);
      db.projects().update(Collections.singleton(child));
    }

    // Invalidate all projects in cache since inherited rights were changed.
    //
    projectCache.evictAll();

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UpdateParentsFailedException(err.toString());
    }
  }
}
