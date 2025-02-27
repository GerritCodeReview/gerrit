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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Checks if a given username and token match a user's credentials. */
public class AuthTokenVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final VersionedAuthTokens.Accessor tokenAccessor;
  private final ExternalIds externalIds;

  @Inject
  public AuthTokenVerifier(VersionedAuthTokens.Accessor tokenAccessor, ExternalIds externalIds) {
    this.tokenAccessor = tokenAccessor;
    this.externalIds = externalIds;
  }

  /**
   * Checks if a given username and token match a user's credentials.
   *
   * @param accountId the account ID to check.
   * @param providedToken the token to check.
   * @return whether there is a token stored for the account that matches the provided token.
   */
  public boolean checkToken(Account.Id accountId, @Nullable String providedToken) {
    if (Strings.isNullOrEmpty(providedToken)) {
      return false;
    }

    try {
      for (AuthToken t : tokenAccessor.getTokens(accountId)) {
        try {
          if (HashedPassword.decode(t.hashedToken()).checkPassword(providedToken)) {
            return true;
          }
        } catch (HashedPassword.DecoderException e) {
          logger.atSevere().withCause(e).log(
              "Could not decode token for account %s: %s ", accountId, e.getMessage());
        }
      }
      return checkPassword(accountId, providedToken);
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log(
          "Could not parse tokens for account %s: %s ", accountId, e.getMessage());
    }
    return false;
  }

  @Deprecated
  private boolean checkPassword(Account.Id accountId, String password) throws IOException {
    ImmutableList<ExternalId> ids = externalIds.byAccount(accountId, SCHEME_USERNAME).asList();
    if (ids.isEmpty()) {
      return false;
    }

    String hashedStr = ids.get(0).password();
    if (!Strings.isNullOrEmpty(hashedStr)) {
      try {
        return HashedPassword.decode(hashedStr).checkPassword(password);
      } catch (HashedPassword.DecoderException e) {
        logger.atSevere().log(
            "DecoderException for account %d: %s ", accountId.get(), e.getMessage());
        return false;
      }
    }
    return false;
  }
}
