// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.server.mail.EmailFactories.PASSWORD_UPDATED;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

/**
 * REST endpoint to delete a token of an account.
 *
 * <p>This REST endpoint handles {@code DELETE /accounts/<account-identifier>/tokens/<token-id>}
 * requests.
 */
@Singleton
public class DeleteToken implements RestModifyView<AccountResource.Token, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AuthTokenAccessor tokenAccessor;
  private final EmailFactories emailFactories;

  @Inject
  DeleteToken(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AuthTokenAccessor tokenAccessor,
      EmailFactories emailFactories) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.tokenAccessor = tokenAccessor;
    this.emailFactories = emailFactories;
  }

  @Override
  public Response<String> apply(AccountResource.Token rsrc, Input input)
      throws AuthException,
          RepositoryNotFoundException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException,
          InvalidAuthTokenException {
    return apply(rsrc.getUser(), rsrc.getId(), true);
  }

  @CanIgnoreReturnValue
  public Response<String> apply(IdentifiedUser user, String id, boolean notify)
      throws RepositoryNotFoundException,
          IOException,
          ConfigInvalidException,
          AuthException,
          PermissionBackendException,
          InvalidAuthTokenException {
    if (!self.get().hasSameAccountId(user)) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }
    Account.Id accountId = user.getAccountId();
    tokenAccessor.deleteToken(accountId, id);
    if (notify) {
      try {
        emailFactories
            .createOutgoingEmail(
                PASSWORD_UPDATED, emailFactories.createHttpPasswordUpdateEmail(user, "deleted"))
            .send();
      } catch (EmailException e) {
        logger.atSevere().withCause(e).log(
            "Cannot send token deletion message to %s", user.getAccount().preferredEmail());
      }
    }

    return Response.none();
  }
}
