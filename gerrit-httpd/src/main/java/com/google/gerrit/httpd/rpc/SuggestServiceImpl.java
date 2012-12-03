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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.ReviewerInfo;
import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  private static final String MAX_SUFFIX = "\u9fa5";

  private final Provider<ReviewDb> reviewDbProvider;
  private final AccountCache accountCache;
  private final GroupMembers.Factory groupMembersFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final AccountControl.Factory accountControlFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final Config cfg;
  private final GroupBackend groupBackend;
  private final boolean suggestAccounts;

  @Inject
  SuggestServiceImpl(final Provider<ReviewDb> schema,
      final AccountCache accountCache,
      final GroupMembers.Factory groupMembersFactory,
      final Provider<CurrentUser> currentUser,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final AccountControl.Factory accountControlFactory,
      final ChangeControl.Factory changeControlFactory,
      final ProjectControl.Factory projectControlFactory,
      @GerritServerConfig final Config cfg, final GroupBackend groupBackend) {
    super(schema, currentUser);
    this.reviewDbProvider = schema;
    this.accountCache = accountCache;
    this.groupMembersFactory = groupMembersFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.accountControlFactory = accountControlFactory;
    this.changeControlFactory = changeControlFactory;
    this.projectControlFactory = projectControlFactory;
    this.cfg = cfg;
    this.groupBackend = groupBackend;

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

  private interface VisibilityControl {
    boolean isVisible(Account account) throws OrmException;
  }

  public void suggestAccount(final String query, final Boolean active,
      final int limit, final AsyncCallback<List<AccountInfo>> callback) {
    run(callback, new Action<List<AccountInfo>>() {
      public List<AccountInfo> run(final ReviewDb db) throws OrmException {
        return suggestAccount(db, query, active, limit, new VisibilityControl() {
          @Override
          public boolean isVisible(Account account) throws OrmException {
            return accountControlFactory.get().canSee(account);
          }
        });
      }
    });
  }

  private List<AccountInfo> suggestAccount(final ReviewDb db,
      final String query, final Boolean active, final int limit,
      VisibilityControl visibilityControl)
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
      addSuggestion(r, p, new AccountInfo(p), active, visibilityControl);
    }
    if (r.size() < n) {
      for (final Account p : db.accounts().suggestByPreferredEmail(a, b,
          n - r.size())) {
        addSuggestion(r, p, new AccountInfo(p), active, visibilityControl);
      }
    }
    if (r.size() < n) {
      for (final AccountExternalId e : db.accountExternalIds()
          .suggestByEmailAddress(a, b, n - r.size())) {
        if (!r.containsKey(e.getAccountId())) {
          final Account p = accountCache.get(e.getAccountId()).getAccount();
          final AccountInfo info = new AccountInfo(p);
          info.setPreferredEmail(e.getEmailAddress());
          addSuggestion(r, p, info, active, visibilityControl);
        }
      }
    }
    return new ArrayList<AccountInfo>(r.values());
  }

  private void addSuggestion(Map<Account.Id, AccountInfo> map, Account account,
      AccountInfo info, Boolean active, VisibilityControl visibilityControl)
      throws OrmException {
    if (map.containsKey(account.getId())) {
      return;
    }
    if (active != null && active != account.isActive()) {
      return;
    }
    if (visibilityControl.isVisible(account)) {
      map.put(account.getId(), info);
    }
  }

  public void suggestAccountGroup(final String query, final int limit,
      final AsyncCallback<List<GroupReference>> callback) {
    suggestAccountGroupForProject(null, query, limit, callback);
  }

  public void suggestAccountGroupForProject(final Project.NameKey project,
      final String query, final int limit,
      final AsyncCallback<List<GroupReference>> callback) {
    run(callback, new Action<List<GroupReference>>() {
      public List<GroupReference> run(final ReviewDb db) {
        ProjectControl projectControl = null;
        if (project != null) {
          try {
            projectControl = projectControlFactory.controlFor(project);
          } catch (NoSuchProjectException e) {
            return Collections.emptyList();
          }
        }
        return suggestAccountGroup(projectControl, query, limit);
      }
    });
  }

  private List<GroupReference> suggestAccountGroup(
      @Nullable final ProjectControl projectControl, final String query, final int limit) {
    final int n = limit <= 0 ? 10 : Math.min(limit, 10);
    final Project project = projectControl != null ? projectControl.getProject() : null;
    return Lists.newArrayList(Iterables.limit(groupBackend.suggest(query, project), n));
  }

  @Override
  public void suggestReviewer(Project.NameKey project, String query, int limit,
      AsyncCallback<List<ReviewerInfo>> callback) {
    // The RPC is deprecated, but return an empty list for RPC API compatibility.
    callback.onSuccess(Collections.<ReviewerInfo>emptyList());
  }

  @Override
  public void suggestChangeReviewer(final Change.Id change,
      final String query, final int limit,
      final AsyncCallback<List<ReviewerInfo>> callback) {
    run(callback, new Action<List<ReviewerInfo>>() {
      public List<ReviewerInfo> run(final ReviewDb db) throws OrmException {
        final ChangeControl changeControl;
        try {
          changeControl = changeControlFactory.controlFor(change);
        } catch (NoSuchChangeException e) {
          return Collections.emptyList();
        }

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
                  identifiedUserFactory.create(reviewDbProvider, account.getId());
              // we can't use changeControl directly as it won't suggest reviewers
              // to drafts
              return changeControl.forUser(who).isRefVisible();
            }
          };
        }

        final List<AccountInfo> suggestedAccounts =
            suggestAccount(db, query, Boolean.TRUE, limit, visibilityControl);
        final List<ReviewerInfo> reviewer =
            new ArrayList<ReviewerInfo>(suggestedAccounts.size());
        for (final AccountInfo a : suggestedAccounts) {
          reviewer.add(new ReviewerInfo(a));
        }
        final List<GroupReference> suggestedAccountGroups =
            suggestAccountGroup(changeControl.getProjectControl(), query, limit);
        for (final GroupReference g : suggestedAccountGroups) {
          if (suggestGroupAsReviewer(changeControl.getProject().getNameKey(), g)) {
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
    if (!PostReviewers.isLegalReviewerGroup(group.getUUID())) {
      return false;
    }

    try {
      final Set<Account> members = groupMembersFactory.create(getCurrentUser())
          .listAccounts(group.getUUID(), project);

      if (members.isEmpty()) {
        return false;
      }

      final int maxAllowed =
          cfg.getInt("addreviewer", "maxAllowed",
              PostReviewers.DEFAULT_MAX_REVIEWERS);
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
