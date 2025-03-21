// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.server.account.DirectAuthTokenAccessor.LEGACY_ID;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.mail.EmailFactories.PASSWORD_UPDATED;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.extensions.common.HttpPasswordInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.account.PasswordMigrator;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set/delete the password for HTTP access of an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/password.http} and {@code
 * DELETE /accounts/<account-identifier>/password.http} requests.
 *
 * <p>Gerrit only stores the hash of the HTTP password, hence if an HTTP password was set it's not
 * possible to get it back from Gerrit.
 */
@Singleton
@Deprecated
public class PutHttpPassword implements RestModifyView<AccountResource, HttpPasswordInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AuthTokenAccessor tokenAccessor;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final ExternalIds externalIds;
  private final ExternalIdFactory externalIdFactory;
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private final CreateToken putToken;
  private final DeleteToken deleteToken;
  private final EmailFactories emailFactories;

  @Inject
  PutHttpPassword(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AuthTokenAccessor tokenAccessor,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      ExternalIds externalIds,
      ExternalIdFactory externalIdFactory,
      ExternalIdKeyFactory externalIdKeyFactory,
      CreateToken putToken,
      DeleteToken deleteToken,
      EmailFactories emailFactories) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.tokenAccessor = tokenAccessor;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.externalIds = externalIds;
    this.externalIdFactory = externalIdFactory;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.putToken = putToken;
    this.deleteToken = deleteToken;
    this.emailFactories = emailFactories;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, HttpPasswordInput input)
      throws IOException,
          ConfigInvalidException,
          PermissionBackendException,
          BadRequestException,
          RestApiException,
          InvalidAuthTokenException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (input == null) {
      input = new HttpPasswordInput();
    }

    input.httpPassword = Strings.emptyToNull(input.httpPassword);
    boolean isDeleteOp = !input.generate && input.httpPassword == null;

    Response<String> resp = Response.none();
    if (tokenAccessor.getToken(rsrc.getUser().getAccountId(), LEGACY_ID).isPresent()) {
      Optional<ExternalId> optionalExtId =
          externalIds.get(
              externalIdKeyFactory.create(SCHEME_USERNAME, rsrc.getUser().getUserName().get()));
      ExternalId extId = optionalExtId.orElseThrow(ResourceNotFoundException::new);
      accountsUpdateProvider
          .get()
          .update(
              "Remove HTTP Password",
              extId.accountId(),
              u ->
                  u.updateExternalId(
                      externalIdFactory.createWithEmail(
                          extId.key(), extId.accountId(), extId.email())));
      if (isDeleteOp) {
        try {
          emailFactories
              .createOutgoingEmail(
                  PASSWORD_UPDATED,
                  emailFactories.createHttpPasswordUpdateEmail(rsrc.getUser(), "deleted"))
              .send();
        } catch (EmailException e) {
          logger.atSevere().withCause(e).log(
              "Cannot send HttpPassword update message to %s",
              rsrc.getUser().getAccount().preferredEmail());
        }
      }
    } else {
      resp = deleteToken.apply(rsrc.getUser(), PasswordMigrator.DEFAULT_ID, isDeleteOp);
    }

    if (isDeleteOp) {
      return resp;
    }

    AuthTokenInput authTokenInput = new AuthTokenInput();
    authTokenInput.token = input.httpPassword;
    return Response.created(
        putToken
            .apply(rsrc, IdString.fromDecoded(PasswordMigrator.DEFAULT_ID), authTokenInput)
            .value()
            .token);
  }
}
