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
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
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

import org.apache.commons.lang.mutable.MutableDouble;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReviewerRecommender {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewersUtil.class);
  private static final double BASE_REVIEWER_WEIGHT = 10;
  private static final double BASE_OWNER_WEIGHT = 1;
  private static final double BASE_COMMENT_WEIGHT = 0.5;
  private static final long PLUGIN_QUERY_TIMEOUT = 500; //ms

  private final ChangeQueryBuilder changeQueryBuilder;
  private final DynamicSet<ReviewerSuggestion> reviewerSuggestionPluginSet;
  private final InternalChangeQuery internalChangeQuery;
  private final WorkQueue workQueue;
  private final double baseWeight;

  @Inject
  ReviewerRecommender(ChangeQueryBuilder changeQueryBuilder,
      DynamicSet<ReviewerSuggestion> reviewerSuggestionPluginSet,
      InternalChangeQuery internalChangeQuery,
      WorkQueue workQueue,
      @GerritServerConfig Config config) {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.SECONDARY_EMAILS);
    fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    this.changeQueryBuilder = changeQueryBuilder;
    this.internalChangeQuery = internalChangeQuery;
    this.reviewerSuggestionPluginSet = reviewerSuggestionPluginSet;
    this.workQueue = workQueue;

    this.baseWeight = config.getInt("suggest", "baseReviewerWeight", 1);
  }

  public List<Account.Id> suggestReviewers(
      ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers, ProjectControl projectControl,
      List<Account.Id> candidateList)
      throws IOException, OrmException, BadRequestException {
    String query = suggestReviewers.getQuery();

    Map<Account.Id, MutableDouble> reviewerScores;
    if (Strings.isNullOrEmpty(query)) {
      reviewerScores = baseRankingForEmptyQuery(baseWeight);
    } else {
      reviewerScores = baseRankingForCandidateList(
          candidateList, projectControl, baseWeight);
    }

    // Send the query along with a candidate list to all plugins and merge the
    // results. Plugins don't necessarily need to use the candidates list, they
    // can also return non-candidate account ids.
    List<ReviewerSuggestion> reviewerSuggestionPlugins =
        Lists.newArrayList(reviewerSuggestionPluginSet);
    List<Callable<List<SuggestedReviewer>>> tasks =
        new ArrayList<>(reviewerSuggestionPlugins.size());
    List<Double> weights =
        new ArrayList<>(reviewerSuggestionPlugins.size());
    for (ReviewerSuggestion r : reviewerSuggestionPlugins) {
      tasks.add(() -> r.suggestReviewers(
          changeNotes.getChangeId(), query, reviewerScores.keySet()));
      weights.add(r.getWeight());
    }
    try {
      List<Future<List<SuggestedReviewer>>> futures = workQueue
          .getDefaultQueue()
          .invokeAll(tasks, PLUGIN_QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
      Iterator<Double> weightIterator = weights.iterator();
      for (Future<List<SuggestedReviewer>> f : futures) {
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
      log.warn("Exception while suggesting reviewers", e);
      return ImmutableList.of();
    }

    // Remove change owner
    reviewerScores.remove(changeNotes.getChange().getOwner());

    // Sort results
    Stream<Entry<Account.Id, MutableDouble>> sorted =
        reviewerScores.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
    List<Account.Id> sortedSuggestions = sorted
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    return sortedSuggestions;
  }

  private Map<Account.Id, MutableDouble> baseRankingForEmptyQuery(
      double baseWeight) throws OrmException{
    // Get the user's last 50 changes, check reviewers
    try {
      List<ChangeData> result = internalChangeQuery
          .setLimit(50)
          .setRequestedFields(ImmutableSet.of(ChangeField.REVIEWER.getName()))
          .query(changeQueryBuilder.owner("self"));
      Map<Account.Id, MutableDouble> suggestions = new HashMap<>();
      for (ChangeData cd : result) {
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
    // Get each reviewer's activity based on number of reviews (weighted 10d),
    // number of comments (weighted 0.5d) and number of owned changes
    // (weighted 1d).
    Map<Account.Id, MutableDouble> reviewers = new LinkedHashMap<>();
    if (candidates.size() == 0) {
      return reviewers;
    }
    List<Predicate<ChangeData>> predicates = new ArrayList<>();
    for (Account.Id id : candidates) {
      try {
        Predicate<ChangeData> projectQuery =
            changeQueryBuilder.project(projectControl.getProject().getName());
        Predicate<ChangeData> reviewerQuery = Predicate.and(projectQuery,
            changeQueryBuilder.reviewer(id.toString()));
        Predicate<ChangeData> ownerQuery = Predicate.and(projectQuery,
            changeQueryBuilder.owner(id.toString()));
        Predicate<ChangeData> commentedByQuery = Predicate.and(projectQuery,
            changeQueryBuilder.commentby(id.toString()));

        predicates.add(reviewerQuery);
        predicates.add(ownerQuery);
        predicates.add(commentedByQuery);
        reviewers.put(id, new MutableDouble());
      } catch (QueryParseException e) {
        // Unhandled: If an exception is thrown, we won't increase the
        // candidates's score
        log.warn("Exception while suggesting reviewers", e);
      }
    }

    List<List<ChangeData>> result = internalChangeQuery
        .setLimit(100 * predicates.size())
        .setRequestedFields(ImmutableSet.of())
        .query(predicates);

    Iterator<List<ChangeData>> queryResultIterator = result.iterator();
    Iterator<Account.Id> reviewersIterator = reviewers.keySet().iterator();

    double[] weights = new double[]{
        BASE_REVIEWER_WEIGHT, BASE_OWNER_WEIGHT, BASE_COMMENT_WEIGHT};

    int i = 0;
    Account.Id currentId = null;
    while (queryResultIterator.hasNext()) {
      List<ChangeData> currentResult = queryResultIterator.next();
      if (i % weights.length == 0) {
        currentId = reviewersIterator.next();
      }

      reviewers.get(currentId).add(weights[i % weights.length] *
          baseWeight * currentResult.size());
      i++;
    }
    return reviewers;
  }
}
