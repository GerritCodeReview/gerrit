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

package com.google.gerrit.server.account;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountVisibilityProvider implements Provider<AccountVisibility> {
  private static final Logger log =
      LoggerFactory.getLogger(AccountVisibilityProvider.class);

  private final AccountVisibility accountVisibility;

  @Inject
  AccountVisibilityProvider(@GerritServerConfig Config cfg) {
    AccountVisibility av;
    if (cfg.getString("accounts", null, "visibility") != null) {
      av = cfg.getEnum("accounts", null, "visibility", AccountVisibility.ALL);
    } else if (cfg.getString("suggest", null, "accounts") != null) {
      try {
        av = cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
        log.warn(String.format(
            "Using legacy value %s for suggest.accounts;"
            + " use accounts.visibility=%s instead",
            av, av));
      } catch (IllegalArgumentException err) {
        // If suggest.accounts is a valid boolean, it's a new-style config, and
        // we should use the default here. Invalid values are caught in
        // SuggestServiceImpl so we don't worry about them here.
        av = AccountVisibility.ALL;
      }
    } else {
      av = AccountVisibility.ALL;
    }
    accountVisibility = av;
  }

  @Override
  public AccountVisibility get() {
    return accountVisibility;
  }
}
