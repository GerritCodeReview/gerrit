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
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.ReviewerInfo;
import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.patch.AddReviewer;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  private static final String MAX_SUFFIX = "\u9fa5";

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final AccountCache accountCache;
  private final GroupControl.Factory groupControlFactory;
  private final GroupMembers.Factory groupMembersFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountControl.Factory accountControlFactory;
  private final Config cfg;
  private final GroupCache groupCache;
  private final boolean suggestAccounts;

  @Inject
  SuggestServiceImpl(final Provider<ReviewDb> schema,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final AccountCache accountCache,
      final GroupControl.Factory groupControlFactory,
      final GroupMembers.Factory groupMembersFactory,
      final IdentifiedUser.GenericFactory userFactory,
      final Provider<CurrentUser> currentUser,
      final AccountControl.Factory accountControlFactory,
      @GerritServerConfig final Config cfg, final GroupCache groupCache) {
    super(schema, currentUser);
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.accountCache = accountCache;
    this.groupControlFactory = groupControlFactory;
    this.groupMembersFactory = groupMembersFactory;
    this.userFactory = userFactory;
    this.accountControlFactory = accountControlFactory;
    this.cfg = cfg;
    this.groupCache = groupCache;

    if ("OFF".equals(cfg.getString("suggest", null, "accounts"))) {
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
        return suggestAccount(db, query, active, limit);
      }
    });
  }

  private List<AccountInfo> suggestAccount(final ReviewDb db,
      final String query, final Boolean active, final int limit)
      throws OrmException {
    if (!suggestAccounts) {
      return Collections.<AccountInfo> emptyList();
    }

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

  private void addSuggestion(Map<Account.Id, AccountInfo> map, Account account,
      AccountInfo info, Boolean active) {
    if (map.containsKey(account.getId())) {
      return;
    }
    if (active != null && active != account.isActive()) {
      return;
    }
    if (accountControlFactory.get().canSee(account)) {
      map.put(account.getId(), info);
    }
  }

  public void suggestAccountGroup(final String query, final int limit,
      final AsyncCallback<List<GroupReference>> callback) {
    run(callback, new Action<List<GroupReference>>() {
      public List<GroupReference> run(final ReviewDb db) throws OrmException {
        return suggestAccountGroup(db, query, limit);
      }
    });
  }

  private List<GroupReference> suggestAccountGroup(final ReviewDb db,
      final String query, final int limit) throws OrmException {
    final String a = query;
    final String b = a + MAX_SUFFIX;
    final int max = 10;
    final int n = limit <= 0 ? max : Math.min(limit, max);
    List<GroupReference> r = new ArrayList<GroupReference>(n);
    for (AccountGroupName group : db.accountGroupNames().suggestByName(a, b, n)) {
      try {
        if (groupControlFactory.controlFor(group.getId()).isVisible()) {
          AccountGroup g = groupCache.get(group.getId());
          if (g != null && g.getGroupUUID() != null) {
            r.add(GroupReference.forGroup(g));
          }
        }
      } catch (NoSuchGroupException e) {
        continue;
      }
    }
    return r;
  }

  @Override
  public void suggestReviewer(final Project.NameKey project,
      final String query, final int limit,
      final AsyncCallback<List<ReviewerInfo>> callback) {
    run(callback, new Action<List<ReviewerInfo>>() {
      public List<ReviewerInfo> run(final ReviewDb db) throws OrmException {
        final List<AccountInfo> suggestedAccounts =
            suggestAccount(db, query, Boolean.TRUE, limit);
        final List<ReviewerInfo> reviewer =
            new ArrayList<ReviewerInfo>(suggestedAccounts.size());
        for (final AccountInfo a : suggestedAccounts) {
          reviewer.add(new ReviewerInfo(a));
        }
        final List<GroupReference> suggestedAccountGroups =
            suggestAccountGroup(db, query, limit);
        for (final GroupReference g : suggestedAccountGroups) {
          if (suggestGroupAsReviewer(project, g)) {
            reviewer.add(new ReviewerInfo(g));
          }
        }

        Collections.sort(reviewer);
        if (reviewer.size() <= limit) {
          return reviewer;
        } else {
          return reviewer.subList(0, limit);
        }
      }
    });
  }

  private boolean suggestGroupAsReviewer(final Project.NameKey project,
      final GroupReference group) throws OrmException {
    if (!AddReviewer.isLegalReviewerGroup(group.getUUID())) {
      return false;
    }

    try {
      final Set<Account> members =
          groupMembersFactory.create().listAccounts(group.getUUID(), project);

      if (members.isEmpty()) {
        return false;
      }

      final int maxAllowed =
          cfg.getInt("addreviewer", "maxAllowed",
              AddReviewer.DEFAULT_MAX_REVIEWERS);
      if (maxAllowed > 0 && members.size() > maxAllowed) {
        return false;
      }
    } catch (NoSuchGroupException e) {
      return false;
    } catch (NoSuchProjectException e) {
      return false;
    }

    return true;
  }
}
