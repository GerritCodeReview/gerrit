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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.auth.AuthTokenInfo;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthToken;
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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set an authentication token of an account.
 *
 * <p>This REST endpoint handles {@code PUT
 * /accounts/<account-identifier>/tokens/<token-identifier>} requests.
 *
 * <p>Gerrit only stores the hash of the token, hence if a token was set it's not possible to get it
 * back from Gerrit.
 */
@Singleton
public class CreateToken
    implements RestCollectionCreateView<AccountResource, AccountResource.Token, AuthTokenInput> {
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
  private final EmailFactories emailFactories;
  private final AuthTokenAccessor tokensAccessor;

  @Inject
  CreateToken(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      EmailFactories emailFactories,
      AuthTokenAccessor tokensAccessor) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.emailFactories = emailFactories;
    this.tokensAccessor = tokensAccessor;
  }

  @Override
  @CanIgnoreReturnValue
  public Response<AuthTokenInfo> apply(AccountResource rsrc, IdString id, AuthTokenInput input)
      throws AuthException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException,
          ConfigInvalidException,
          PermissionBackendException,
          InvalidAuthTokenException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (rsrc.getUser().getUserName().isEmpty()) {
      throw new ResourceConflictException("A username is required to use basic authentication.");
    }

    if (input == null) {
      input = new AuthTokenInput();
    }

    if (input.id != null && !input.id.equals(id.get())) {
      throw new ResourceConflictException("Token ID must match in URL and input");
    }

    String newToken;
    if (Strings.isNullOrEmpty(input.token)) {
      newToken = generate();
    } else {
      // Only administrators can explicitly set a token.
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
      newToken = input.token;
    }
    return apply(rsrc.getUser(), id.get(), newToken);
  }

  @UsedAt(UsedAt.Project.PLUGIN_SERVICEUSER)
  public Response<AuthTokenInfo> apply(IdentifiedUser user, String id, String newToken)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    AuthToken token = tokensAccessor.addPlainToken(user.getAccountId(), id, newToken);
    try {
      emailFactories
          .createOutgoingEmail(
              PASSWORD_UPDATED,
              emailFactories.createHttpPasswordUpdateEmail(
                  user, newToken == null ? "deleted" : "added or updated"))
          .send();
    } catch (EmailException e) {
      logger.atSevere().withCause(e).log(
          "Cannot send HttpPassword update message to %s", user.getAccount().preferredEmail());
    }

    AuthTokenInfo info = new AuthTokenInfo();
    info.id = token.id();
    info.token = newToken;
    return Response.created(info);
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
