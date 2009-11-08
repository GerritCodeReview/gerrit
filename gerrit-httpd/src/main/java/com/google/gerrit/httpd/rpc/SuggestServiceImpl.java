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
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  private static final String MAX_SUFFIX = "\u9fa5";

  private final ProjectCache projectCache;
  private final AccountCache accountCache;
  private final Provider<CurrentUser> currentUser;

  @Inject
  SuggestServiceImpl(final Provider<ReviewDb> schema,
      final ProjectCache projectCache, final AccountCache accountCache,
      final Provider<CurrentUser> currentUser) {
    super(schema, currentUser);
    this.projectCache = projectCache;
    this.accountCache = accountCache;
    this.currentUser = currentUser;
  }

  public void suggestProjectNameKey(final String query, final int limit,
      final AsyncCallback<List<Project.NameKey>> callback) {
    run(callback, new Action<List<Project.NameKey>>() {
      public List<Project.NameKey> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);

        final CurrentUser user = currentUser.get();
        final List<Project.NameKey> r = new ArrayList<Project.NameKey>();
        for (final Project p : db.projects().suggestByName(a, b, n)) {
          final ProjectState e = projectCache.get(p.getNameKey());
          if (e != null && e.controlFor(user).isVisible()) {
            r.add(p.getNameKey());
          }
        }
        return r;
      }
    });
  }

  public void suggestAccount(final String query, final int limit,
      final AsyncCallback<List<AccountInfo>> callback) {
    run(callback, new Action<List<AccountInfo>>() {
      public List<AccountInfo> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);

        final LinkedHashMap<Account.Id, AccountInfo> r =
            new LinkedHashMap<Account.Id, AccountInfo>();
        for (final Account p : db.accounts().suggestByFullName(a, b, n)) {
          r.put(p.getId(), new AccountInfo(p));
        }
        if (r.size() < n) {
          for (final Account p : db.accounts().suggestByPreferredEmail(a, b,
              n - r.size())) {
            r.put(p.getId(), new AccountInfo(p));
          }
        }
        if (r.size() < n) {
          for (final AccountExternalId e : db.accountExternalIds()
              .suggestByEmailAddress(a, b, n - r.size())) {
            if (!r.containsKey(e.getAccountId())) {
              final Account p = accountCache.get(e.getAccountId()).getAccount();
              final AccountInfo info = new AccountInfo(p);
              info.setPreferredEmail(e.getEmailAddress());
              r.put(e.getAccountId(), info);
            }
          }
        }
        return new ArrayList<AccountInfo>(r.values());
      }
    });
  }

  public void suggestAccountGroup(final String query, final int limit,
      final AsyncCallback<List<AccountGroup>> callback) {
    run(callback, new Action<List<AccountGroup>>() {
      public List<AccountGroup> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);
        return db.accountGroups().suggestByName(a, b, n).toList();
      }
    });
  }
}
