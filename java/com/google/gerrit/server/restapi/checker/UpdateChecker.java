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

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchCheckerException;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.checker.Checker;
import com.google.gerrit.server.checker.CheckerJson;
import com.google.gerrit.server.checker.CheckerName;
import com.google.gerrit.server.checker.CheckerUpdate;
import com.google.gerrit.server.checker.CheckerUrl;
import com.google.gerrit.server.checker.CheckersUpdate;
import com.google.gerrit.server.checker.GlobalChecksConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class UpdateChecker implements RestModifyView<CheckerResource, CheckerInput> {
  private final GlobalChecksConfig globalChecksConfig;
  private final PermissionBackend permissionBackend;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final CheckerJson checkerJson;

  @Inject
  public UpdateChecker(
      GlobalChecksConfig globalChecksConfig,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<CheckersUpdate> checkersUpdate,
      CheckerJson checkerJson) {
    this.globalChecksConfig = globalChecksConfig;
    this.permissionBackend = permissionBackend;
    this.checkersUpdate = checkersUpdate;
    this.checkerJson = checkerJson;
  }

  @Override
  public CheckerInfo apply(CheckerResource resource, CheckerInput input)
      throws RestApiException, PermissionBackendException, NoSuchCheckerException, IOException,
          ConfigInvalidException {
    globalChecksConfig.checkThatApiIsEnabled();
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_CHECKERS);

    CheckerUpdate.Builder checkerUpdateBuilder = CheckerUpdate.builder();

    if (input.name != null) {
      String newName = CheckerName.clean(input.name);
      if (newName.isEmpty()) {
        throw new BadRequestException("name cannot be unset");
      }
      checkerUpdateBuilder.setName(newName);
    }

    if (input.description != null) {
      checkerUpdateBuilder.setDescription(Strings.nullToEmpty(input.description).trim());
    }

    if (input.url != null) {
      checkerUpdateBuilder.setUrl(CheckerUrl.clean(input.url));
    }

    Checker updatedChecker =
        checkersUpdate
            .get()
            .updateChecker(resource.getChecker().getUuid(), checkerUpdateBuilder.build());
    return checkerJson.format(updatedChecker);
  }
}
