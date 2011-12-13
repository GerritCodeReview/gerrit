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

import com.google.gerrit.common.data.UpdateParentsResult;
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
  private final Project.NameKey newParentKey;
  private final Set<Project.NameKey> ancestors;

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

    ancestors = new HashSet<Project.NameKey>();
    ancestors.add(allProjectsName);

    if (newParent != null) {
      newParentKey = newParent.getNameKey();

      // Catalog all ancestors of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      Project.NameKey ancestor = newParent.getParent();
      while (ancestor != null && ancestors.add(ancestor)) {
        final ProjectState s = projectCache.get(ancestor);
        if (s != null) {
          ancestor = s.getProject().getParent();
        } else {
          break;
        }
      }
    } else {
      newParentKey = allProjectsName;
    }
  }

  /**
   * Sets the given parent project as new parent for all given child projects.
   *
   * @param children the child projects for which the given parent project
   *        should be set as new parent
   * @param newParent the project which should be set as new parent, if
   *        <code>null</code> the wild project will be set as new parent
   * @return result of the parent update that contains information about errors
   *         that occurred during the update
   */
  public UpdateParentsResult updateParents() {
    final UpdateParentsResult result = new UpdateParentsResult();

    if (!currentUser.getCapabilities().canAdministrateServer()) {
      for (final Project childProject : children) {
        result.addError(new UpdateParentsResult.Error(
            UpdateParentsResult.Error.Type.UPDATE_NOT_PERMITTED,
            childProject.getNameKey(), newParentKey));
      }
      return result;
    }

    for (final Project childProject : children) {
      if (!validateParentUpdate(result, childProject)) {
        continue;
      }

      final Project.NameKey name = childProject.getNameKey();
      try {
        MetaDataUpdate md =
            metaDataUpdateFactory.create(childProject.getNameKey());
        try {
          ProjectConfig config = ProjectConfig.read(md);
          config.getProject().setParentName(newParentKey);
          md.setMessage("Inherit access from " + newParentKey.get() + "\n");
          if (!config.commit(md)) {
            result.addError(new UpdateParentsResult.Error(
                UpdateParentsResult.Error.Type.PROJECT_UPDATE_FAILED, name,
                newParentKey));
          }
        } finally {
          md.close();
        }
      } catch (RepositoryNotFoundException notFound) {
        result.addError(new UpdateParentsResult.Error(
            UpdateParentsResult.Error.Type.PROJECT_NOT_FOUND, name,
            newParentKey));
      } catch (IOException e) {
        final UpdateParentsResult.Error error =
            (new UpdateParentsResult.Error(
                UpdateParentsResult.Error.Type.PROJECT_UPDATE_FAILED, name,
                newParentKey));
        log.error(error.toString(), e);
        result.addError(error);
      } catch (ConfigInvalidException e) {
        final UpdateParentsResult.Error error =
            (new UpdateParentsResult.Error(
                UpdateParentsResult.Error.Type.PROJECT_UPDATE_FAILED, name,
                newParentKey));
        log.error(error.toString(), e);
        result.addError(error);
      }

      projectCache.evict(childProject);
    }

    return result;
  }

  /**
   * Checks if for all child projects the parent project can be updated.
   *
   * @return potential result of the parent update that contains information
   *         about errors that would occur during the update
   */
  public UpdateParentsResult validateParentUpdate() {
    final UpdateParentsResult result = new UpdateParentsResult();
    for (final Project childProject : children) {
      validateParentUpdate(result, childProject);
    }
    return result;
  }

  private boolean validateParentUpdate(final UpdateParentsResult result,
      final Project childProject) {
    final Project.NameKey name = childProject.getNameKey();

    if (allProjectsName.equals(name)) {
      result.addError(new UpdateParentsResult.Error(
          UpdateParentsResult.Error.Type.PARENT_CANNOT_BE_SET, name,
          newParentKey));
      return false;
    }

    if (ancestors.contains(name) || name.equals(newParentKey)) {
      result.addError(new UpdateParentsResult.Error(
          UpdateParentsResult.Error.Type.CYCLE_EXISTS, name, newParentKey));
      return false;
    }

    return true;
  }
}
