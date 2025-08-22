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

package com.google.gerrit.server.account.externalids;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import java.util.Collection;

/** Checks if a given username and password match a user's external IDs. */
public class PasswordVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final int MAX_PASSWORD_LENGTH_ACCORDING_TO_BCRYPT_LIMITS = 72;

  private final ExternalIdKeyFactory externalIdKeyFactory;

  private AuthConfig authConfig;

  @Inject
  public PasswordVerifier(ExternalIdKeyFactory externalIdKeyFactory, AuthConfig authConfig) {
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.authConfig = authConfig;
  }

  /** Returns {@code true} if there is an external ID matching both the username and password. */
  public boolean checkPassword(
      Collection<ExternalId> externalIds, String username, @Nullable String password) {
    if (password == null || password.length() >= MAX_PASSWORD_LENGTH_ACCORDING_TO_BCRYPT_LIMITS) {
      return false;
    }

    for (ExternalId id : externalIds) {
      // Only process the "username:$USER" entry, which is unique.
      if (!id.isScheme(SCHEME_USERNAME)) {
        continue;
      }

      if (!id.key().equals(externalIdKeyFactory.create(SCHEME_USERNAME, username))) {
        if (!authConfig.isUserNameCaseInsensitiveMigrationMode()) {
          continue;
        }

        if (!id.key().equals(externalIdKeyFactory.create(SCHEME_USERNAME, username, false))) {
          continue;
        }
      }

      String hashedStr = id.password();
      if (!Strings.isNullOrEmpty(hashedStr)) {
        try {
          return HashedPassword.decode(hashedStr).checkPassword(password);
        } catch (HashedPassword.DecoderException e) {
          logger.atSevere().log("DecoderException for user %s: %s ", username, e.getMessage());
          return false;
        }
      }
    }
    return false;
  }
}
