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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.change.SuggestedReviewer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.InternalChangeQuery;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.commons.lang.mutable.MutableDouble;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class ReviewerRecommender {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final double BASE_REVIEWER_WEIGHT = 10;
  private static final double BASE_OWNER_WEIGHT = 1;
  private static final double BASE_COMMENT_WEIGHT = 0.5;
  private static final double[] WEIGHTS =
      new double[] {
        BASE_REVIEWER_WEIGHT, BASE_OWNER_WEIGHT, BASE_COMMENT_WEIGHT,
      };
  private static final long PLUGIN_QUERY_TIMEOUT = 500; // ms

  private final ChangeQueryBuilder changeQueryBuilder;
  private final Config config;
  private final PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ExecutorService executor;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  ReviewerRecommender(
      ChangeQueryBuilder changeQueryBuilder,
      PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap,
      Provider<InternalChangeQuery> queryProvider,
      @FanOutExecutor ExecutorService executor,
      ApprovalsUtil approvalsUtil,
      @GerritServerConfig Config config) {
    this.changeQueryBuilder = changeQueryBuilder;
    this.config = config;
    this.queryProvider = queryProvider;
    this.reviewerSuggestionPluginMap = reviewerSuggestionPluginMap;
    this.executor = executor;
    this.approvalsUtil = approvalsUtil;
  }

  public List<Account.Id> suggestReviewers(
      @Nullable ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers,
      ProjectState projectState,
      List<Account.Id> candidateList)
      throws StorageException, IOException, ConfigInvalidException {
    logger.atFine().log("Candidates %s", candidateList);

    String query = suggestReviewers.getQuery();
    logger.atFine().log("query: %s", query);

    double baseWeight = config.getInt("addReviewer", "baseWeight", 1);
    logger.atFine().log("base weight: %s", baseWeight);

    Map<Account.Id, MutableDouble> reviewerScores;
    if (Strings.isNullOrEmpty(query)) {
      reviewerScores = baseRankingForEmptyQuery(baseWeight);
    } else {
      reviewerScores = baseRankingForCandidateList(candidateList, projectState, baseWeight);
    }
    logger.atFine().log("Base reviewer scores: %s", reviewerScores);

    // Send the query along with a candidate list to all plugins and merge the
    // results. Plugins don't necessarily need to use the candidates list, they
    // can also return non-candidate account ids.
    List<Callable<Set<SuggestedReviewer>>> tasks =
        new ArrayList<>(reviewerSuggestionPluginMap.plugins().size());
    List<Double> weights = new ArrayList<>(reviewerSuggestionPluginMap.plugins().size());

    reviewerSuggestionPluginMap.runEach(
        extension -> {
          tasks.add(
              () ->
                  extension
                      .get()
                      .suggestReviewers(
                          projectState.getNameKey(),
                          changeNotes != null ? changeNotes.getChangeId() : null,
                          query,
                          reviewerScores.keySet()));
          String key = extension.getPluginName() + "-" + extension.getExportName();
          String pluginWeight = config.getString("addReviewer", key, "weight");
          if (Strings.isNullOrEmpty(pluginWeight)) {
            pluginWeight = "1";
          }
          logger.atFine().log("weight for %s: %s", key, pluginWeight);
          try {
            weights.add(Double.parseDouble(pluginWeight));
          } catch (NumberFormatException e) {
            logger.atSevere().withCause(e).log("Exception while parsing weight for %s", key);
            weights.add(1d);
          }
        });

    try {
      List<Future<Set<SuggestedReviewer>>> futures =
          executor.invokeAll(tasks, PLUGIN_QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
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
      logger.atFine().log("Reviewer scores: %s", reviewerScores);
    } catch (ExecutionException | InterruptedException e) {
      logger.atSevere().withCause(e).log("Exception while suggesting reviewers");
      return ImmutableList.of();
    }

    if (changeNotes != null) {
      // Remove change owner
      if (reviewerScores.remove(changeNotes.getChange().getOwner()) != null) {
        logger.atFine().log("Remove change owner %s", changeNotes.getChange().getOwner());
      }

      // Remove existing reviewers
      approvalsUtil
          .getReviewers(changeNotes)
          .byState(REVIEWER)
          .forEach(
              r -> {
                if (reviewerScores.remove(r) != null) {
                  logger.atFine().log("Remove existing reviewer %s", r);
                }
              });
    }

    // Sort results
    Stream<Map.Entry<Account.Id, MutableDouble>> sorted =
        reviewerScores.entrySet().stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
    List<Account.Id> sortedSuggestions = sorted.map(Map.Entry::getKey).collect(toList());
    logger.atFine().log("Sorted suggestions: %s", sortedSuggestions);
    return sortedSuggestions;
  }

  private Map<Account.Id, MutableDouble> baseRankingForEmptyQuery(double baseWeight)
      throws StorageException, IOException, ConfigInvalidException {
    // Get the user's last 25 changes, check approvals
    try {
      List<ChangeData> result =
          queryProvider
              .get()
              .setLimit(25)
              .setRequestedFields(ChangeField.APPROVAL)
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
      logger.atSevere().withCause(e).log("Exception while suggesting reviewers");
      return ImmutableMap.of();
    }
  }

  private Map<Account.Id, MutableDouble> baseRankingForCandidateList(
      List<Account.Id> candidates, ProjectState projectState, double baseWeight)
      throws StorageException, IOException, ConfigInvalidException {
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
        Predicate<ChangeData> projectQuery = changeQueryBuilder.project(projectState.getName());

        // Get all labels for this project and create a compound OR query to
        // fetch all changes where users have applied one of these labels
        List<LabelType> labelTypes = projectState.getLabelTypes().getLabelTypes();
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
        logger.atSevere().withCause(e).log("Exception while suggesting reviewers");
      }
    }

    List<List<ChangeData>> result = queryProvider.get().setLimit(25).noFields().query(predicates);

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
