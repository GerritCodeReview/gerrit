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
import java.util.Collection;

/** Checks if a given username and password match a user's external IDs. */
public class PasswordVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Returns {@code true} if there is an external ID matching both the username and password. */
  public static boolean checkPassword(
      Collection<ExternalId> externalIds, String username, @Nullable String password) {
    if (password == null) {
      return false;
    }
    for (ExternalId id : externalIds) {
      // Only process the "username:$USER" entry, which is unique.
      if (!id.isScheme(SCHEME_USERNAME) || !username.equals(id.key().id())) {
        continue;
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
