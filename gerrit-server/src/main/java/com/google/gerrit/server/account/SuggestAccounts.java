// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuggestAccounts implements RestReadView<TopLevelResource> {
  private static final int MAX_RESULTS = 100;
  private static final String MAX_SUFFIX = "\u9fa5";

  private final AccountControl accountControl;
  private final AccountLoader accountLoader;
  private final AccountCache accountCache;
  private final ReviewDb db;
  private final boolean suggest;
  private final int suggestFrom;

  private int limit = 10;
  private String query;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "maximum number of users to return")
  public void setLimit(int n) {
    if (n < 0) {
      limit = 10;
    } else if (n == 0) {
      limit = MAX_RESULTS;
    } else {
      limit = Math.min(n, MAX_RESULTS);
    }
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", usage = "match users")
  public void setQuery(String query) {
    this.query = query;
  }

  @Inject
  SuggestAccounts(AccountControl.Factory accountControlFactory,
      AccountLoader.Factory accountLoaderFactory,
      AccountCache accountCache,
      ReviewDb db,
      @GerritServerConfig Config cfg) {
    accountControl = accountControlFactory.get();
    accountLoader = accountLoaderFactory.create(true);
    this.accountCache = accountCache;
    this.db = db;
    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);

    if ("off".equalsIgnoreCase(cfg.getString("suggest", null, "accounts"))) {
      suggest = false;
    } else {
      boolean suggest;
      try {
        AccountVisibility av =
            cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
        suggest = (av != AccountVisibility.NONE);
      } catch (IllegalArgumentException err) {
        suggest = cfg.getBoolean("suggest", null, "accounts", true);
      }
      this.suggest = suggest;
    }
  }

  @Override
  public List<AccountInfo> apply(TopLevelResource rsrc)
      throws OrmException, BadRequestException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggest || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    String a = query;
    String b = a + MAX_SUFFIX;

    Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
    Map<Account.Id, String> queryEmail = new HashMap<>();

    for (Account p : db.accounts().suggestByFullName(a, b, limit)) {
      addSuggestion(matches, p);
    }
    if (matches.size() < limit) {
      for (Account p : db.accounts()
          .suggestByPreferredEmail(a, b, limit - matches.size())) {
        addSuggestion(matches, p);
      }
    }
    if (matches.size() < limit) {
      for (AccountExternalId e : db.accountExternalIds()
          .suggestByEmailAddress(a, b, limit - matches.size())) {
        if (addSuggestion(matches, e.getAccountId())) {
          queryEmail.put(e.getAccountId(), e.getEmailAddress());
        }
      }
    }

    accountLoader.fill();
    for (Map.Entry<Account.Id, String> p : queryEmail.entrySet()) {
      AccountInfo info = matches.get(p.getKey());
      if (info != null) {
        info.email = p.getValue();
      }
    }

    List<AccountInfo> m = new ArrayList<>(matches.values());
    Collections.sort(m, AccountInfoComparator.orderNullsLast());
    return m;
  }

  private boolean addSuggestion(Map<Account.Id, AccountInfo> map, Account a) {
    if (!a.isActive()) {
      return false;
    }
    Account.Id id = a.getId();
    if (!map.containsKey(id) && accountControl.canSee(id)) {
      map.put(id, accountLoader.get(id));
      return true;
    }
    return false;
  }

  private boolean addSuggestion(Map<Account.Id, AccountInfo> map, Account.Id id) {
    Account a = accountCache.get(id).getAccount();
    return addSuggestion(map, a);
  }
}
