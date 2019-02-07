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

package com.google.gerrit.server.restapi.verifier;

import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierCreation;
import com.google.gerrit.server.verifier.VerifierJson;
import com.google.gerrit.server.verifier.VerifierName;
import com.google.gerrit.server.verifier.VerifierUpdate;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateVerifier
    implements RestCollectionModifyView<TopLevelResource, VerifierResource, VerifierInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<VerifiersUpdate> verifiersUpdate;
  private final VerifierJson verifierJson;
  private final ProjectCache projectCache;

  @Inject
  public CreateVerifier(
      PermissionBackend permissionBackend,
      @UserInitiated Provider<VerifiersUpdate> verifiersUpdate,
      VerifierJson verifierJson,
      ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.verifiersUpdate = verifiersUpdate;
    this.verifierJson = verifierJson;
    this.projectCache = projectCache;
  }

  @Override
  public Response<VerifierInfo> apply(TopLevelResource parentResource, VerifierInput input)
      throws RestApiException, PermissionBackendException, IOException, ConfigInvalidException,
          OrmDuplicateKeyException {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);

    if (input == null) {
      input = new VerifierInput();
    }

    String name = VerifierName.clean(input.name);
    if (name.isEmpty()) {
      throw new BadRequestException("name is required");
    }
    Project.NameKey repository = resolveRepository(input.repository);

    String verifierUuid = VerifierUuid.make(name);
    VerifierCreation.Builder verifierCreationBuilder =
        VerifierCreation.builder()
            .setVerifierUuid(verifierUuid)
            .setName(name)
            .setRepository(repository);
    VerifierUpdate.Builder verifierUpdateBuilder = VerifierUpdate.builder();
    if (input.description != null && !input.description.trim().isEmpty()) {
      verifierUpdateBuilder.setDescription(input.description.trim());
    }
    if (input.url != null && !input.url.trim().isEmpty()) {
      verifierUpdateBuilder.setUrl(input.url.trim());
    }
    Verifier verifier =
        verifiersUpdate
            .get()
            .createVerifier(verifierCreationBuilder.build(), verifierUpdateBuilder.build());
    return Response.created(verifierJson.format(verifier));
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
