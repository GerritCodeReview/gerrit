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
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierJson;
import com.google.gerrit.server.verifier.VerifierUUID;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gerrit.server.verifier.db.VerifierCreation;
import com.google.gerrit.server.verifier.db.VerifierUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class CreateVerifier
    implements RestCollectionCreateView<TopLevelResource, VerifierResource, VerifierInput> {
  private final PermissionBackend permissionBackend;
  private final Provider<VerifiersUpdate> verifiersUpdate;
  private final VerifierJson verifierJson;

  @Inject
  public CreateVerifier(
      PermissionBackend permissionBackend,
      @UserInitiated Provider<VerifiersUpdate> verifiersUpdate,
      VerifierJson verifierJson) {
    this.permissionBackend = permissionBackend;
    this.verifiersUpdate = verifiersUpdate;
    this.verifierJson = verifierJson;
  }

  @Override
  public Response<VerifierInfo> apply(
      TopLevelResource parentResource, IdString id, VerifierInput input) throws Exception {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);

    if (input == null) {
      input = new VerifierInput();
    }

    String name = id.get();
    checkInput(name, input);

    String verifierUuid = VerifierUUID.make(name);
    VerifierCreation.Builder verifierCreationBuilder =
        VerifierCreation.builder().setVerifierUuid(verifierUuid).setName(name.trim());
    VerifierUpdate.Builder verifierUpdateBuilder = VerifierUpdate.builder();
    if (input.description != null && !input.description.trim().isEmpty()) {
      verifierUpdateBuilder.setDescription(input.description.trim());
    }
    Verifier verifier =
        verifiersUpdate
            .get()
            .createVerifier(verifierCreationBuilder.build(), verifierUpdateBuilder.build());
    return Response.created(verifierJson.format(verifier));
  }

  private static void checkInput(String name, VerifierInput input) throws BadRequestException {
    if (name == null || name.trim().isEmpty()) {
      throw new BadRequestException("name is required");
    }

    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }
  }
}
