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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.QueryResult;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryAccounts implements RestReadView<TopLevelResource> {
  private static final int MAX_SUGGEST_RESULTS = 100;
  private static final String MAX_SUFFIX = "\u9fa5";

  private final AccountControl accountControl;
  private final AccountLoader accountLoader;
  private final AccountCache accountCache;
  private final AccountIndexCollection indexes;
  private final AccountQueryBuilder queryBuilder;
  private final AccountQueryProcessor queryProcessor;
  private final ReviewDb db;
  private final boolean suggestConfig;
  private final int suggestFrom;

  private boolean suggest;
  private int suggestLimit = 10;
  private String query;
  private Integer start;

  @Option(name = "--suggest", metaVar = "SUGGEST", usage = "suggest users")
  public void setSuggest(boolean suggest) {
    this.suggest = suggest;
  }

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "maximum number of users to return")
  public void setLimit(int n) {
    queryProcessor.setLimit(n);

    if (n < 0) {
      suggestLimit = 10;
    } else if (n == 0) {
      suggestLimit = MAX_SUGGEST_RESULTS;
    } else {
      suggestLimit = Math.min(n, MAX_SUGGEST_RESULTS);
    }
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", usage = "match users")
  public void setQuery(String query) {
    this.query = query;
  }

  @Option(name = "--start", aliases = {"-S"}, metaVar = "CNT",
      usage = "Number of accounts to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Inject
  QueryAccounts(AccountControl.Factory accountControlFactory,
      AccountLoader.Factory accountLoaderFactory,
      AccountCache accountCache,
      AccountIndexCollection indexes,
      AccountQueryBuilder queryBuilder,
      AccountQueryProcessor queryProcessor,
      ReviewDb db,
      @GerritServerConfig Config cfg) {
    accountControl = accountControlFactory.get();
    accountLoader = accountLoaderFactory.create(true);
    this.accountCache = accountCache;
    this.indexes = indexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
    this.db = db;
    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);

    if ("off".equalsIgnoreCase(cfg.getString("suggest", null, "accounts"))) {
      suggestConfig = false;
    } else {
      boolean suggest;
      try {
        AccountVisibility av =
            cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
        suggest = (av != AccountVisibility.NONE);
      } catch (IllegalArgumentException err) {
        suggest = cfg.getBoolean("suggest", null, "accounts", true);
      }
      this.suggestConfig = suggest;
    }
  }

  @Override
  public List<AccountInfo> apply(TopLevelResource rsrc)
      throws OrmException, BadRequestException, MethodNotAllowedException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    AccountIndex searchIndex = indexes.getSearchIndex();
    if (!suggest && searchIndex == null) {
      throw new MethodNotAllowedException();
    }

    if (searchIndex == null && start != null) {
      throw new MethodNotAllowedException("option start not allowed");
    }

    if (suggest && (!suggestConfig || query.length() < suggestFrom)) {
      return Collections.emptyList();
    }

    Collection<AccountInfo> matches =
        searchIndex != null
            ? queryFromIndex()
            : queryFromDb();
    return AccountInfoComparator.ORDER_NULLS_LAST.sortedCopy(matches);
  }

  public Collection<AccountInfo> queryFromIndex()
      throws BadRequestException, MethodNotAllowedException, OrmException {
    if (queryProcessor.isDisabled()) {
      throw new MethodNotAllowedException("query disabled");
    }

    if (start != null) {
      queryProcessor.setStart(start);
    }

    Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
    try {
      Predicate<AccountState> queryPred;
      if (suggest) {
        queryPred = queryBuilder.defaultField(query);
        queryProcessor.setLimit(suggestLimit);
      } else {
        queryPred = queryBuilder.parse(query);
      }
      QueryResult<AccountState> result = queryProcessor.query(queryPred);
      for (AccountState accountState : result.entities()) {
        Account.Id id = accountState.getAccount().getId();
        matches.put(id, accountLoader.get(id));
      }
    } catch (QueryParseException e) {
      if (suggest) {
        return ImmutableSet.of();
      }
      throw new BadRequestException(e.getMessage());
    }

    accountLoader.fill();
    return matches.values();
  }

  public Collection<AccountInfo> queryFromDb() throws OrmException {
    String a = query;
    String b = a + MAX_SUFFIX;

    Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
    Map<Account.Id, String> queryEmail = new HashMap<>();

    for (Account p : db.accounts().suggestByFullName(a, b, suggestLimit)) {
      addSuggestion(matches, p);
    }
    if (matches.size() < suggestLimit) {
      for (Account p : db.accounts()
          .suggestByPreferredEmail(a, b, suggestLimit - matches.size())) {
        addSuggestion(matches, p);
      }
    }
    if (matches.size() < suggestLimit) {
      for (AccountExternalId e : db.accountExternalIds()
          .suggestByEmailAddress(a, b, suggestLimit - matches.size())) {
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

    return matches.values();
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
