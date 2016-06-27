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
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.QueryResult;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuggestAccounts implements RestReadView<TopLevelResource> {
  private static final int MAX_RESULTS = 100;

  private final AccountLoader accountLoader;
  private final AccountQueryBuilder queryBuilder;
  private final AccountQueryProcessor queryProcessor;
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
    queryProcessor.setLimit(limit);
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", usage = "match users")
  public void setQuery(String query) {
    this.query = query;
  }

  @Inject
  SuggestAccounts(AccountLoader.Factory accountLoaderFactory,
      AccountQueryBuilder queryBuilder,
      AccountQueryProcessor queryProcessor,
      @GerritServerConfig Config cfg) {
    accountLoader = accountLoaderFactory.create(true);
    this.queryProcessor = queryProcessor;
    this.queryBuilder = queryBuilder;
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
      throws OrmException, BadRequestException, MethodNotAllowedException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggest || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    if (queryProcessor.isDisabled()) {
      throw new MethodNotAllowedException("query disabled");
    }

    Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
    try {
      QueryResult<AccountState> result =
          queryProcessor.query(queryBuilder.parse("is:active " + query));
      for (AccountState accountState : result.entities()) {
        Account.Id id = accountState.getAccount().getId();
        matches.put(id, accountLoader.get(id));
      }
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }

    accountLoader.fill();

    List<AccountInfo> m = new ArrayList<>(matches.values());
    Collections.sort(m, AccountInfoComparator.ORDER_NULLS_LAST);
    return m;
  }
}
