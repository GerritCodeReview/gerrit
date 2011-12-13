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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public class UpdateParents {

  public interface Factory {
    UpdateParents create(final Collection<Project> children,
        @Nullable final Project newParent);
  }

  private static final Logger log = LoggerFactory
      .getLogger(UpdateParents.class);

  private final ProjectCache projectCache;
  private final AllProjectsName allProjectsName;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final IdentifiedUser currentUser;

  private final Collection<Project> children;
  private final Project newParent;

  @Inject
  UpdateParents(final ProjectCache projectCache,
      final AllProjectsName allProjectsName,
      final MetaDataUpdate.User metaDataUpdateFactory,
      final IdentifiedUser currentUser,
      final @Assisted Collection<Project> children,
      final @Nullable @Assisted Project newParent) {
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.currentUser = currentUser;
    this.children = children;
    this.newParent = newParent;
  }

  /**
   * Sets the given parent project as new parent for all given child projects.
   *
   * @param children the child projects for which the given parent project
   *        should be set as new parent
   * @param newParent the project which should be set as new parent, if
   *        <code>null</code> the wild project will be set as new parent
   * @throws UpdateParentsFailedException thrown if updating the parent project
   *         fails for any child project. It is ensured that all child projects
   *         that can be updated without problems are updated, even if this
   *         exception is thrown.
   */
  public void updateParents() throws UpdateParentsFailedException {
    if (!currentUser.getCapabilities().canAdministrateServer()) {
      throw new UpdateParentsFailedException("Not a Gerrit administrator");
    }

    final StringBuilder err = new StringBuilder();
    final Set<Project.NameKey> grandParents = new HashSet<Project.NameKey>();

    grandParents.add(allProjectsName);

    Project.NameKey newParentKey = null;
    if (newParent != null) {
      newParentKey = newParent.getNameKey();

      // Catalog all grandparents of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      Project.NameKey gp = newParent.getParent();
      while (gp != null && grandParents.add(gp)) {
        final ProjectState s = projectCache.get(gp);
        if (s != null) {
          gp = s.getProject().getParent();
        } else {
          break;
        }
      }
    }

    for (final Project childProject : children) {
      final String name = childProject.getName();
      final Project.NameKey nameKey = childProject.getNameKey();

      if (allProjectsName.equals(nameKey)) {
        // Don't allow the wild card project to have a parent.
        //
        err.append("error: Cannot set parent of '" + name + "'\n");
        continue;
      }

      if (grandParents.contains(nameKey) || nameKey.equals(newParentKey)) {
        // Try to avoid creating a cycle in the parent pointers.
        //
        err.append("error: Cycle exists between '" + name + "' and '"
            + (newParentKey != null ? newParentKey.get() : allProjectsName.get())
            + "'\n");
        continue;
      }

      try {
        MetaDataUpdate md = metaDataUpdateFactory.create(nameKey);
        try {
          ProjectConfig config = ProjectConfig.read(md);
          config.getProject().setParentName(newParentKey);
          md.setMessage("Inherit access from "
              + (newParentKey != null ? newParentKey.get() : allProjectsName.get()) + "\n");
          if (!config.commit(md)) {
            err.append("error: Could not update project " + name + "\n");
          }
        } finally {
          md.close();
        }
      } catch (RepositoryNotFoundException notFound) {
        err.append("error: Project " + name + " not found\n");
      } catch (IOException e) {
        final String msg = "Cannot update project " + name;
        log.error(msg, e);
        err.append("error: " + msg + "\n");
      } catch (ConfigInvalidException e) {
        final String msg = "Cannot update project " + name;
        log.error(msg, e);
        err.append("error: " + msg + "\n");
      }

      projectCache.evict(childProject);
    }

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UpdateParentsFailedException(err.toString());
    }
  }
}
