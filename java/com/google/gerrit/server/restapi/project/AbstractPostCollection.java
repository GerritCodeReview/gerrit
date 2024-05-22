// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.AbstractBatchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Base class for a rest API batch update. */
public abstract class AbstractPostCollection<
        TId,
        TResource extends RestResource,
        TItemInput,
        TBatchInput extends AbstractBatchInput<TItemInput>>
    implements RestCollectionModifyView<ProjectResource, TResource, TBatchInput> {
  private final Provider<CurrentUser> user;
  private final RepoMetaDataUpdater updater;

  public AbstractPostCollection(RepoMetaDataUpdater updater, Provider<CurrentUser> user) {
    this.user = user;
    this.updater = updater;
  }

  @Override
  public Response<?> apply(ProjectResource rsrc, TBatchInput input)
      throws AuthException, UnprocessableEntityException, PermissionBackendException, IOException,
          ConfigInvalidException, BadRequestException, ResourceConflictException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    if (input == null) {
      return Response.ok("");
    }

    try (var configUpdater =
        updater.configUpdater(rsrc.getNameKey(), input.commitMessage, defaultCommitMessage())) {
      ProjectConfig config = configUpdater.getConfig();
      if (updateProjectConfig(config, input)) {
        configUpdater.commitConfigUpdate();
      }
    }
    return Response.ok("");
  }

  public boolean updateProjectConfig(ProjectConfig config, AbstractBatchInput<TItemInput> input)
      throws UnprocessableEntityException, ResourceConflictException, BadRequestException {
    boolean configChanged = false;
    if (input.delete != null && !input.delete.isEmpty()) {
      for (String name : input.delete) {
        if (Strings.isNullOrEmpty(name)) {
          throw new BadRequestException("The delete property contains null or empty name");
        }
        deleteItem(config, name.trim());
      }
      configChanged = true;
    }
    if (input.create != null && !input.create.isEmpty()) {
      for (TItemInput labelInput : input.create) {
        if (labelInput == null) {
          throw new BadRequestException("The create property contains a null item");
        }
        createItem(config, labelInput);
      }
      configChanged = true;
    }
    if (input.update != null && !input.update.isEmpty()) {
      for (var e : input.update.entrySet()) {
        if (e.getKey() == null) {
          throw new BadRequestException("The update property contains a null key");
        }
        if (e.getValue() == null) {
          throw new BadRequestException("The update property contains a null value");
        }
        configChanged |= updateItem(config, e.getKey().trim(), e.getValue());
      }
    }
    return configChanged;
  }

  /** Provides default commit message when user doesn't specify one in the input. */
  public abstract String defaultCommitMessage();

  protected abstract boolean updateItem(ProjectConfig config, String name, TItemInput resource)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException;

  protected abstract void createItem(ProjectConfig config, TItemInput resource)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException;

  protected abstract void deleteItem(ProjectConfig config, String name)
      throws BadRequestException, ResourceConflictException, UnprocessableEntityException;
}
