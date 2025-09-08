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

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Checks if a given username and token match a user's credentials. */
public class AuthTokenVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final int MAX_PASSWORD_LENGTH_ACCORDING_TO_BCRYPT_LIMITS = 72;

  private final AuthTokenAccessor tokenAccessor;

  @Inject
  public AuthTokenVerifier(AuthTokenAccessor tokenAccessor) {
    this.tokenAccessor = tokenAccessor;
  }

  /**
   * Checks if a given username and token match a user's credentials.
   *
   * @param accountId the account ID to check.
   * @param providedToken the token to check.
   * @return whether there is a token hash stored for the account that matches the provided token.
   */
  public boolean checkToken(Account.Id accountId, @Nullable String providedToken) {
    if (Strings.isNullOrEmpty(providedToken)
        || providedToken.length() >= MAX_PASSWORD_LENGTH_ACCORDING_TO_BCRYPT_LIMITS) {
      return false;
    }

    try {
      for (AuthToken t : tokenAccessor.getValidTokens(accountId)) {
        if (HashedPassword.decode(t.hashedToken()).checkPassword(providedToken)) {
          return true;
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      logger.atSevere().withCause(e).log(
          "Could not read tokens for account %s: %s ", accountId, e.getMessage());
    } catch (HashedPassword.DecoderException e) {
      logger.atSevere().withCause(e).log(
          "Could not decode token for account %s: %s ", accountId, e.getMessage());
    }
    return false;
  }
}
