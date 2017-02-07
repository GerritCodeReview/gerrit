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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class QueryAccounts implements RestReadView<TopLevelResource> {
  private static final int MAX_SUGGEST_RESULTS = 100;
  private static final String MAX_SUFFIX = "\u9fa5";

  private final AccountControl accountControl;
  private final AccountLoader.Factory accountLoaderFactory;
  private final AccountCache accountCache;
  private final AccountIndexCollection indexes;
  private final AccountQueryBuilder queryBuilder;
  private final AccountQueryProcessor queryProcessor;
  private final ReviewDb db;
  private final boolean suggestConfig;
  private final int suggestFrom;

  private AccountLoader accountLoader;
  private boolean suggest;
  private int suggestLimit = 10;
  private String query;
  private Integer start;
  private EnumSet<ListAccountsOption> options;

  @Option(name = "--suggest", metaVar = "SUGGEST", usage = "suggest users")
  public void setSuggest(boolean suggest) {
    this.suggest = suggest;
  }

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of users to return"
  )
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

  @Option(name = "-o", usage = "Output options per account")
  public void addOption(ListAccountsOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListAccountsOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Option(
    name = "--query",
    aliases = {"-q"},
    metaVar = "QUERY",
    usage = "match users"
  )
  public void setQuery(String query) {
    this.query = query;
  }

  @Option(
    name = "--start",
    aliases = {"-S"},
    metaVar = "CNT",
    usage = "Number of accounts to skip"
  )
  public void setStart(int start) {
    this.start = start;
  }

  @Inject
  QueryAccounts(
      AccountControl.Factory accountControlFactory,
      AccountLoader.Factory accountLoaderFactory,
      AccountCache accountCache,
      AccountIndexCollection indexes,
      AccountQueryBuilder queryBuilder,
      AccountQueryProcessor queryProcessor,
      ReviewDb db,
      @GerritServerConfig Config cfg) {
    this.accountControl = accountControlFactory.get();
    this.accountLoaderFactory = accountLoaderFactory;
    this.accountCache = accountCache;
    this.indexes = indexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
    this.db = db;
    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);
    this.options = EnumSet.noneOf(ListAccountsOption.class);

    if ("off".equalsIgnoreCase(cfg.getString("suggest", null, "accounts"))) {
      suggestConfig = false;
    } else {
      boolean suggest;
      try {
        AccountVisibility av = cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
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

    if (suggest && (!suggestConfig || query.length() < suggestFrom)) {
      return Collections.emptyList();
    }

    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.ID);
    if (options.contains(ListAccountsOption.DETAILS)) {
      fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    }
    if (options.contains(ListAccountsOption.ALL_EMAILS)) {
      fillOptions.add(FillOptions.EMAIL);
      fillOptions.add(FillOptions.SECONDARY_EMAILS);
    }
    if (suggest) {
      fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
      fillOptions.add(FillOptions.EMAIL);
      fillOptions.add(FillOptions.SECONDARY_EMAILS);
    }
    accountLoader = accountLoaderFactory.create(fillOptions);

    AccountIndex searchIndex = indexes.getSearchIndex();
    if (searchIndex != null) {
      return queryFromIndex();
    }

    if (!suggest) {
      throw new MethodNotAllowedException();
    }
    if (start != null) {
      throw new MethodNotAllowedException("option start not allowed");
    }
    return queryFromDb();
  }

  public List<AccountInfo> queryFromIndex()
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
        queryPred = queryBuilder.defaultQuery(query);
        queryProcessor.setLimit(suggestLimit);
      } else {
        queryPred = queryBuilder.parse(query);
      }
      QueryResult<AccountState> result = queryProcessor.query(queryPred);
      for (AccountState accountState : result.entities()) {
        Account.Id id = accountState.getAccount().getId();
        matches.put(id, accountLoader.get(id));
      }

      accountLoader.fill();

      List<AccountInfo> sorted =
          AccountInfoComparator.ORDER_NULLS_LAST.sortedCopy(matches.values());
      if (!sorted.isEmpty() && result.more()) {
        sorted.get(sorted.size() - 1)._moreAccounts = true;
      }
      return sorted;
    } catch (QueryParseException e) {
      if (suggest) {
        return ImmutableList.of();
      }
      throw new BadRequestException(e.getMessage());
    }
  }

  public List<AccountInfo> queryFromDb() throws OrmException {
    String a = query;
    String b = a + MAX_SUFFIX;

    Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
    Map<Account.Id, String> queryEmail = new HashMap<>();

    for (Account p : db.accounts().suggestByFullName(a, b, suggestLimit)) {
      addSuggestion(matches, p);
    }
    if (matches.size() < suggestLimit) {
      for (Account p : db.accounts().suggestByPreferredEmail(a, b, suggestLimit - matches.size())) {
        addSuggestion(matches, p);
      }
    }
    if (matches.size() < suggestLimit) {
      for (AccountExternalId e :
          db.accountExternalIds().suggestByEmailAddress(a, b, suggestLimit - matches.size())) {
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

    return AccountInfoComparator.ORDER_NULLS_LAST.sortedCopy(matches.values());
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
