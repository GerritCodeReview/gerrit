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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.mail.EmailFactories.PASSWORD_UPDATED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.common.HttpPasswordInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
public class PutHttpPassword implements RestModifyView<AccountResource, HttpPasswordInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int LEN = 31;
  private static final SecureRandom rng;

  static {
    try {
      rng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot create RNG for password generator", e);
    }
  }

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ExternalIds externalIds;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final EmailFactories emailFactories;
  private final ExternalIdFactory externalIdFactory;
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  PutHttpPassword(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ExternalIds externalIds,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      EmailFactories emailFactories,
      ExternalIdFactory externalIdFactory,
      ExternalIdKeyFactory externalIdKeyFactory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.externalIds = externalIds;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.emailFactories = emailFactories;
    this.externalIdFactory = externalIdFactory;
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, HttpPasswordInput input)
      throws AuthException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (input == null) {
      input = new HttpPasswordInput();
    }
    input.httpPassword = Strings.emptyToNull(input.httpPassword);

    String newPassword;
    if (input.generate) {
      newPassword = generate();
    } else if (input.httpPassword == null) {
      newPassword = null;
    } else {
      // Only administrators can explicitly set the password.
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
      newPassword = input.httpPassword;
    }
    return apply(rsrc.getUser(), newPassword);
  }

  @UsedAt(UsedAt.Project.PLUGIN_SERVICEUSER)
  public Response<String> apply(IdentifiedUser user, String newPassword)
      throws ResourceNotFoundException,
          ResourceConflictException,
          IOException,
          ConfigInvalidException {
    String userName =
        user.getUserName().orElseThrow(() -> new ResourceConflictException("username must be set"));
    Optional<ExternalId> optionalExtId =
        externalIds.get(externalIdKeyFactory.create(SCHEME_USERNAME, userName));
    ExternalId extId = optionalExtId.orElseThrow(ResourceNotFoundException::new);
    accountsUpdateProvider
        .get()
        .update(
            "Set HTTP Password via API",
            extId.accountId(),
            u ->
                u.updateExternalId(
                    externalIdFactory.createWithPassword(
                        extId.key(), extId.accountId(), extId.email(), newPassword)));

    try {
      emailFactories
          .createOutgoingEmail(
              PASSWORD_UPDATED,
              emailFactories.createHttpPasswordUpdateEmail(
                  user, newPassword == null ? "deleted" : "added or updated"))
          .send();
    } catch (EmailException e) {
      logger.atSevere().withCause(e).log(
          "Cannot send HttpPassword update message to %s", user.getAccount().preferredEmail());
    }

    return Strings.isNullOrEmpty(newPassword) ? Response.none() : Response.ok(newPassword);
  }

  @UsedAt(UsedAt.Project.PLUGIN_SERVICEUSER)
  public static String generate() {
    byte[] rand = new byte[LEN];
    rng.nextBytes(rand);

    byte[] enc = BaseEncoding.base64().encode(rand).getBytes(UTF_8);
    StringBuilder r = new StringBuilder(enc.length);
    for (int i = 0; i < enc.length; i++) {
      if (enc[i] == '=') {
        break;
      }
      r.append((char) enc[i]);
    }
    return r.toString();
  }
}
