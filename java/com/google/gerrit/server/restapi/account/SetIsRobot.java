// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to mark an account as robot.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/robot} requests.
 *
 * <p>Marking an account as human (non-robot) is handled by {@link DeleteIsRobot}.
 */
@Singleton
public class SetIsRobot implements RestModifyView<AccountResource, Input> {

  private final Provider<CurrentUser> currentUser;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  SetIsRobot(
      Provider<CurrentUser> currentUser,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.currentUser = currentUser;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (!currentUser.get().isIdentifiedUser()
        || !rsrc.getUser().getAccountId().equals(currentUser.get().getAccountId())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    AtomicBoolean wasRobot = new AtomicBoolean(false);
    accountsUpdateProvider
        .get()
        .update(
            "Set IsRobot",
            rsrc.getUser().getAccountId(),
            (a, u) -> {
              if (a.account().isRobot()) {
                wasRobot.set(true);
              } else {
                u.setIsRobot(true);
              }
            })
        .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    return wasRobot.get() ? Response.ok() : Response.created();
  }
}
