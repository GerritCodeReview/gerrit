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
import com.google.inject.Singleton;
import java.util.Locale;

@Singleton
public class InternalAuthBackend implements AuthBackend {
  private final AccountCache accountCache;
  private final AuthConfig authConfig;

  @Inject
  InternalAuthBackend(AccountCache accountCache, AuthConfig authConfig) {
    this.accountCache = accountCache;
    this.authConfig = authConfig;
  }

  @Override
  public String getDomain() {
    return "username";
  }

  // TODO(gerritcodereview-team): This function has no coverage.
  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException, AuthException {
    if (!req.getUsername().isPresent() || !req.getPassword().isPresent()) {
      throw new MissingCredentialsException();
    }

    String username;
    if (authConfig.isUserNameToLowerCase()) {
      username = req.getUsername().map(u -> u.toLowerCase(Locale.US)).get();
    } else {
      username = req.getUsername().get();
    }

    AccountState who =
        accountCache.getByUsername(username).orElseThrow(() -> new UnknownUserException());

    if (!who.getAccount().isActive()) {
      throw new UserNotAllowedException(
          "Authentication failed for "
              + username
              + ": account inactive or not provisioned in Gerrit");
    }

    if (!who.checkPassword(req.getPassword().get(), username)) {
      throw new InvalidCredentialsException();
    }
    return new AuthUser(AuthUser.UUID.create(username), username);
  }
}
