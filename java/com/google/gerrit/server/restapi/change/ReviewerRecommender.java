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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.change.SuggestedReviewer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private static final long PLUGIN_QUERY_TIMEOUT = 500; // ms

  private final ChangeQueryBuilder changeQueryBuilder;
  private final Config config;
  private final PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ExecutorService executor;
  private final ApprovalsUtil approvalsUtil;
  private final AccountCache accountCache;

  @Inject
  ReviewerRecommender(
      ChangeQueryBuilder changeQueryBuilder,
      PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap,
      Provider<InternalChangeQuery> queryProvider,
      @FanOutExecutor ExecutorService executor,
      ApprovalsUtil approvalsUtil,
      @GerritServerConfig Config config,
      AccountCache accountCache) {
    this.changeQueryBuilder = changeQueryBuilder;
    this.config = config;
    this.queryProvider = queryProvider;
    this.reviewerSuggestionPluginMap = reviewerSuggestionPluginMap;
    this.executor = executor;
    this.approvalsUtil = approvalsUtil;
    this.accountCache = accountCache;
  }

  public List<Account.Id> suggestReviewers(
      ReviewerState reviewerState,
      @Nullable ChangeNotes changeNotes,
      SuggestReviewers suggestReviewers,
      ProjectState projectState,
      List<Account.Id> candidateList)
      throws IOException, ConfigInvalidException {
    logger.atFine().log("Candidates %s", candidateList);

    String query = suggestReviewers.getQuery();
    logger.atFine().log("query: %s", query);

    double baseWeight = config.getInt("addReviewer", "baseWeight", 1);
    logger.atFine().log("base weight: %s", baseWeight);

    Map<Account.Id, MutableDouble> reviewerScores = baseRanking(baseWeight, query, candidateList);
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
          .byState(ReviewerStateInternal.fromReviewerState(reviewerState))
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

  /**
   * @param baseWeight The weight applied to the ordering of the reviewers.
   * @param query Query to match. For example, it can try to match all users that start with "Ab".
   * @param candidateList The list of candidates based on the query. If query is empty, this list is
   *     also empty.
   * @return Map of account ids that match the query and their appropriate ranking (the better the
   *     ranking, the better it is to suggest them as reviewers).
   * @throws IOException Can't find owner="self" account.
   * @throws ConfigInvalidException Can't find owner="self" account.
   */
  private Map<Account.Id, MutableDouble> baseRanking(
      double baseWeight, String query, List<Account.Id> candidateList)
      throws IOException, ConfigInvalidException {
    // Get the user's last 25 changes, check approvals
    try {
      List<ChangeData> result =
          queryProvider
              .get()
              .setLimit(25)
              .setRequestedFields(ChangeField.APPROVAL)
              .query(changeQueryBuilder.owner("self"));
      Map<Account.Id, MutableDouble> suggestions = new LinkedHashMap<>();
      // Put those candidates at the bottom of the list
      candidateList.stream().forEach(id -> suggestions.put(id, new MutableDouble(0)));

      for (ChangeData cd : result) {
        for (PatchSetApproval approval : cd.currentApprovals()) {
          Account.Id id = approval.accountId();
          if (Strings.isNullOrEmpty(query) || accountMatchesQuery(id, query)) {
            suggestions.computeIfAbsent(id, (ignored) -> new MutableDouble(0)).add(baseWeight);
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

  private boolean accountMatchesQuery(Account.Id id, String query) {
    Optional<Account> account = accountCache.get(id).map(AccountState::account);
    if (account.isPresent() && account.get().isActive()) {
      if ((account.get().fullName() != null && account.get().fullName().startsWith(query))
          || (account.get().preferredEmail() != null
              && account.get().preferredEmail().startsWith(query))) {
        return true;
      }
    }
    return false;
  }
}
