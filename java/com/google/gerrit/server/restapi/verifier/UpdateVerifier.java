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
import com.google.gerrit.extensions.api.verifiers.UpdateVerifierOption;
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
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gerrit.server.verifier.db.VerifierUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.EnumSet;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class UpdateVerifier implements RestModifyView<VerifierResource, VerifierInput> {
  private final EnumSet<UpdateVerifierOption> options = EnumSet.noneOf(UpdateVerifierOption.class);
  private final PermissionBackend permissionBackend;
  private final Provider<VerifiersUpdate> verifiersUpdate;
  private final VerifierJson verifierJson;

  @Option(name = "-o", usage = "Output options")
  public void addOption(UpdateVerifierOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  public void setOptionFlagsHex(String hex) {
    options.addAll(UpdateVerifierOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  public UpdateVerifier(
      PermissionBackend permissionBackend,
      @UserInitiated Provider<VerifiersUpdate> verifiersUpdate,
      VerifierJson verifierJson) {
    this.permissionBackend = permissionBackend;
    this.verifiersUpdate = verifiersUpdate;
    this.verifierJson = verifierJson;
  }

  @Override
  public VerifierInfo apply(VerifierResource resource, VerifierInput input)
      throws RestApiException, PermissionBackendException, NoSuchVerifierException, IOException,
          ConfigInvalidException {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);

    checkInput(input);

    VerifierUpdate.Builder verifierUpdateBuilder = VerifierUpdate.builder();
    if (options.contains(UpdateVerifierOption.NAME)) {
      verifierUpdateBuilder.setName(input.name.trim());
    }
    if (options.contains(UpdateVerifierOption.DESCRIPTION)) {
      verifierUpdateBuilder.setDescription(Strings.nullToEmpty(input.description).trim());
    }

    Verifier updatedVerifier =
        verifiersUpdate
            .get()
            .updateVerifier(resource.getVerifier().getUuid(), verifierUpdateBuilder.build());
    return verifierJson.format(updatedVerifier);
  }

  private void checkInput(VerifierInput input) throws BadRequestException {
    if (input.name != null && !options.contains(UpdateVerifierOption.NAME)) {
      throw new BadRequestException(
          String.format(
              "name is set although %s option is not present", UpdateVerifierOption.NAME));
    }

    if ((input.name == null || input.name.trim().isEmpty())
        && options.contains(UpdateVerifierOption.NAME)) {
      throw new BadRequestException("name cannot be unset");
    }

    if (input.description != null && !options.contains(UpdateVerifierOption.DESCRIPTION)) {
      throw new BadRequestException(
          String.format(
              "description is set although %s option is not present",
              UpdateVerifierOption.DESCRIPTION));
    }
  }
}
