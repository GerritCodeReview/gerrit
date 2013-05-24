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

import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;

import java.util.Locale;

public class InternalUserBackend {
  private final AccountCache accountCache;
  private final boolean usernameToLowerCase;

  @Inject
  InternalUserBackend(AccountCache accountCache, AuthConfig authConfig) {
    this.accountCache = accountCache;
    this.usernameToLowerCase = authConfig.isUserNameToLowerCase();
  }

  public AccountState getByUsername(String username)
      throws UnknownUserException, UserNotAllowedException {
    username = normalizeUsername(username);
    AccountState who = accountCache.getByUsername(username);
    if (who == null) {
      throw new UnknownUserException();
    } else if (!who.getAccount().isActive()) {
      throw new UserNotAllowedException("Authentication failed for " + username
          + ": account inactive or not provisioned in Gerrit");
    }
    return who;
  }

  private String normalizeUsername(String username) {
    return usernameToLowerCase ? username.toLowerCase(Locale.US) : username;
  }
}
