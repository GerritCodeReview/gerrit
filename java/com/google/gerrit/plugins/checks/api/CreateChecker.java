// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerJson;
import com.google.gerrit.plugins.checks.CheckerName;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckerUrl;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateChecker
    implements RestCollectionModifyView<TopLevelResource, CheckerResource, CheckerInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final CheckerJson checkerJson;
  private final AdministrateCheckersPermission permission;
  private final ProjectCache projectCache;

  @Inject
  public CreateChecker(
      PermissionBackend permissionBackend,
      @UserInitiated Provider<CheckersUpdate> checkersUpdate,
      CheckerJson checkerJson,
      AdministrateCheckersPermission permission,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.checkersUpdate = checkersUpdate;
    this.checkerJson = checkerJson;
    this.permission = permission;
    this.projectCache = projectCache;
  }

  @Override
  public Response<CheckerInfo> apply(TopLevelResource parentResource, CheckerInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          OrmDuplicateKeyException {
    permissionBackend.currentUser().check(permission);

    if (input == null) {
      input = new CheckerInput();
    }

    String name = CheckerName.clean(input.name);
    if (name.isEmpty()) {
      throw new BadRequestException("name is required");
    }
    Project.NameKey repository = resolveRepository(input.repository);

    String checkerUuid = CheckerUuid.make(name);
    CheckerCreation.Builder checkerCreationBuilder =
        CheckerCreation.builder()
            .setCheckerUuid(checkerUuid)
            .setName(name)
            .setRepository(repository);
    CheckerUpdate.Builder checkerUpdateBuilder = CheckerUpdate.builder();
    if (input.description != null && !input.description.trim().isEmpty()) {
      checkerUpdateBuilder.setDescription(input.description.trim());
    }
    if (input.url != null) {
      checkerUpdateBuilder.setUrl(CheckerUrl.clean(input.url));
    }
    if (input.status != null) {
      checkerUpdateBuilder.setStatus(input.status);
    }
    if (input.blockingConditions != null) {
      checkerUpdateBuilder.setBlockingConditions(
          ImmutableSortedSet.copyOf(input.blockingConditions));
    }
    Checker checker =
        checkersUpdate
            .get()
            .createChecker(checkerCreationBuilder.build(), checkerUpdateBuilder.build());
    return Response.created(checkerJson.format(checker));
  }

  private Project.NameKey resolveRepository(String repository)
      throws BadRequestException, UnprocessableEntityException, IOException {
    if (repository == null || repository.trim().isEmpty()) {
      throw new BadRequestException("repository is required");
    }

    ProjectState projectState = projectCache.checkedGet(new Project.NameKey(repository.trim()));
    if (projectState == null) {
      throw new UnprocessableEntityException(String.format("repository %s not found", repository));
    }

    return projectState.getNameKey();
  }
}
