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

package com.google.gerrit.server.restapi.checker;

import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.checker.Checker;
import com.google.gerrit.server.checker.CheckerCreation;
import com.google.gerrit.server.checker.CheckerJson;
import com.google.gerrit.server.checker.CheckerName;
import com.google.gerrit.server.checker.CheckerUpdate;
import com.google.gerrit.server.checker.CheckerUuid;
import com.google.gerrit.server.checker.CheckersUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
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
  private final GlobalCheckerConfig globalCheckerConfig;
  private final PermissionBackend permissionBackend;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final CheckerJson checkerJson;
  private final ProjectCache projectCache;

  @Inject
  public CreateChecker(
      GlobalCheckerConfig globalCheckerConfig,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<CheckersUpdate> checkersUpdate,
      CheckerJson checkerJson,
      ProjectCache projectCache) {
    this.globalCheckerConfig = globalCheckerConfig;
    this.permissionBackend = permissionBackend;
    this.checkersUpdate = checkersUpdate;
    this.checkerJson = checkerJson;
    this.projectCache = projectCache;
  }

  @Override
  public Response<CheckerInfo> apply(TopLevelResource parentResource, CheckerInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          OrmDuplicateKeyException {
    globalCheckerConfig.checkThatApiIsEnabled();
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_CHECKERS);

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
    if (input.url != null && !input.url.trim().isEmpty()) {
      checkerUpdateBuilder.setUrl(input.url.trim());
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
