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

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupBaseInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReviewersUtil {
  private static final String MAX_SUFFIX = "\u9fa5";
  private static final Ordering<SuggestedReviewerInfo> ORDERING =
      Ordering.natural().onResultOf(new Function<SuggestedReviewerInfo, String>() {
        @Nullable
        @Override
        public String apply(@Nullable SuggestedReviewerInfo suggestedReviewerInfo) {
          if (suggestedReviewerInfo == null) {
            return null;
          }
          return suggestedReviewerInfo.account != null
              ? MoreObjects.firstNonNull(suggestedReviewerInfo.account.email,
              Strings.nullToEmpty(suggestedReviewerInfo.account.name))
              : Strings.nullToEmpty(suggestedReviewerInfo.group.name);
        }
      });
  private final AccountLoader accountLoader;
  private final AccountCache accountCache;
  private final ReviewerSuggestionCache reviewerSuggestionCache;
  private final AccountControl accountControl;
  private final Provider<ReviewDb> dbProvider;
  private final GroupBackend groupBackend;
  private final GroupMembers.Factory groupMembersFactory;
  private final Provider<CurrentUser> currentUser;

  @Inject
  ReviewersUtil(AccountLoader.Factory accountLoaderFactory,
      AccountCache accountCache,
      ReviewerSuggestionCache reviewerSuggestionCache,
      AccountControl.Factory accountControlFactory,
      Provider<ReviewDb> dbProvider,
      GroupBackend groupBackend,
      GroupMembers.Factory groupMembersFactory,
      Provider<CurrentUser> currentUser) {
    this.accountLoader = accountLoaderFactory.create(true);
    this.accountCache = accountCache;
    this.reviewerSuggestionCache = reviewerSuggestionCache;
    this.accountControl = accountControlFactory.get();
    this.dbProvider = dbProvider;
    this.groupBackend = groupBackend;
    this.groupMembersFactory = groupMembersFactory;
    this.currentUser = currentUser;
  }

  public interface VisibilityControl {
    boolean isVisibleTo(Account.Id account) throws OrmException;
  }

  public List<SuggestedReviewerInfo> suggestReviewers(
      SuggestReviewers suggestReviewers, Project project,
      ProjectControl projectControl, VisibilityControl visibilityControl)
      throws IOException, OrmException, BadRequestException {
    String query = suggestReviewers.getQuery();
    boolean suggestAccounts = suggestReviewers.getSuggestAccounts();
    int suggestFrom = suggestReviewers.getSuggestFrom();
    boolean useFullTextSearch = suggestReviewers.getUseFullTextSearch();
    int limit = suggestReviewers.getLimit();

    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggestAccounts || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    List<AccountInfo> suggestedAccounts;
    if (useFullTextSearch) {
      suggestedAccounts = suggestAccountFullTextSearch(suggestReviewers, visibilityControl);
    } else {
      suggestedAccounts = suggestAccount(suggestReviewers, visibilityControl);
    }

    List<SuggestedReviewerInfo> reviewer = Lists.newArrayList();
    for (AccountInfo a : suggestedAccounts) {
      SuggestedReviewerInfo info = new SuggestedReviewerInfo();
      info.account = a;
      reviewer.add(info);
    }

    for (GroupReference g : suggestAccountGroup(suggestReviewers, projectControl)) {
      if (suggestGroupAsReviewer(suggestReviewers, project, g, visibilityControl)) {
        GroupBaseInfo info = new GroupBaseInfo();
        info.id = Url.encode(g.getUUID().get());
        info.name = g.getName();
        SuggestedReviewerInfo suggestedReviewerInfo = new SuggestedReviewerInfo();
        suggestedReviewerInfo.group = info;
        reviewer.add(suggestedReviewerInfo);
      }
    }

    reviewer = ORDERING.immutableSortedCopy(reviewer);
    if (reviewer.size() <= limit) {
      return reviewer;
    } else {
      return reviewer.subList(0, limit);
    }
  }

  private List<AccountInfo> suggestAccountFullTextSearch(
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl)
          throws IOException, OrmException {
    List<AccountInfo> results = reviewerSuggestionCache.search(
        suggestReviewers.getQuery(), suggestReviewers.getFullTextMaxMatches());

    Iterator<AccountInfo> it = results.iterator();
    while (it.hasNext()) {
      Account.Id accountId = new Account.Id(it.next()._accountId);
      if (!(visibilityControl.isVisibleTo(accountId)
          && accountControl.canSee(accountId))) {
        it.remove();
      }
    }

    return results;
  }

  private List<AccountInfo> suggestAccount(SuggestReviewers suggestReviewers,
      VisibilityControl visibilityControl)
      throws OrmException {
    String query = suggestReviewers.getQuery();
    int limit = suggestReviewers.getLimit();

    String a = query;
    String b = a + MAX_SUFFIX;

    Map<Account.Id, AccountInfo> r = new LinkedHashMap<>();
    Map<Account.Id, String> queryEmail = new HashMap<>();

    for (Account p : dbProvider.get().accounts()
        .suggestByFullName(a, b, limit)) {
      if (p.isActive()) {
        addSuggestion(r, p.getId(), visibilityControl);
      }
    }

    if (r.size() < limit) {
      for (Account p : dbProvider.get().accounts()
          .suggestByPreferredEmail(a, b, limit - r.size())) {
        if (p.isActive()) {
          addSuggestion(r, p.getId(), visibilityControl);
        }
      }
    }

    if (r.size() < limit) {
      for (AccountExternalId e : dbProvider.get().accountExternalIds()
          .suggestByEmailAddress(a, b, limit - r.size())) {
        if (!r.containsKey(e.getAccountId())) {
          Account p = accountCache.get(e.getAccountId()).getAccount();
          if (p.isActive()) {
            if (addSuggestion(r, p.getId(), visibilityControl)) {
              queryEmail.put(e.getAccountId(), e.getEmailAddress());
            }
          }
        }
      }
    }

    accountLoader.fill();
    for (Map.Entry<Account.Id, String> p : queryEmail.entrySet()) {
      AccountInfo info = r.get(p.getKey());
      if (info != null) {
        info.email = p.getValue();
      }
    }
    return new ArrayList<>(r.values());
  }

  private boolean addSuggestion(Map<Account.Id, AccountInfo> map,
      Account.Id account, VisibilityControl visibilityControl)
      throws OrmException {
    if (!map.containsKey(account)
        // Can the suggestion see the change?
        && visibilityControl.isVisibleTo(account)
        // Can the account see the current user?
        && accountControl.canSee(account)) {
      map.put(account, accountLoader.get(account));
      return true;
    }
    return false;
  }

  private List<GroupReference> suggestAccountGroup(
      SuggestReviewers suggestReviewers, ProjectControl ctl) {
    return Lists.newArrayList(
        Iterables.limit(groupBackend.suggest(suggestReviewers.getQuery(), ctl),
            suggestReviewers.getLimit()));
  }

  private boolean suggestGroupAsReviewer(SuggestReviewers suggestReviewers,
      Project project, GroupReference group,
      VisibilityControl visibilityControl) throws OrmException, IOException {
    int maxAllowed = suggestReviewers.getMaxAllowed();

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
        if (visibilityControl.isVisibleTo(account.getId())) {
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
}
