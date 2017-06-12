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

import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.change.SuggestReviewers;
import com.google.gerrit.server.change.SuggestedReviewer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.commons.lang.mutable.MutableDouble;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewerRecommender {
  private static final Logger log = LoggerFactory.getLogger(ReviewerRecommender.class);
  private static final double BASE_REVIEWER_WEIGHT = 10;
  private static final double BASE_OWNER_WEIGHT = 1;
  private static final double BASE_COMMENT_WEIGHT = 0.5;
  private static final double[] WEIGHTS =
      new double[] {
        BASE_REVIEWER_WEIGHT, BASE_OWNER_WEIGHT, BASE_COMMENT_WEIGHT,
      };
  private static final long PLUGIN_QUERY_TIMEOUT = 500; //ms

  private final ChangeQueryBuilder changeQueryBuilder;
  private final Config config;
  private final DynamicMap<ReviewerSuggestion> reviewerSuggestionPluginMap;
  private final InternalChangeQuery internalChangeQuery;
  private final WorkQueue workQueue;
  private final Provider<ReviewDb> dbProvider;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  ReviewerRecommender(
      ChangeQueryBuilder changeQueryBuilder,
      DynamicMap<ReviewerSuggestion> reviewerSuggestionPluginMap,
      InternalChangeQuery internalChangeQuery,
      WorkQueue workQueue,
      Provider<ReviewDb> dbProvider,
      ApprovalsUtil approvalsUtil,
      @GerritServerConfig Config config) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.changeQueryBuilder = changeQueryBuilder;
    this.config = config;
    this.internalChangeQuery = internalChangeQuery;
    this.reviewerSuggestionPluginMap = reviewerSuggestionPluginMap;
    this.workQueue = workQueue;
    this.dbProvider = dbProvider;
    this.approvalsUtil = approvalsUtil;
  }

  public List<Account.Id> suggestReviewers(
      ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers,
      ProjectControl projectControl,
      List<Account.Id> candidateList)
      throws OrmException, IOException {
    String query = suggestReviewers.getQuery();
    double baseWeight = config.getInt("addReviewer", "baseWeight", 1);

    Map<Account.Id, MutableDouble> reviewerScores;
    if (Strings.isNullOrEmpty(query)) {
      reviewerScores = baseRankingForEmptyQuery(baseWeight);
    } else {
      reviewerScores = baseRankingForCandidateList(candidateList, projectControl, baseWeight);
    }

    // Send the query along with a candidate list to all plugins and merge the
    // results. Plugins don't necessarily need to use the candidates list, they
    // can also return non-candidate account ids.
    List<Callable<Set<SuggestedReviewer>>> tasks =
        new ArrayList<>(reviewerSuggestionPluginMap.plugins().size());
    List<Double> weights = new ArrayList<>(reviewerSuggestionPluginMap.plugins().size());

    for (DynamicMap.Entry<ReviewerSuggestion> plugin : reviewerSuggestionPluginMap) {
      tasks.add(
          () ->
              plugin
                  .getProvider()
                  .get()
                  .suggestReviewers(
                      projectControl.getProject().getNameKey(),
                      changeNotes.getChangeId(),
                      query,
                      reviewerScores.keySet()));
      String pluginWeight =
          config.getString(
              "addReviewer", plugin.getPluginName() + "-" + plugin.getExportName(), "weight");
      if (Strings.isNullOrEmpty(pluginWeight)) {
        pluginWeight = "1";
      }
      try {
        weights.add(Double.parseDouble(pluginWeight));
      } catch (NumberFormatException e) {
        log.error(
            "Exception while parsing weight for "
                + plugin.getPluginName()
                + "-"
                + plugin.getExportName(),
            e);
        weights.add(1d);
      }
    }

    try {
      List<Future<Set<SuggestedReviewer>>> futures =
          workQueue.getDefaultQueue().invokeAll(tasks, PLUGIN_QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
      Iterator<Double> weightIterator = weights.iterator();
      for (Future<Set<SuggestedReviewer>> f : futures) {
        double weight = weightIterator.next();
        for (SuggestedReviewer s : f.get()) {
          if (reviewerScores.containsKey(s.account)) {
            reviewerScores.get(s.account).add(s.score * weight);
          } else {
            reviewerScores.put(s.account, new MutableDouble(s.score * weight));
          }
        }
      }
    } catch (ExecutionException | InterruptedException e) {
      log.error("Exception while suggesting reviewers", e);
      return ImmutableList.of();
    }

    if (changeNotes != null) {
      // Remove change owner
      reviewerScores.remove(changeNotes.getChange().getOwner());

      // Remove existing reviewers
      reviewerScores
          .keySet()
          .removeAll(approvalsUtil.getReviewers(dbProvider.get(), changeNotes).byState(REVIEWER));
    }

    // Sort results
    Stream<Entry<Account.Id, MutableDouble>> sorted =
        reviewerScores
            .entrySet()
            .stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
    List<Account.Id> sortedSuggestions = sorted.map(Map.Entry::getKey).collect(toList());
    return sortedSuggestions;
  }

  private Map<Account.Id, MutableDouble> baseRankingForEmptyQuery(double baseWeight)
      throws OrmException, IOException {
    // Get the user's last 25 changes, check approvals
    try {
      List<ChangeData> result =
          internalChangeQuery
              .setLimit(25)
              .setRequestedFields(ImmutableSet.of(ChangeField.REVIEWER.getName()))
              .query(changeQueryBuilder.owner("self"));
      Map<Account.Id, MutableDouble> suggestions = new HashMap<>();
      for (ChangeData cd : result) {
        for (PatchSetApproval approval : cd.currentApprovals()) {
          Account.Id id = approval.getAccountId();
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
      log.error("Exception while suggesting reviewers", e);
      return ImmutableMap.of();
    }
  }

  private Map<Account.Id, MutableDouble> baseRankingForCandidateList(
      List<Account.Id> candidates, ProjectControl projectControl, double baseWeight)
      throws OrmException, IOException {
    // Get each reviewer's activity based on number of applied labels
    // (weighted 10d), number of comments (weighted 0.5d) and number of owned
    // changes (weighted 1d).
    Map<Account.Id, MutableDouble> reviewers = new LinkedHashMap<>();
    if (candidates.size() == 0) {
      return reviewers;
    }
    List<Predicate<ChangeData>> predicates = new ArrayList<>();
    for (Account.Id id : candidates) {
      try {
        Predicate<ChangeData> projectQuery =
            changeQueryBuilder.project(projectControl.getProject().getName());

        // Get all labels for this project and create a compound OR query to
        // fetch all changes where users have applied one of these labels
        List<LabelType> labelTypes = projectControl.getLabelTypes().getLabelTypes();
        List<Predicate<ChangeData>> labelPredicates = new ArrayList<>(labelTypes.size());
        for (LabelType type : labelTypes) {
          labelPredicates.add(changeQueryBuilder.label(type.getName() + ",user=" + id));
        }
        Predicate<ChangeData> reviewerQuery =
            Predicate.and(projectQuery, Predicate.or(labelPredicates));

        Predicate<ChangeData> ownerQuery =
            Predicate.and(projectQuery, changeQueryBuilder.owner(id.toString()));
        Predicate<ChangeData> commentedByQuery =
            Predicate.and(projectQuery, changeQueryBuilder.commentby(id.toString()));

        predicates.add(reviewerQuery);
        predicates.add(ownerQuery);
        predicates.add(commentedByQuery);
        reviewers.put(id, new MutableDouble());
      } catch (QueryParseException e) {
        // Unhandled: If an exception is thrown, we won't increase the
        // candidates's score
        log.error("Exception while suggesting reviewers", e);
      }
    }

    List<List<ChangeData>> result =
        internalChangeQuery.setLimit(25).setRequestedFields(ImmutableSet.of()).query(predicates);

    Iterator<List<ChangeData>> queryResultIterator = result.iterator();
    Iterator<Account.Id> reviewersIterator = reviewers.keySet().iterator();

    int i = 0;
    Account.Id currentId = null;
    while (queryResultIterator.hasNext()) {
      List<ChangeData> currentResult = queryResultIterator.next();
      if (i % WEIGHTS.length == 0) {
        currentId = reviewersIterator.next();
      }

      reviewers.get(currentId).add(WEIGHTS[i % WEIGHTS.length] * baseWeight * currentResult.size());
      i++;
    }
    return reviewers;
  }
}
