/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.projects.PutLabelInput;
import com.google.gerrit.extensions.common.LabelTypeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class PutLabel implements RestModifyView<ProjectResource, PutLabelInput> {

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public PutLabel(
      PermissionBackend permissionBackend,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
  }

  @Override
  public List<LabelTypeInfo> apply(ProjectResource resource, PutLabelInput input)
      throws AuthException, PermissionBackendException, BadRequestException {
    Project.NameKey project = resource.getNameKey();
    checkRequiredPermissions(project);

    ProjectConfig projectConfig = resource.getProjectState().getConfig();
    if (input == null
        || (input.adds.isEmpty() && input.updates.isEmpty() && input.removes.isEmpty())) {
      return getLabels(projectConfig);
    }
    checkInput(projectConfig.getLabelSections().keySet(), input);

    updateLabelConfigs(project, projectConfig, input);

    return getLabels(projectConfigFactory.create(project));
  }

  private static List<LabelTypeInfo> getLabels(ProjectConfig projectConfig) {
    return projectConfig.getLabelSections().values().stream()
        .map(LabelType::toLabelTypeInfo)
        .collect(Collectors.toList());
  }

  /**
   * Checks required permissions for putting labels.
   *
   * @param project the project where the label should be configured.
   * @throws AuthException if the user doesn't hold required permissions.
   * @throws PermissionBackendException if there is an error in {@link PermissionBackend}.
   */
  private void checkRequiredPermissions(Project.NameKey project)
      throws AuthException, PermissionBackendException {
    try {
      permissionBackend.currentUser().project(project).check(ProjectPermission.WRITE_CONFIG);
    } catch (AuthException e) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }
  }

  private void checkInput(Set<String> existingLabels, PutLabelInput labelInput)
      throws BadRequestException {
    Set<String> labelsToBeAdded = new HashSet<>();
    Set<String> labelsToBeUpdated = new HashSet<>();
    Set<String> labelsToBeRemoved = new HashSet<>();
    if (!labelInput.adds.isEmpty()) {
      labelInput.adds.forEach(input -> labelsToBeAdded.add(input.name));
    }
    if (!labelInput.updates.isEmpty()) {
      labelInput.updates.forEach(input -> labelsToBeUpdated.add(input.name));
    }
    if (!labelInput.removes.isEmpty()) {
      labelInput.removes.forEach(input -> labelsToBeRemoved.add(input.name));
    }

    // Makes sure there is no overlap in the input.
    SetView<String> addAndUpdate = Sets.intersection(labelsToBeAdded, labelsToBeUpdated);
    if (!addAndUpdate.isEmpty()) {
      throw new BadRequestException("cannot add and update the same label: " + addAndUpdate);
    }
    SetView<String> addAndRemove = Sets.intersection(labelsToBeAdded, labelsToBeRemoved);
    if (!addAndUpdate.isEmpty()) {
      throw new BadRequestException("cannot add and remove the same label: " + addAndRemove);
    }
    SetView<String> removeAndUpdate = Sets.intersection(labelsToBeRemoved, labelsToBeUpdated);
    if (!addAndUpdate.isEmpty()) {
      throw new BadRequestException("cannot remove and update the same label: " + removeAndUpdate);
    }

    // Makes sure all labels to be added don't exist.
    if (!existingLabels.isEmpty()) {
      SetView<String> addAndExisting = Sets.intersection(labelsToBeAdded, existingLabels);
      if (!addAndExisting.isEmpty()) {
        throw new BadRequestException("cannot add existing label: " + addAndExisting);
      }
    }

    // Makes sure all labels to be updated do exist.
    SetView<String> toBeUpdatedButNotExist = Sets.difference(labelsToBeUpdated, existingLabels);
    if (!toBeUpdatedButNotExist.isEmpty()) {
      throw new BadRequestException("cannot update non-existing label: " + toBeUpdatedButNotExist);
    }

    // Makes sure all labels to be removed do exist.
    SetView<String> toBeRemovedButNotExist = Sets.difference(labelsToBeRemoved, existingLabels);
    if (!toBeRemovedButNotExist.isEmpty()) {
      throw new BadRequestException("cannot remove non-existing label: " + toBeRemovedButNotExist);
    }
  }

  private void updateLabelConfigs(
      Project.NameKey project, ProjectConfig projectConfig, PutLabelInput putLabelInput) {
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(project)) {
      md.setMessage("Update project label configs");

      projectConfig.commit(md);
      projectCache.evict(project);
    } catch (RepositoryNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
