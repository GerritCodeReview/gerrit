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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupBaseInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.SuggestReviewers;
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
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ReviewersUtil {
  @Singleton
  private static class Metrics {
    final Timer0 queryAccountsLatency;
    final Timer0 recommendAccountsLatency;
    final Timer0 loadAccountsLatency;
    final Timer0 queryGroupsLatency;
    final Timer0 filterVisibility;

    @Inject
    Metrics(MetricMaker metricMaker) {
      queryAccountsLatency =
          metricMaker.newTimer(
              "reviewer_suggestion/query_accounts",
              new Description("Latency for querying accounts for reviewer suggestion")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      recommendAccountsLatency =
          metricMaker.newTimer(
              "reviewer_suggestion/recommend_accounts",
              new Description("Latency for recommending accounts for reviewer suggestion")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      loadAccountsLatency =
          metricMaker.newTimer(
              "reviewer_suggestion/load_accounts",
              new Description("Latency for loading accounts for reviewer suggestion")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      queryGroupsLatency =
          metricMaker.newTimer(
              "reviewer_suggestion/query_groups",
              new Description("Latency for querying groups for reviewer suggestion")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
      filterVisibility =
          metricMaker.newTimer(
              "reviewer_suggestion/filter_visibility",
              new Description("Latency for removing users that can't see the change")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
    }
  }

  // Generate a candidate list at 2x the size of what the user wants to see to
  // give the ranking algorithm a good set of candidates it can work with
  private static final int CANDIDATE_LIST_MULTIPLIER = 2;

  private final AccountLoader accountLoader;
  private final AccountQueryBuilder accountQueryBuilder;
  private final AccountQueryProcessor accountQueryProcessor;
  private final GroupBackend groupBackend;
  private final GroupMembers.Factory groupMembersFactory;
  private final Provider<CurrentUser> currentUser;
  private final ReviewerRecommender reviewerRecommender;
  private final Metrics metrics;

  @Inject
  ReviewersUtil(
      AccountLoader.Factory accountLoaderFactory,
      AccountQueryBuilder accountQueryBuilder,
      AccountQueryProcessor accountQueryProcessor,
      GroupBackend groupBackend,
      GroupMembers.Factory groupMembersFactory,
      Provider<CurrentUser> currentUser,
      ReviewerRecommender reviewerRecommender,
      Metrics metrics) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.accountLoader = accountLoaderFactory.create(fillOptions);
    this.accountQueryBuilder = accountQueryBuilder;
    this.accountQueryProcessor = accountQueryProcessor;
    this.currentUser = currentUser;
    this.groupBackend = groupBackend;
    this.groupMembersFactory = groupMembersFactory;
    this.reviewerRecommender = reviewerRecommender;
    this.metrics = metrics;
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
      candidateList = suggestAccounts(suggestReviewers);
    }

    List<Account.Id> sortedRecommendations =
        recommendAccounts(changeNotes, suggestReviewers, projectControl, candidateList);

    // Filter accounts by visibility and enforce limit
    List<Account.Id> filteredRecommendations = new ArrayList<>();
    try (Timer0.Context ctx = metrics.filterVisibility.start()) {
      for (Account.Id reviewer : sortedRecommendations) {
        if (filteredRecommendations.size() >= limit) {
          break;
        }
        if (visibilityControl.isVisibleTo(reviewer)) {
          filteredRecommendations.add(reviewer);
        }
      }
    }

    List<SuggestedReviewerInfo> suggestedReviewer = loadAccounts(filteredRecommendations);
    if (!excludeGroups && suggestedReviewer.size() < limit && !Strings.isNullOrEmpty(query)) {
      // Add groups at the end as individual accounts are usually more
      // important.
      suggestedReviewer.addAll(
          suggestAccountGroups(
              suggestReviewers,
              projectControl,
              visibilityControl,
              limit - suggestedReviewer.size()));
    }

    if (suggestedReviewer.size() <= limit) {
      return suggestedReviewer;
    }
    return suggestedReviewer.subList(0, limit);
  }

  private List<Account.Id> suggestAccounts(SuggestReviewers suggestReviewers) throws OrmException {
    try (Timer0.Context ctx = metrics.queryAccountsLatency.start()) {
      try {
        QueryResult<AccountState> result =
            accountQueryProcessor
                .setLimit(suggestReviewers.getLimit() * CANDIDATE_LIST_MULTIPLIER)
                .query(accountQueryBuilder.defaultQuery(suggestReviewers.getQuery()));
        return result.entities().stream().map(a -> a.getAccount().getId()).collect(toList());
      } catch (QueryParseException e) {
        return ImmutableList.of();
      }
    }
  }

  private List<Account.Id> recommendAccounts(
      ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers,
      ProjectControl projectControl,
      List<Account.Id> candidateList)
      throws OrmException, IOException {
    try (Timer0.Context ctx = metrics.recommendAccountsLatency.start()) {
      return reviewerRecommender.suggestReviewers(
          changeNotes, suggestReviewers, projectControl, candidateList);
    }
  }

  private List<SuggestedReviewerInfo> loadAccounts(List<Account.Id> accountIds)
      throws OrmException {
    try (Timer0.Context ctx = metrics.loadAccountsLatency.start()) {
      List<SuggestedReviewerInfo> reviewer =
          accountIds
              .stream()
              .map(accountLoader::get)
              .filter(Objects::nonNull)
              .map(
                  a -> {
                    SuggestedReviewerInfo info = new SuggestedReviewerInfo();
                    info.account = a;
                    info.count = 1;
                    return info;
                  })
              .collect(toList());
      accountLoader.fill();
      return reviewer;
    }
  }

  private List<SuggestedReviewerInfo> suggestAccountGroups(
      SuggestReviewers suggestReviewers,
      ProjectControl projectControl,
      VisibilityControl visibilityControl,
      int limit)
      throws OrmException, IOException {
    try (Timer0.Context ctx = metrics.queryGroupsLatency.start()) {
      List<SuggestedReviewerInfo> groups = new ArrayList<>();
      for (GroupReference g : suggestAccountGroups(suggestReviewers, projectControl)) {
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
          groups.add(suggestedReviewerInfo);
          if (groups.size() >= limit) {
            break;
          }
        }
      }
      return groups;
    }
  }

  private List<GroupReference> suggestAccountGroups(
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
