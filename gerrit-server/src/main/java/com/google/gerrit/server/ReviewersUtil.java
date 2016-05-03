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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupBaseInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
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
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.change.SuggestReviewers;
import com.google.gerrit.server.change.SuggestedReviewer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.QueryResult;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.commons.lang.mutable.MutableDouble;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReviewersUtil {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewersUtil.class);
  private static final String MAX_SUFFIX = "\u9fa5";
  // Generate a candidate list at 3x the size of what the user wants to see to
  // give the ranking algorithm a good set of candidates it can work with
  private static final int CANDIDATE_LIST_MULTIPLIER = 3;
  private static final double BASE_REVIEWER_WEIGHT = 10;
  private static final double BASE_OWNER_WEIGHT = 1;
  private static final double BASE_COMMENT_WEIGHT = 0.5;

  private final AccountCache accountCache;
  private final AccountControl accountControl;
  private final AccountIndexCollection accountIndexes;
  private final AccountLoader accountLoader;
  private final AccountQueryBuilder accountQueryBuilder;
  private final AccountQueryProcessor accountQueryProcessor;
  private final Config config;
  private final ChangeQueryBuilder changeQueryBuilder;
  private final ChangeQueryProcessor changeQueryProcessor;
  private final DynamicSet<ReviewerSuggestion> reviewerSuggestionPlugins;
  private final GroupBackend groupBackend;
  private final GroupMembers.Factory groupMembersFactory;
  private final Provider<CurrentUser> currentUser;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  ReviewersUtil(AccountCache accountCache,
      AccountControl.Factory accountControlFactory,
      AccountIndexCollection accountIndexes,
      AccountLoader.Factory accountLoaderFactory,
      AccountQueryBuilder accountQueryBuilder,
      AccountQueryProcessor accountQueryProcessor,
      ChangeQueryBuilder changeQueryBuilder,
      ChangeQueryProcessor changeQueryProcessor,
      DynamicSet<ReviewerSuggestion> reviewerSuggestionPlugins,
      GroupBackend groupBackend,
      GroupMembers.Factory groupMembersFactory,
      Provider<CurrentUser> currentUser,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config config) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.accountCache = accountCache;
    this.accountControl = accountControlFactory.get();
    this.accountIndexes = accountIndexes;
    this.accountLoader = accountLoaderFactory.create(fillOptions);
    this.accountQueryBuilder = accountQueryBuilder;
    this.accountQueryProcessor = accountQueryProcessor;
    this.config = config;
    this.changeQueryBuilder = changeQueryBuilder;
    this.changeQueryProcessor = changeQueryProcessor;
    this.currentUser = currentUser;
    this.dbProvider = dbProvider;
    this.groupBackend = groupBackend;
    this.groupMembersFactory = groupMembersFactory;
    this.reviewerSuggestionPlugins = reviewerSuggestionPlugins;
  }

  public interface VisibilityControl {
    boolean isVisibleTo(Account.Id account) throws OrmException;
  }

  public List<SuggestedReviewerInfo> suggestReviewers(
      ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers, ProjectControl projectControl,
      VisibilityControl visibilityControl, boolean excludeGroups)
      throws IOException, OrmException, BadRequestException {
    String query = suggestReviewers.getQuery();
    int limit = suggestReviewers.getLimit();

    if (!suggestReviewers.getSuggestAccounts()) {
      return Collections.emptyList();
    }

    Map<Account.Id, MutableDouble> reviewerScores;
    double baseWeight = config.getInt("suggest", "baseReviewerWeight", 1);
    if (Strings.isNullOrEmpty(query)) {
      reviewerScores = baseRankingForEmptyQuery(baseWeight);
    } else {
      List<Account.Id> suggestedAccounts =
          suggestAccounts(suggestReviewers, visibilityControl);
      reviewerScores = baseRankingForCandidateList(
          suggestedAccounts, projectControl, baseWeight);
    }

    // Send the query along with a candidate list to all plugins and merge the
    // results. Plugins don't necessarily need to use the candidates list, they
    // can also return non-candidate account ids.
    for (ReviewerSuggestion r : reviewerSuggestionPlugins) {
      // TODO(hiesel) Multiply by plugin score
      // TODO(hiesel) Thread Pool
      for (SuggestedReviewer s : r.suggestReviewers(query,
          reviewerScores.keySet())) {
        // Enfore bounds for the plugin recommendations and multiply by weight
        double boundedScore = Math.min(s.score, 10);
        boundedScore = Math.max(boundedScore, 0);
        boundedScore *= r.getWeight();
        if (reviewerScores.containsKey(s.account)) {
          reviewerScores.get(s.account).add(boundedScore);
        } else {
          reviewerScores.put(s.account, new MutableDouble(boundedScore));
        }
      }
    }

    // Remove change owner
    reviewerScores.remove(changeNotes.getChange().getOwner());

    // Sort results
    Stream<Entry<Id, MutableDouble>> sorted = reviewerScores.entrySet().stream()
        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
    List<Account.Id> sortedSuggestions = sorted
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());

    // Populate AccountInfo
    List<SuggestedReviewerInfo> reviewer = new ArrayList<>();
    for (Account.Id id : sortedSuggestions) {
      SuggestedReviewerInfo info = new SuggestedReviewerInfo();
      info.account = accountLoader.get(id);
      info.count = 1;
      reviewer.add(info);
    }
    accountLoader.fill();

    if (!excludeGroups) {
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
          // Always add groups at the front as they are usually more important
          reviewer.add(0, suggestedReviewerInfo);
        }
      }
    }

    if (reviewer.size() <= limit) {
      return reviewer;
    }
    return reviewer.subList(0, limit);
  }

  private List<Account.Id> suggestAccounts(SuggestReviewers suggestReviewers,
      VisibilityControl visibilityControl)
      throws OrmException {
    AccountIndex searchIndex = accountIndexes.getSearchIndex();
    if (searchIndex != null) {
      return suggestAccountsFromIndex(suggestReviewers);
    }
    return suggestAccountsFromDb(suggestReviewers, visibilityControl);
  }

  private List<Account.Id> suggestAccountsFromIndex(
      SuggestReviewers suggestReviewers) throws OrmException {
    try {
      Set<Account.Id> matches = new HashSet<>();
      QueryResult<AccountState> result = accountQueryProcessor
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
      SuggestReviewers suggestReviewers, VisibilityControl visibilityControl)
          throws OrmException {
    String query = suggestReviewers.getQuery();
    int limit = suggestReviewers.getLimit() * CANDIDATE_LIST_MULTIPLIER;

    String a = query;
    String b = a + MAX_SUFFIX;

    Set<Account.Id> r = new HashSet<>();

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

  private Map<Account.Id, MutableDouble> baseRankingForEmptyQuery(
      double baseWeight) throws OrmException{
    // Get the user's last 50 changes, check reviewers
    try {
      QueryResult<ChangeData> result = changeQueryProcessor
          .setLimit(50)
          .setRequestedFields(ImmutableSet.of(ChangeField.REVIEWER.getName()))
          .query(changeQueryBuilder.owner("self"));
      Map<Account.Id, MutableDouble> suggestions = new HashMap<>();
      for (ChangeData cd : result.entities()) {
        for (Account.Id id : cd.getReviewers().all()) {
          if (suggestions.containsKey(id)) {
            suggestions.get(id).add(baseWeight);
          } else {
            suggestions.put(id, new MutableDouble(baseWeight));
          }
        }
      }
      return suggestions;
    } catch (QueryParseException e) {
      // Unhandled, because owner:self will never provoke a QueryParseException
      log.warn("Exception while suggesting reviewers", e);
      return ImmutableMap.of();
    }
  }

  private Map<Account.Id, MutableDouble> baseRankingForCandidateList(
      List<Account.Id> candidates,
      ProjectControl projectControl,
      double baseWeight) throws OrmException {
    // Get each reviewer's activity based on number of reviews (weighted 1.5d),
    // number of comments (weighted 0.5d) and number of owned changes
    // (weighted 1d).
    Map<Account.Id, MutableDouble> reviewers = new HashMap<>();
    for (Account.Id id : candidates) {
      try {
        Predicate<ChangeData> projectQuery =
            changeQueryBuilder.project(projectControl.getProject().getName());
        addReviewerWeightFromIndexQuery(
            id,
            Predicate.and(projectQuery,
                changeQueryBuilder.reviewer(id.toString())),
            reviewers,
            BASE_REVIEWER_WEIGHT * baseWeight);
        addReviewerWeightFromIndexQuery(
            id,
            Predicate.and(projectQuery,
                changeQueryBuilder.owner(id.toString())),
            reviewers,
            BASE_OWNER_WEIGHT * baseWeight);
        addReviewerWeightFromIndexQuery(
            id,
            Predicate.and(projectQuery,
                changeQueryBuilder.commentby(id.toString())),
            reviewers,
            BASE_COMMENT_WEIGHT * baseWeight);
      } catch (QueryParseException e) {
        reviewers.put(id, new MutableDouble(1d));
      }
    }
    return reviewers;
  }

  private void addReviewerWeightFromIndexQuery(
      Account.Id id,
      Predicate<ChangeData> query,
      Map<Account.Id, MutableDouble> reviewers,
      double weight) throws OrmException {
    try {
      QueryResult<ChangeData> result = changeQueryProcessor
          .setLimit(100)
          .setRequestedFields(ImmutableSet.of())
          .query(query);
      if (reviewers.containsKey(id)) {
        reviewers.get(id).add((double) result.entities().size() * weight);
      } else {
        reviewers.put(id, new MutableDouble(
            (double) result.entities().size() * weight));
      }
    } catch (QueryParseException e) {
      reviewers.put(id, new MutableDouble());
    }
  }

  private boolean addSuggestion(Set<Account.Id> map,
      Account.Id account, VisibilityControl visibilityControl)
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
        Iterables.limit(groupBackend.suggest(suggestReviewers.getQuery(), ctl),
            suggestReviewers.getLimit()));
  }

  private static class GroupAsReviewer {
    boolean allowed;
    boolean allowedWithConfirmation;
    int size;
  }

  private GroupAsReviewer suggestGroupAsReviewer(
      SuggestReviewers suggestReviewers,
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
