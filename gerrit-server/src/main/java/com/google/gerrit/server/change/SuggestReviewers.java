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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupJson.GroupBaseInfo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SuggestReviewers implements RestReadView<ChangeResource> {

  private static final String MAX_SUFFIX = "\u9fa5";
  private static final int MAX = 10;

  private final AccountInfo.Loader.Factory accountLoaderFactory;
  private final AccountControl.Factory accountControlFactory;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountCache accountCache;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final GroupBackend groupBackend;
  private final boolean suggestAccounts;
  private final int suggestFrom;
  private final int maxAllowed;
  private int limit;
  private String query;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT",
      usage = "maximum number of reviewers to list")
  public void setLimit(int l) {
    this.limit = l <= 0 ? MAX : Math.min(l, MAX);
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY",
      usage = "match reviewers query")
  public void setQuery(String q) {
    this.query = q;
  }

  @Inject
  SuggestReviewers(AccountVisibility av,
      AccountInfo.Loader.Factory accountLoaderFactory,
      AccountControl.Factory accountControlFactory,
      AccountCache accountCache,
      GroupMembers.Factory groupMembersFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      Provider<CurrentUser> currentUser,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg,
      GroupBackend groupBackend) {
    this.accountLoaderFactory = accountLoaderFactory;
    this.accountControlFactory = accountControlFactory;
    this.accountCache = accountCache;
    this.groupMembersFactory = groupMembersFactory;
    this.dbProvider = dbProvider;
    this.identifiedUserFactory = identifiedUserFactory;
    this.currentUser = currentUser;
    this.groupBackend = groupBackend;

    String suggest = cfg.getString("suggest", null, "accounts");
    if ("OFF".equalsIgnoreCase(suggest)
        || "false".equalsIgnoreCase(suggest)) {
      this.suggestAccounts = false;
    } else {
      this.suggestAccounts = (av != AccountVisibility.NONE);
    }

    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);
    this.maxAllowed = cfg.getInt("addreviewer", "maxAllowed",
        PostReviewers.DEFAULT_MAX_REVIEWERS);
  }

  private interface VisibilityControl {
    boolean isVisibleTo(Account account) throws OrmException;
  }

  @Override
  public List<SuggestedReviewerInfo> apply(ChangeResource rsrc)
      throws BadRequestException, OrmException, IOException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggestAccounts || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    VisibilityControl visibilityControl = getVisibility(rsrc);
    List<AccountInfo> suggestedAccounts = suggestAccount(visibilityControl);
    accountLoaderFactory.create(true).fill(suggestedAccounts);

    List<SuggestedReviewerInfo> reviewer = Lists.newArrayList();
    for (AccountInfo a : suggestedAccounts) {
      reviewer.add(new SuggestedReviewerInfo(a));
    }

    Project p = rsrc.getControl().getProject();
    for (GroupReference g : suggestAccountGroup(
        rsrc.getControl().getProjectControl())) {
      if (suggestGroupAsReviewer(p, g, visibilityControl)) {
        GroupBaseInfo info = new GroupBaseInfo();
        info.id = Url.encode(g.getUUID().get());
        info.name = g.getName();
        reviewer.add(new SuggestedReviewerInfo(info));
      }
    }

    Collections.sort(reviewer);
    if (reviewer.size() <= limit) {
      return reviewer;
    } else {
      return reviewer.subList(0, limit);
    }
  }

  private VisibilityControl getVisibility(final ChangeResource rsrc) {
    if (rsrc.getControl().getRefControl().isVisibleByRegisteredUsers()) {
      return new VisibilityControl() {
        @Override
        public boolean isVisibleTo(Account account) throws OrmException {
          return true;
        }
      };
    } else {
      return new VisibilityControl() {
        @Override
        public boolean isVisibleTo(Account account) throws OrmException {
          IdentifiedUser who =
              identifiedUserFactory.create(dbProvider, account.getId());
          // we can't use changeControl directly as it won't suggest reviewers
          // to drafts
          return rsrc.getControl().forUser(who).isRefVisible();
        }
      };
    }
  }

  private List<GroupReference> suggestAccountGroup(ProjectControl ctl) {
    return Lists.newArrayList(
        Iterables.limit(groupBackend.suggest(query, ctl), limit));
  }

  private List<AccountInfo> suggestAccount(VisibilityControl visibilityControl)
      throws OrmException {
    String a = query;
    String b = a + MAX_SUFFIX;

    LinkedHashMap<Account.Id, AccountInfo> r = Maps.newLinkedHashMap();
    for (Account p : dbProvider.get().accounts()
        .suggestByFullName(a, b, limit)) {
      addSuggestion(r, p, new AccountInfo(p.getId()), visibilityControl);
    }

    if (r.size() < limit) {
      for (Account p : dbProvider.get().accounts()
          .suggestByPreferredEmail(a, b, limit - r.size())) {
        addSuggestion(r, p, new AccountInfo(p.getId()), visibilityControl);
      }
    }

    if (r.size() < limit) {
      for (AccountExternalId e : dbProvider.get().accountExternalIds()
          .suggestByEmailAddress(a, b, limit - r.size())) {
        if (!r.containsKey(e.getAccountId())) {
          Account p = accountCache.get(e.getAccountId()).getAccount();
          AccountInfo info = new AccountInfo(p.getId());
          addSuggestion(r, p, info, visibilityControl);
        }
      }
    }

    return Lists.newArrayList(r.values());
  }

  private void addSuggestion(Map<Account.Id, AccountInfo> map, Account account,
      AccountInfo info, VisibilityControl visibilityControl)
      throws OrmException {
    if (!map.containsKey(account.getId())
        && account.isActive()
        // Can the suggestion see the change?
        && visibilityControl.isVisibleTo(account)
        // Can the account see the current user?
        && accountControlFactory.get().canSee(account)) {
      map.put(account.getId(), info);
    }
  }

  private boolean suggestGroupAsReviewer(Project project,
      GroupReference group, VisibilityControl visibilityControl)
      throws OrmException, IOException {
    if (!PostReviewers.isLegalReviewerGroup(group.getUUID())) {
      return false;
    }

    try {
      Set<Account> members = groupMembersFactory
          .create(currentUser.get())
          .listAccounts(group.getUUID(), project.getNameKey());

      if (members.isEmpty()) {
        return false;
      }

      if (maxAllowed > 0 && members.size() > maxAllowed) {
        return false;
      }

      // require that at least one member in the group can see the change
      for (Account account : members) {
        if (visibilityControl.isVisibleTo(account)) {
          return true;
        }
      }
    } catch (NoSuchGroupException e) {
      return false;
    } catch (NoSuchProjectException e) {
      return false;
    }

    return false;
  }

  static class SuggestedReviewerInfo implements Comparable<SuggestedReviewerInfo> {
    String kind = "gerritcodereview#suggestedreviewer";
    AccountInfo account;
    GroupBaseInfo group;

    SuggestedReviewerInfo(AccountInfo a) {
      this.account = a;
    }

    SuggestedReviewerInfo(GroupBaseInfo g) {
      this.group = g;
    }

    @Override
    public int compareTo(SuggestedReviewerInfo o) {
      return getSortValue().compareTo(o.getSortValue());
    }

    private String getSortValue() {
      return account != null
          ? Objects.firstNonNull(account.email,
              Strings.nullToEmpty(account.name))
          : Strings.nullToEmpty(group.name);
    }
  }
}
