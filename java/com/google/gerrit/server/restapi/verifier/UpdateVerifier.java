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

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchVerifierException;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierJson;
import com.google.gerrit.server.verifier.VerifierName;
import com.google.gerrit.server.verifier.VerifierUpdate;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class UpdateVerifier implements RestModifyView<VerifierResource, VerifierInput> {
  private final GlobalVerifierConfig globalVerifierConfig;
  private final PermissionBackend permissionBackend;
  private final Provider<VerifiersUpdate> verifiersUpdate;
  private final VerifierJson verifierJson;

  @Inject
  public UpdateVerifier(
      GlobalVerifierConfig globalVerifierConfig,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<VerifiersUpdate> verifiersUpdate,
      VerifierJson verifierJson) {
    this.globalVerifierConfig = globalVerifierConfig;
    this.permissionBackend = permissionBackend;
    this.verifiersUpdate = verifiersUpdate;
    this.verifierJson = verifierJson;
  }

  @Override
  public VerifierInfo apply(VerifierResource resource, VerifierInput input)
      throws RestApiException, PermissionBackendException, NoSuchVerifierException, IOException,
          ConfigInvalidException {
    globalVerifierConfig.checkThatApiIsEnabled();
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);

    VerifierUpdate.Builder verifierUpdateBuilder = VerifierUpdate.builder();

    if (input.name != null) {
      String newName = VerifierName.clean(input.name);
      if (newName.isEmpty()) {
        throw new BadRequestException("name cannot be unset");
      }
      verifierUpdateBuilder.setName(newName);
    }

    if (input.description != null) {
      verifierUpdateBuilder.setDescription(Strings.nullToEmpty(input.description).trim());
    }

    Verifier updatedVerifier =
        verifiersUpdate
            .get()
            .updateVerifier(resource.getVerifier().getUuid(), verifierUpdateBuilder.build());
    return verifierJson.format(updatedVerifier);
  }
}
