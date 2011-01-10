// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  private static final String MAX_SUFFIX = "\u9fa5";

  private final ProjectControl.Factory projectControlFactory;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;

  @Inject
  SuggestServiceImpl(final Provider<ReviewDb> schema,
      final ProjectControl.Factory projectControlFactory,
      final AccountCache accountCache, final ProjectCache projectCache,
      final Provider<CurrentUser> currentUser) {
    super(schema, currentUser);
    this.projectControlFactory = projectControlFactory;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
  }

  public void suggestProjectNameKey(final String query, final int limit,
      final AsyncCallback<List<Project.NameKey>> callback) {
    final int max = 10;
    final int n = limit <= 0 ? max : Math.min(limit, max);

    final List<Project.NameKey> r = new ArrayList<Project.NameKey>(n);
    for (final Project.NameKey nameKey : projectCache.byName(query)) {
      final ProjectControl ctl;
      try {
        ctl = projectControlFactory.validateFor(nameKey);
      } catch (NoSuchProjectException e) {
        continue;
      }

      r.add(ctl.getProject().getNameKey());
      if (r.size() == n) {
        break;
      }
    }
    callback.onSuccess(r);
  }

  public void suggestAccount(final String query, final Boolean active,
      final int limit, final AsyncCallback<List<AccountInfo>> callback) {
    run(callback, new Action<List<AccountInfo>>() {
      public List<AccountInfo> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);

        final LinkedHashMap<Account.Id, AccountInfo> r =
            new LinkedHashMap<Account.Id, AccountInfo>();
        for (final Account p : db.accounts().suggestByFullName(a, b, n)) {
          addSuggestion(r, p, new AccountInfo(p), active);
        }
        if (r.size() < n) {
          for (final Account p : db.accounts().suggestByPreferredEmail(a, b,
              n - r.size())) {
            addSuggestion(r, p, new AccountInfo(p), active);
          }
        }
        if (r.size() < n) {
          for (final AccountExternalId e : db.accountExternalIds()
              .suggestByEmailAddress(a, b, n - r.size())) {
            if (!r.containsKey(e.getAccountId())) {
              final Account p = accountCache.get(e.getAccountId()).getAccount();
              final AccountInfo info = new AccountInfo(p);
              info.setPreferredEmail(e.getEmailAddress());
              addSuggestion(r, p, info, active);
            }
          }
        }
        return new ArrayList<AccountInfo>(r.values());
      }
    });
  }

  private void addSuggestion(Map map, Account account, AccountInfo info,
      Boolean active) {
    if (active == null || active == account.isActive()) {
      map.put(account.getId(), info);
    }
  }

  public void suggestAccountGroup(final String query, final int limit,
      final AsyncCallback<List<AccountGroupName>> callback) {
    run(callback, new Action<List<AccountGroupName>>() {
      public List<AccountGroupName> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);
        return db.accountGroupNames().suggestByName(a, b, n).toList();
      }
    });
  }
}
