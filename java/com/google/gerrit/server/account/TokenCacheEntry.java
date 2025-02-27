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
import com.google.gerrit.entities.Account;

public class TokenCacheEntry {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Account.Id accountId;
  private final Token token;

  public TokenCacheEntry(Account.Id accountId, Token token) {
    this.accountId = accountId;
    this.token = token;
  }

  public boolean checkToken(String hashedToken) {
    if (!Strings.isNullOrEmpty(hashedToken)) {
      try {
        return HashedPassword.decode(token.hashedToken()).checkPassword(hashedToken);
      } catch (HashedPassword.DecoderException e) {
        logger.atSevere().log("DecoderException for account %s: %s ", accountId, e.getMessage());
      }
    }
    return false;
  }

  public Token getToken() {
    return token;
  }
}
