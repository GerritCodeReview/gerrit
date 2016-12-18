// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.SuggestReviewers;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.QueryResult;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
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
  private final AccountIndexCollection indexes;
  private final AccountQueryBuilder queryBuilder;
  private final AccountQueryProcessor queryProcessor;
  private final AccountControl accountControl;
  private final Provider<ReviewDb> dbProvider;
  private final GroupBackend groupBackend;
  private final GroupMembers.Factory groupMembersFactory;
  private final Provider<CurrentUser> currentUser;

  @Inject
  ReviewersUtil(AccountLoader.Factory accountLoaderFactory,
      AccountCache accountCache,
      AccountIndexCollection indexes,
      AccountQueryBuilder queryBuilder,
      AccountQueryProcessor queryProcessor,
      AccountControl.Factory accountControlFactory,
      Provider<ReviewDb> dbProvider,
      GroupBackend groupBackend,
      GroupMembers.Factory groupMembersFactory,
      Provider<CurrentUser> currentUser) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.accountLoader = accountLoaderFactory.create(fillOptions);
    this.accountCache = accountCache;
    this.indexes = indexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
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
      SuggestReviewers suggestReviewers, ProjectControl projectControl,
      VisibilityControl visibilityControl)
      throws IOException, OrmException, BadRequestException {
    String query = suggestReviewers.getQuery();
    boolean suggestAccounts = suggestReviewers.getSuggestAccounts();
    int suggestFrom = suggestReviewers.getSuggestFrom();
    int limit = suggestReviewers.getLimit();

    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggestAccounts || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    Collection<AccountInfo> suggestedAccounts =
        suggestAccounts(suggestReviewers, visibilityControl);

    List<SuggestedReviewerInfo> reviewer = new ArrayList<>();
    for (AccountInfo a : suggestedAccounts) {
      SuggestedReviewerInfo info = new SuggestedReviewerInfo();
      info.account = a;
      info.count = 1;
      reviewer.add(info);
    }

    for (GroupReference g : suggestAccountGroup(suggestReviewers, projectControl)) {
      GroupAsReviewer result = suggestGroupAsReviewer(
          suggestReviewers, projectControl.getProject(), g, visibilityControl);
      if (result.allowed || result.allowedWithConfirmation) {
        GroupBaseInfo info = new GroupBaseInfo();
        info.id = Url.encode(g.getUUID().get());
        info.name = g.getName();
        SuggestedReviewerInfo suggestedReviewerInfo = new SuggestedReviewerInfo();
        suggestedReviewerInfo.group = info;
        suggestedReviewerInfo.count = result.size;
        if (result.allowedWithConfirmation) {
          suggestedReviewerInfo.confirm = true;
        }
        reviewer.add(suggestedReviewerInfo);
      }
    }

    reviewer = ORDERING.immutableSortedCopy(reviewer);
    if (reviewer.size() <= limit) {
      return reviewer;
    }
    return reviewer.subList(0, limit);
  }

  private Collection<AccountInfo> suggestAccounts(SuggestReviewers suggestReviewers,
      VisibilityControl visibilityControl)
      throws OrmException {
    AccountIndex searchIndex = indexes.getSearchIndex();
    if (searchIndex != null) {
      return suggestAccountsFromIndex(suggestReviewers, visibilityControl);
    }
    return suggestAccountsFromDb(suggestReviewers, visibilityControl);
  }

  private Collection<AccountInfo> suggestAccountsFromIndex(
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl)
      throws OrmException {
    try {
      Map<Account.Id, AccountInfo> matches = new LinkedHashMap<>();
      QueryResult<AccountState> result = queryProcessor
          .setLimit(suggestReviewers.getLimit())
          .query(queryBuilder.defaultQuery(suggestReviewers.getQuery()));
      for (AccountState accountState : result.entities()) {
        Account.Id id = accountState.getAccount().getId();
        if(visibilityControl.isVisibleTo(id)) {
          matches.put(id, accountLoader.get(id));
        }
      }

      accountLoader.fill();

      return matches.values();
    } catch (QueryParseException e) {
      return ImmutableList.of();
    }
  }

  private Collection<AccountInfo> suggestAccountsFromDb(
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl)
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
        // Can the current user see the account?
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

  private static class GroupAsReviewer {
    boolean allowed;
    boolean allowedWithConfirmation;
    int size;
  }

  private GroupAsReviewer suggestGroupAsReviewer(SuggestReviewers suggestReviewers,
      Project project, GroupReference group,
      VisibilityControl visibilityControl) throws OrmException, IOException {
    GroupAsReviewer result = new GroupAsReviewer();
    int maxAllowed = suggestReviewers.getMaxAllowed();
    int maxAllowedWithoutConfirmation =
        suggestReviewers.getMaxAllowedWithoutConfirmation();

    if (!PostReviewers.isLegalReviewerGroup(group.getUUID())) {
      return result;
    }

    try {
      Set<Account> members = groupMembersFactory
          .create(currentUser.get())
          .listAccounts(group.getUUID(), project.getNameKey());

      if (members.isEmpty()) {
        return result;
      }

      result.size = members.size();
      if (maxAllowed > 0 && result.size > maxAllowed) {
        return result;
      }

      boolean needsConfirmation = result.size > maxAllowedWithoutConfirmation;

      // require that at least one member in the group can see the change
      for (Account account : members) {
        if (visibilityControl.isVisibleTo(account.getId())) {
          if (needsConfirmation) {
            result.allowedWithConfirmation = true;
          } else {
            result.allowed = true;
          }
          return result;
        }
      }
    } catch (NoSuchGroupException e) {
      return result;
    } catch (NoSuchProjectException e) {
      return result;
    }

    return result;
  }
}
