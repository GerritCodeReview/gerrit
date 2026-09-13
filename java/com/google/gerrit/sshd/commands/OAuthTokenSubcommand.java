// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.auth.oauth.OAuthTokenCache;
import com.google.gerrit.auth.oauth.OAuthTokenRefresher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthRevokedException;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

/**
 * Base for the {@code oauth-token} read subcommands: resolves the caller's token, refreshing on
 * read.
 */
abstract class OAuthTokenSubcommand extends SshCommand {
  @Inject protected OAuthTokenCache tokenCache;
  @Inject protected OAuthTokenRefresher refresher;

  /**
   * Returns the caller's current, non-expired OAuth token, renewing it in place first if it has
   * expired (RFC 6749 section 6). Fails if there is no token or it cannot be refreshed.
   */
  protected OAuthToken currentToken() throws Failure {
    if (!user.isIdentifiedUser()) {
      throw die("not an identified user");
    }
    Account.Id id = user.getAccountId();
    try {
      refresher.refreshIfExpired(id);
    } catch (OAuthRevokedException e) {
      throw die("OAuth grant revoked; sign in again", e);
    }
    OAuthToken token = tokenCache.getEvenIfExpired(id);
    if (token == null) {
      throw die("no OAuth token for this account (sign in with the OAuth provider first)");
    }
    if (token.isExpired()) {
      throw die("OAuth token has expired and could not be refreshed; sign in again");
    }
    return token;
  }
}
