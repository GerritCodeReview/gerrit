// Copyright (C) 2017 The Android Open Source Project
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

package java.com.google.gerrit.server.restapi.account;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.accounts.DisplayNameInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.com.google.gerrit.extensions.api.accounts.DisplayNameInput;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set the display name of an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/displayname} requests.
 *
 * <p>The display name is a free-form text that a user can set for the own account. It defines how
 * the user's name will be rendered in the UI in most screens. It is optional, and if not set, then
 * the UI falls back to whatever is configured as the default display name, e.g. the full name.
 */
@Singleton
public class PutDisplayName implements RestModifyView<AccountResource, DisplayNameInput> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  PutDisplayName(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, DisplayNameInput input)
      throws AuthException, ResourceNotFoundException, IOException, PermissionBackendException,
          ConfigInvalidException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<String> apply(IdentifiedUser user, DisplayNameInput input)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new DisplayNameInput();
    }

    String newDisplayName = input.displayName;
    AccountState accountState =
        accountsUpdateProvider
            .get()
            .update(
                "Set Display Name via API",
                user.getAccountId(),
                u -> u.setDisplayName(newDisplayName))
            .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    return Strings.isNullOrEmpty(accountState.account().displayName())
        ? Response.none()
        : Response.ok(accountState.account().displayName());
  }
}
