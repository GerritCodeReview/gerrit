// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.change.SuggestReviewers.Input;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuggestReviewers implements RestModifyView<ChangeResource, Input> {

  public static class Input {
    String query;
    int limit;
  }

  private static final String MAX_SUFFIX = "\u9fa5";

  private final AccountInfo.Loader.Factory accountLoaderFactory;
  private final AccountCache accountCache;
  private final Provider<ReviewDb> dbProvider;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final boolean suggestAccounts;

  @Inject
  SuggestReviewers(
      AccountInfo.Loader.Factory accountLoaderFactory,
      AccountCache accountCache,
      Provider<ReviewDb> dbProvider,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg) {
    this.accountLoaderFactory = accountLoaderFactory;
    this.accountCache = accountCache;
    this.dbProvider = dbProvider;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;

    if ("OFF".equals(this.cfg.getString("suggest", null, "accounts"))) {
      this.suggestAccounts = false;
    } else {
      boolean suggestAccounts;
      try {
        AccountVisibility av =
            cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
        suggestAccounts = (av != AccountVisibility.NONE);
      } catch (IllegalArgumentException err) {
        suggestAccounts = cfg.getBoolean("suggest", null, "accounts", true);
      }
      this.suggestAccounts = suggestAccounts;
    }
  }

  private interface VisibilityControl {
    boolean isVisible(Account account) throws OrmException;
  }

  @Override
  public List<AccountInfo> apply(ChangeResource rsrc, Input input)
      throws BadRequestException, OrmException {
    if (input.query == null) {
      throw new BadRequestException("missing query field");
    }
    return suggestChangeReviewer(rsrc.getControl(),
        rsrc.getChange().getId(),
        input.query, input.limit);
  }

  private List<AccountInfo> suggestChangeReviewer(
      final ChangeControl changeControl,
      final Change.Id change,
      final String query,
      final int limit) throws OrmException {
    VisibilityControl visibilityControl;
    if (changeControl.getRefControl().isVisibleByRegisteredUsers()) {
      visibilityControl = new VisibilityControl() {
        @Override
        public boolean isVisible(Account account) throws OrmException {
          return true;
        }
      };
    } else {
      visibilityControl = new VisibilityControl() {
        @Override
        public boolean isVisible(Account account) throws OrmException {
          IdentifiedUser who =
              identifiedUserFactory.create(dbProvider, account.getId());
          // we can't use changeControl directly as it won't suggest reviewers
          // to drafts
          return changeControl.forUser(who).isRefVisible();
        }
      };
    }
    final List<AccountInfo> reviewer =
        suggestAccount(query, limit, visibilityControl);
    accountLoaderFactory.create(true).fill(reviewer);
    if (reviewer.size() <= limit) {
      return reviewer;
    } else {
      return reviewer.subList(0, limit);
    }
  }

  private List<AccountInfo> suggestAccount(final String query,
      final int limit,
      VisibilityControl visibilityControl)
      throws OrmException {
    if (!suggestAccounts) {
      return Collections.emptyList();
    }

    final String a = query;
    final String b = a + MAX_SUFFIX;
    final int max = 10;
    final int n = limit <= 0 ? max : Math.min(limit, max);

    final LinkedHashMap<Account.Id, AccountInfo> r =
        new LinkedHashMap<Account.Id, AccountInfo>();
    for (final Account p : dbProvider.get().accounts()
        .suggestByFullName(a, b, n)) {
      addSuggestion(r, p, new AccountInfo(p.getId()), visibilityControl);
    }
    if (r.size() < n) {
      for (final Account p : dbProvider.get().accounts()
          .suggestByPreferredEmail(a, b, n - r.size())) {
        addSuggestion(r, p, new AccountInfo(p.getId()), visibilityControl);
      }
    }
    if (r.size() < n) {
      for (final AccountExternalId e : dbProvider.get().accountExternalIds()
          .suggestByEmailAddress(a, b, n - r.size())) {
        if (!r.containsKey(e.getAccountId())) {
          final Account p = accountCache.get(e.getAccountId()).getAccount();
          final AccountInfo info = new AccountInfo(p.getId());
          addSuggestion(r, p, info, visibilityControl);
        }
      }
    }
    return new ArrayList<AccountInfo>(r.values());
  }

  private void addSuggestion(Map<Account.Id, AccountInfo> map, Account account,
      AccountInfo info, VisibilityControl visibilityControl)
      throws OrmException {
    if (map.containsKey(account.getId())) {
      return;
    }
    if (!account.isActive()) {
      return;
    }
    if (visibilityControl.isVisible(account)) {
      map.put(account.getId(), info);
    }
  }
}
