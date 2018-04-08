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

package com.google.gerrit.server.restapi.account;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountInfoComparator;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.account.AccountPredicates;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class QueryAccounts implements RestReadView<TopLevelResource> {
  private static final int MAX_SUGGEST_RESULTS = 100;

  private final PermissionBackend permissionBackend;
  private final AccountLoader.Factory accountLoaderFactory;
  private final AccountQueryBuilder queryBuilder;
  private final AccountQueryProcessor queryProcessor;
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
    queryProcessor.setUserProvidedLimit(n);

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
      PermissionBackend permissionBackend,
      AccountLoader.Factory accountLoaderFactory,
      AccountQueryBuilder queryBuilder,
      AccountQueryProcessor queryProcessor,
      @GerritServerConfig Config cfg) {
    this.permissionBackend = permissionBackend;
    this.accountLoaderFactory = accountLoaderFactory;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
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
      throws OrmException, RestApiException, PermissionBackendException {
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
    boolean modifyAccountCapabilityChecked = false;
    if (options.contains(ListAccountsOption.ALL_EMAILS)) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
      modifyAccountCapabilityChecked = true;
      fillOptions.add(FillOptions.EMAIL);
      fillOptions.add(FillOptions.SECONDARY_EMAILS);
    }
    if (suggest) {
      fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
      fillOptions.add(FillOptions.EMAIL);

      if (modifyAccountCapabilityChecked
          || permissionBackend.currentUser().test(GlobalPermission.MODIFY_ACCOUNT)) {
        fillOptions.add(FillOptions.SECONDARY_EMAILS);
      }
    }
    accountLoader = accountLoaderFactory.create(fillOptions);

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
        queryProcessor.setUserProvidedLimit(suggestLimit);
      } else {
        queryPred = queryBuilder.parse(query);
      }
      if (!AccountPredicates.hasActive(queryPred)) {
        // if neither 'is:active' nor 'is:inactive' appears in the query only
        // active accounts should be queried
        queryPred = AccountPredicates.andActive(queryPred);
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
}
