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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.inject.Inject;

/** Checks if a given username and password match a user's external IDs. */
public class AuthTokenVerifier {
  private final AuthTokenCache tokenCache;

  @Inject
  public AuthTokenVerifier(AuthTokenCache tokenCache) {
    this.tokenCache = tokenCache;
  }

  /**
   * Returns {@code true} if there is a token stored for the account that matches the provided
   * token.
   */
  public boolean checkToken(Account.Id accountId, @Nullable String token) {
    if (token == null) {
      return false;
    }

    for (AuthTokenCacheEntry t : tokenCache.get(accountId)) {
      if (t.checkToken(token)) {
        return true;
      }
    }
    return false;
  }
}
