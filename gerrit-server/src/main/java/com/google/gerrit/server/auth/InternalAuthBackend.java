// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.auth;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class InternalAuthBackend implements AuthBackend {
  private final AccountCache accountCache;
  private final AuthConfig authConfig;
  private final ExternalIds externalIds;

  @Inject
  InternalAuthBackend(AccountCache accountCache, AuthConfig authConfig, ExternalIds externalIds) {
    this.accountCache = accountCache;
    this.authConfig = authConfig;
    this.externalIds = externalIds;
  }

  @Override
  public String getDomain() {
    return "gerrit";
  }

  // TODO(gerritcodereview-team): This function has no coverage.
  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException, AuthException {
    if (Strings.isNullOrEmpty(req.getUsername()) || Strings.isNullOrEmpty(req.getPassword())) {
      throw new MissingCredentialsException();
    }

    String username;
    if (authConfig.isUserNameToLowerCase()) {
      username = req.getUsername().toLowerCase(Locale.US);
    } else {
      username = req.getUsername();
    }

    ExternalId.Key extIdKey = ExternalId.Key.create(SCHEME_USERNAME, username);
    try {
      ExternalId extId = externalIds.get(extIdKey);
      if (extId == null) {
        throw new UnknownUserException();
      }
      AccountState who = accountCache.get(extId.accountId());
      if (who == null) {
        throw new UnknownUserException();
      } else if (!who.getAccount().isActive()) {
        throw new UserNotAllowedException(
            "Authentication failed for "
                + username
                + ": account inactive or not provisioned in Gerrit");
      }

      if (!who.checkPassword(req.getPassword(), username)) {
        throw new InvalidCredentialsException();
      }
      return new AuthUser(AuthUser.UUID.create(username), username);
    } catch (IOException | ConfigInvalidException e) {
      throw new AuthException(String.format("Failed to read external ID %s", extIdKey), e);
    }
  }
}
