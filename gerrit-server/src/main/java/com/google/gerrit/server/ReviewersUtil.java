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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupBaseInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
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
import com.google.gerrit.server.notedb.ChangeNotes;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReviewersUtil {
  private static final String MAX_SUFFIX = "\u9fa5";
  // Generate a candidate list at 3x the size of what the user wants to see to
  // give the ranking algorithm a good set of candidates it can work with
  private static final int CANDIDATE_LIST_MULTIPLIER = 3;

  private final AccountCache accountCache;
  private final AccountControl accountControl;
  private final AccountIndexCollection accountIndexes;
  private final AccountLoader accountLoader;
  private final AccountQueryBuilder accountQueryBuilder;
  private final AccountQueryProcessor accountQueryProcessor;
  private final GroupBackend groupBackend;
  private final GroupMembers.Factory groupMembersFactory;
  private final Provider<CurrentUser> currentUser;
  private final Provider<ReviewDb> dbProvider;
  private final ReviewerRecommender reviewerRecommender;

  @Inject
  ReviewersUtil(
      AccountCache accountCache,
      AccountControl.Factory accountControlFactory,
      AccountIndexCollection accountIndexes,
      AccountLoader.Factory accountLoaderFactory,
      AccountQueryBuilder accountQueryBuilder,
      AccountQueryProcessor accountQueryProcessor,
      GroupBackend groupBackend,
      GroupMembers.Factory groupMembersFactory,
      Provider<CurrentUser> currentUser,
      Provider<ReviewDb> dbProvider,
      ReviewerRecommender reviewerRecommender) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.accountCache = accountCache;
    this.accountControl = accountControlFactory.get();
    this.accountIndexes = accountIndexes;
    this.accountLoader = accountLoaderFactory.create(fillOptions);
    this.accountQueryBuilder = accountQueryBuilder;
    this.accountQueryProcessor = accountQueryProcessor;
    this.currentUser = currentUser;
    this.dbProvider = dbProvider;
    this.groupBackend = groupBackend;
    this.groupMembersFactory = groupMembersFactory;
    this.reviewerRecommender = reviewerRecommender;
  }

  public interface VisibilityControl {
    boolean isVisibleTo(Account.Id account) throws OrmException;
  }

  public List<SuggestedReviewerInfo> suggestReviewers(
      ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers,
      ProjectControl projectControl,
      VisibilityControl visibilityControl,
      boolean excludeGroups)
      throws IOException, OrmException {
    String query = suggestReviewers.getQuery();
    int limit = suggestReviewers.getLimit();

    if (!suggestReviewers.getSuggestAccounts()) {
      return Collections.emptyList();
    }

    List<Account.Id> candidateList = new ArrayList<>();
    if (!Strings.isNullOrEmpty(query)) {
      candidateList = suggestAccounts(suggestReviewers, visibilityControl);
    }

    List<Account.Id> sortedRecommendations =
        reviewerRecommender.suggestReviewers(
            changeNotes, suggestReviewers, projectControl, candidateList);

    // Populate AccountInfo
    List<SuggestedReviewerInfo> reviewer = new ArrayList<>();
    for (Account.Id id : sortedRecommendations) {
      AccountInfo account = accountLoader.get(id);
      if (account != null) {
        SuggestedReviewerInfo info = new SuggestedReviewerInfo();
        info.account = account;
        info.count = 1;
        reviewer.add(info);
      }
    }
    accountLoader.fill();

    if (!excludeGroups && !Strings.isNullOrEmpty(query)) {
      for (GroupReference g : suggestAccountGroup(suggestReviewers, projectControl)) {
        GroupAsReviewer result =
            suggestGroupAsReviewer(
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
          // Always add groups at the end as individual accounts are usually
          // more important
          reviewer.add(suggestedReviewerInfo);
        }
      }
    }

    if (reviewer.size() <= limit) {
      return reviewer;
    }
    return reviewer.subList(0, limit);
  }

  private List<Account.Id> suggestAccounts(
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl) throws OrmException {
    AccountIndex searchIndex = accountIndexes.getSearchIndex();
    if (searchIndex != null) {
      return suggestAccountsFromIndex(suggestReviewers);
    }
    return suggestAccountsFromDb(suggestReviewers, visibilityControl);
  }

  private List<Account.Id> suggestAccountsFromIndex(SuggestReviewers suggestReviewers)
      throws OrmException {
    try {
      Set<Account.Id> matches = new HashSet<>();
      QueryResult<AccountState> result =
          accountQueryProcessor
              .setLimit(suggestReviewers.getLimit() * CANDIDATE_LIST_MULTIPLIER)
              .query(accountQueryBuilder.defaultQuery(suggestReviewers.getQuery()));
      for (AccountState accountState : result.entities()) {
        Account.Id id = accountState.getAccount().getId();
        matches.add(id);
      }
      return new ArrayList<>(matches);
    } catch (QueryParseException e) {
      return ImmutableList.of();
    }
  }

  private List<Account.Id> suggestAccountsFromDb(
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl) throws OrmException {
    String query = suggestReviewers.getQuery();
    int limit = suggestReviewers.getLimit() * CANDIDATE_LIST_MULTIPLIER;

    String a = query;
    String b = a + MAX_SUFFIX;

    Set<Account.Id> r = new HashSet<>();

    for (Account p : dbProvider.get().accounts().suggestByFullName(a, b, limit)) {
      if (p.isActive()) {
        addSuggestion(r, p.getId(), visibilityControl);
      }
    }

    if (r.size() < limit) {
      for (Account p :
          dbProvider.get().accounts().suggestByPreferredEmail(a, b, limit - r.size())) {
        if (p.isActive()) {
          addSuggestion(r, p.getId(), visibilityControl);
        }
      }
    }

    if (r.size() < limit) {
      for (AccountExternalId e :
          dbProvider.get().accountExternalIds().suggestByEmailAddress(a, b, limit - r.size())) {
        if (!r.contains(e.getAccountId())) {
          Account p = accountCache.get(e.getAccountId()).getAccount();
          if (p.isActive()) {
            addSuggestion(r, p.getId(), visibilityControl);
          }
        }
      }
    }
    return new ArrayList<>(r);
  }

  private boolean addSuggestion(
      Set<Account.Id> map, Account.Id account, VisibilityControl visibilityControl)
      throws OrmException {
    if (!map.contains(account)
        // Can the suggestion see the change?
        && visibilityControl.isVisibleTo(account)
        // Can the current user see the account?
        && accountControl.canSee(account)) {
      map.add(account);
      return true;
    }
    return false;
  }

  private List<GroupReference> suggestAccountGroup(
      SuggestReviewers suggestReviewers, ProjectControl ctl) {
    return Lists.newArrayList(
        Iterables.limit(
            groupBackend.suggest(suggestReviewers.getQuery(), ctl), suggestReviewers.getLimit()));
  }

  private static class GroupAsReviewer {
    boolean allowed;
    boolean allowedWithConfirmation;
    int size;
  }

  private GroupAsReviewer suggestGroupAsReviewer(
      SuggestReviewers suggestReviewers,
      Project project,
      GroupReference group,
      VisibilityControl visibilityControl)
      throws OrmException, IOException {
    GroupAsReviewer result = new GroupAsReviewer();
    int maxAllowed = suggestReviewers.getMaxAllowed();
    int maxAllowedWithoutConfirmation = suggestReviewers.getMaxAllowedWithoutConfirmation();

    if (!PostReviewers.isLegalReviewerGroup(group.getUUID())) {
      return result;
    }

    try {
      Set<Account> members =
          groupMembersFactory
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
