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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

public class AccountVisibilityProvider implements Provider<AccountVisibility> {
  private final AccountVisibility accountVisibility;

  @Inject
  AccountVisibilityProvider(@GerritServerConfig Config cfg) {
    AccountVisibility av;
    if (cfg.getString("accounts", null, "visibility") != null) {
      av = cfg.getEnum("accounts", null, "visibility", AccountVisibility.ALL);
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
