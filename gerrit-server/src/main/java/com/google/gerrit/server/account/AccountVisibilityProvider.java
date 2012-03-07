// Copyright 2012 Google Inc. All Rights Reserved.

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
