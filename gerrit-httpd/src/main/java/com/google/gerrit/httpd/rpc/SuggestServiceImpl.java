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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

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

        List<Future<ProjectState>> want = Lists.newArrayList();
        for (Project p : db.projects().suggestByName(a, b, n)) {
          want.add(projectCache.get(p.getNameKey()));
        }

        CurrentUser user = currentUser.get();
        List<Project.NameKey> res = Lists.newArrayList();
        for (Future<ProjectState> f : want) {
          ProjectState e = FutureUtil.getOrNull(f);
          if (e != null && e.controlFor(user).isVisible()) {
            res.add(e.getProject().getNameKey());
          }
        }
        return res;
      }
    });
  }

  public void suggestAccount(final String query, final Boolean active,
      final int limit, final AsyncCallback<List<AccountInfo>> callback) {
    run(callback, new Action<List<AccountInfo>>() {
      public List<AccountInfo> run(final ReviewDb db) throws OrmException {
        final String a = query;
        final String b = a + MAX_SUFFIX;
        final int max = 10;
        final int n = limit <= 0 ? max : Math.min(limit, max);

        LinkedHashMap<Account.Id, AccountInfo> res = Maps.newLinkedHashMap();
        for (Account p : db.accounts().suggestByFullName(a, b, n)) {
          if (active == null || active == p.isActive()) {
            res.put(p.getId(), new AccountInfo(p));
          }
        }
        if (res.size() < n) {
          for (Account p : db.accounts().suggestByPreferredEmail(a, b,
              n - res.size())) {
            if (active == null || active == p.isActive()) {
              res.put(p.getId(), new AccountInfo(p));
            }
          }
        }
        if (res.size() < n) {
          Map<String, Future<Account>> want = Maps.newHashMap();
          for (AccountExternalId e : db.accountExternalIds()
              .suggestByEmailAddress(a, b, n - res.size())) {
            if (!res.containsKey(e.getAccountId())) {
              want.put(e.getEmailAddress(), //
                  accountCache.getAccount(e.getAccountId()));
            }
          }

          for (Map.Entry<String, Future<Account>> ent : want.entrySet()) {
            Account p = FutureUtil.get(ent.getValue());
            if (active == null || active == p.isActive()) {
              AccountInfo info = new AccountInfo(p);
              info.setPreferredEmail(ent.getKey());
              res.put(p.getId(), info);
            }
          }
        }
        return new ArrayList<AccountInfo>(res.values());
      }
    });
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
