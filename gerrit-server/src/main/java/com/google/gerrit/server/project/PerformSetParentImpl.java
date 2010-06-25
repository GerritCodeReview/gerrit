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

import com.google.gerrit.common.errors.OperationNotExecutedException;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Common class that holds the code to set inheritance between projects */
public class PerformSetParentImpl implements PerformSetParent {

  public interface Factory {
    PerformSetParentImpl create(NameKey parentNameKey,
        List<Project.NameKey> childProjects);
  }

  private final ProjectCache projectCache;
  private final ReviewDb db;

  private NameKey parentNameKey;
  private final List<Project.NameKey> childProjects;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Inject
  PerformSetParentImpl(final ProjectCache projectCache, final ReviewDb db,
      @Assisted final NameKey parentNameKey,
      @Assisted List<Project.NameKey> childProjects) {
    this.db = db;
    this.projectCache = projectCache;

    this.parentNameKey = parentNameKey;
    this.childProjects = childProjects;
  }

  @Override
  public void setParent() throws OrmException, NoSuchProjectException,
      OperationNotExecutedException {
    final StringBuilder err = new StringBuilder();

    final Set<Project.NameKey> grandParents = new HashSet<Project.NameKey>();

    if (parentNameKey.equals(wildProject)) {
      parentNameKey = null;
    }

    grandParents.add(wildProject);

    // If no parent was selected (parent == null), use the default.
    if (parentNameKey != null) {
      final ProjectState state = projectCache.get(parentNameKey);
      Project.NameKey grandParent = null;

      if (state != null) {
        grandParent = state.getProject().getParent();
      } else {
        throw new NoSuchProjectException(parentNameKey);
      }

      // Catalog all grandparents of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      while (grandParent != null && !grandParent.equals(wildProject)) {
        grandParents.add(grandParent);
        final ProjectState s = projectCache.get(grandParent);
        if (s != null) {
          grandParent = s.getProject().getParent();
        } else {
          break;
        }
      }
    }

    int numberOfFailedProjects = 0;
    for (final Project.NameKey pc : childProjects) {
      final String name = pc.get();

      if (wildProject.equals(pc)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        numberOfFailedProjects++;
        continue;
      }

      if (grandParents.contains(pc) || pc.equals(parentNameKey)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (parentNameKey != null ? parentNameKey.get() : wildProject.get())
            + "'\n");
        numberOfFailedProjects++;
        continue;
      }

      final Project child = db.projects().get(pc);
      if (child == null) {
        // Race condition? Its in the cache, but not the database.
        //
        err.append("error: Project '" + name + "' not found\n");
        numberOfFailedProjects++;
        continue;
      }

      child.setParent(parentNameKey);
      db.projects().update(Collections.singleton(child));
    }

    // Invalidate all projects in cache since inherited rights were changed.
    //
    projectCache.evictAll();

    // All projects failed to have a parent set
    if (numberOfFailedProjects == childProjects.size()
        && childProjects.size() > 1) {
      err.append("Set parent failed for all projects.");
    }

    if (err.length() > 0) {
      throw new OperationNotExecutedException(err.toString());
    }
  }
}
