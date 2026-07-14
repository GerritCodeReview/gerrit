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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.change.SuggestedReviewer;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.AgePredicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.eclipse.jgit.lib.Config;

public class ReviewerRecommender {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long PLUGIN_QUERY_TIMEOUT = 500; // ms

  private static final long ATTENTION_SET_QUERY_TIMEOUT = 500; // ms

  /** Maximum number of open changes with an attention set entry that are counted per account. */
  private static final int MAX_ATTENTION_SET_LOAD = 25;

  private final Config config;
  private final PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<IdentifiedUser> identifiedUser;
  private final ExecutorService executor;
  private final ApprovalsUtil approvalsUtil;
  private final AccountCache accountCache;
  private final GroupMembers groupMembers;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  ReviewerRecommender(
      PluginMapContext<ReviewerSuggestion> reviewerSuggestionPluginMap,
      Provider<InternalChangeQuery> queryProvider,
      Provider<IdentifiedUser> identifiedUser,
      @FanOutExecutor ExecutorService executor,
      ApprovalsUtil approvalsUtil,
      @GerritServerConfig Config config,
      AccountCache accountCache,
      GroupMembers groupMembers,
      ChangeData.Factory changeDataFactory) {
    this.config = config;
    this.queryProvider = queryProvider;
    this.identifiedUser = identifiedUser;
    this.reviewerSuggestionPluginMap = reviewerSuggestionPluginMap;
    this.executor = executor;
    this.approvalsUtil = approvalsUtil;
    this.accountCache = accountCache;
    this.groupMembers = groupMembers;
    this.changeDataFactory = changeDataFactory;
  }

  public List<Account.Id> suggestReviewers(
      ReviewerState reviewerState,
      @Nullable ChangeNotes changeNotes,
      String query,
      ProjectState projectState,
      ImmutableList<Account.Id> candidateList)
      throws IOException, NoSuchProjectException {
    logger.atFine().log("query: %s, candidates: %s", query, candidateList);

    Map<Account.Id, MutableDouble> candidateScores = new LinkedHashMap<>();
    candidateList.stream().forEach(id -> candidateScores.put(id, new MutableDouble(0)));

    // Get the user's recent changes and add them as candidates
    double recentChangeCandidatesWeight = config.getInt("addReviewer", "baseWeight", 1);
    logger.atFine().log("recentChangeCandidatesWeight: %s", recentChangeCandidatesWeight);
    ImmutableList<ChangeData> changes =
        queryRecentChanges(ChangePredicates.owner(identifiedUser.get().getAccountId()));
    getMatchingReviewers(changes, query)
        .forEach(
            reviewerCandidate ->
                candidateScores
                    .computeIfAbsent(reviewerCandidate, (ignored) -> new MutableDouble(0))
                    .add(recentChangeCandidatesWeight));

    // Boost accounts that recently owned or reviewed changes that touched the same files as the
    // change for which reviewers are suggested. Such users often are the de-facto owners of these
    // files, even if they are not listed in any code owner configuration.
    double touchedFilesWeight = config.getInt("addReviewer", "touchedFilesWeight", 2);
    if (changeNotes != null && touchedFilesWeight > 0) {
      logger.atFine().log("touchedFilesWeight: %s", touchedFilesWeight);
      addTouchedFilesCandidates(
          changeNotes, projectState, query, touchedFilesWeight, candidateScores);
    }

    if (Strings.isNullOrEmpty(query) && candidateScores.isEmpty()) {
      // There are no candidates for the default reviewer suggestion (= suggestion for an empty
      // query). Fallback to suggesting the reviewers of recent changes in the same project.
      changes = queryRecentChanges(ChangePredicates.project(projectState.getNameKey()));

      // Since we are suggesting default reviewers here (query is empty) we do not need to call
      // getMatchingReviewers here, but we can include the reviewers directly.
      getReviewers(changes)
          .forEach(reviewerId -> candidateScores.put(reviewerId, new MutableDouble(0)));

      if (candidateScores.isEmpty()) {
        // There are still no candidates for the default reviewer suggestion. Fallback to suggesting
        // the project owners.
        groupMembers
            .listAccounts(SystemGroupBackend.PROJECT_OWNERS, projectState.getNameKey())
            .stream()
            .map(Account::id)
            .forEach(projectOwnerId -> candidateScores.put(projectOwnerId, new MutableDouble(0)));
      }
    }

    logger.atFine().log("Base candidate scores: %s", candidateScores);

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
                          candidateScores.keySet()));
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
          if (candidateScores.containsKey(s.account)) {
            candidateScores.get(s.account).add(s.score * weight);
          } else {
            candidateScores.put(s.account, new MutableDouble(s.score * weight));
          }
        }
      }
      logger.atFine().log("Candidate scores: %s", candidateScores);
    } catch (ExecutionException | InterruptedException e) {
      logger.atSevere().withCause(e).log("Exception while suggesting reviewers");
      return ImmutableList.of();
    }

    if (changeNotes != null) {
      // Remove change owner
      if (candidateScores.remove(changeNotes.getChange().getOwner()) != null) {
        logger.atFine().log("Remove change owner %s", changeNotes.getChange().getOwner());
      }

      // Remove existing reviewers
      approvalsUtil
          .getReviewers(changeNotes)
          .byState(ReviewerStateInternal.fromReviewerState(reviewerState))
          .forEach(
              r -> {
                if (candidateScores.remove(r) != null) {
                  logger.atFine().log("Remove existing reviewer %s", r);
                }
              });
    }

    // Sort results
    List<Account.Id> sortedSuggestions = sortSuggestions(candidateScores);
    logger.atFine().log("Sorted suggestions: %s", sortedSuggestions);
    return sortedSuggestions;
  }

  /**
   * Sorts the candidates by score, deprioritizing candidates that are in the attention set of many
   * open changes.
   *
   * <p>The scores of the top {@code suggest.attentionSetMaxCandidates} candidates are dampened by
   * the number of open changes in which the candidate is in the attention set. This spreads the
   * review load across equally well matching candidates instead of sending all reviews to the
   * candidate that ranks first. Candidates with equal dampened scores are ordered by ascending
   * attention set load.
   */
  private List<Account.Id> sortSuggestions(Map<Account.Id, MutableDouble> candidateScores) {
    List<Map.Entry<Account.Id, MutableDouble>> byScore =
        candidateScores.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
            .collect(toList());

    double attentionSetFactor = getAttentionSetFactor();
    int maxCandidates = config.getInt("suggest", "attentionSetMaxCandidates", 25);
    if (attentionSetFactor <= 0 || maxCandidates <= 0 || byScore.isEmpty()) {
      return byScore.stream().map(Map.Entry::getKey).collect(toList());
    }

    List<Map.Entry<Account.Id, MutableDouble>> top =
        byScore.subList(0, Math.min(maxCandidates, byScore.size()));
    ImmutableMap<Account.Id, Integer> attentionSetLoads =
        getAttentionSetLoads(top.stream().map(Map.Entry::getKey).collect(toImmutableList()));
    logger.atFine().log("Attention set loads: %s", attentionSetLoads);

    List<Account.Id> result = new ArrayList<>(byScore.size());
    top.stream()
        .sorted(
            Comparator.<Map.Entry<Account.Id, MutableDouble>>comparingDouble(
                    e -> {
                      int load = attentionSetLoads.getOrDefault(e.getKey(), 0);
                      return -(e.getValue().doubleValue() / (1 + attentionSetFactor * load));
                    })
                .thenComparingInt(e -> attentionSetLoads.getOrDefault(e.getKey(), 0)))
        .forEach(e -> result.add(e.getKey()));
    byScore.subList(top.size(), byScore.size()).forEach(e -> result.add(e.getKey()));
    return result;
  }

  private double getAttentionSetFactor() {
    String value = config.getString("suggest", null, "attentionSetFactor");
    if (Strings.isNullOrEmpty(value)) {
      return 0.1;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log(
          "Invalid value for suggest.attentionSetFactor: %s", value);
      return 0.1;
    }
  }

  /**
   * Returns for each of the given accounts the number of open changes in which the account is in
   * the attention set, capped at {@link #MAX_ATTENTION_SET_LOAD}.
   */
  private ImmutableMap<Account.Id, Integer> getAttentionSetLoads(
      ImmutableList<Account.Id> accountIds) {
    List<Callable<Integer>> tasks = new ArrayList<>(accountIds.size());
    for (Account.Id accountId : accountIds) {
      InternalChangeQuery internalChangeQuery =
          queryProvider
              .get()
              .setLimit(MAX_ATTENTION_SET_LOAD)
              .setRequestedFields(ChangeField.NUMERIC_ID_STR_SPEC);
      tasks.add(
          () ->
              internalChangeQuery
                  .query(
                      Predicate.and(
                          ChangeStatusPredicate.open(), ChangePredicates.attentionSet(accountId)))
                  .size());
    }

    ImmutableMap.Builder<Account.Id, Integer> loads = ImmutableMap.builder();
    try {
      List<Future<Integer>> futures =
          executor.invokeAll(tasks, ATTENTION_SET_QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
      for (int i = 0; i < futures.size(); i++) {
        if (futures.get(i).isCancelled()) {
          logger.atWarning().log(
              "Attention set query for account %s timed out", accountIds.get(i));
          continue;
        }
        loads.put(accountIds.get(i), futures.get(i).get());
      }
    } catch (ExecutionException | InterruptedException e) {
      logger.atWarning().withCause(e).log("Cannot compute attention set loads");
      return ImmutableMap.of();
    }
    return loads.buildOrThrow();
  }

  /**
   * Scores accounts that recently worked on the same files as the given change.
   *
   * <p>Considers open and merged changes in the same project that touched at least one of the
   * files of the current patch set and that were updated within {@code
   * suggest.touchedFilesMaxAgeDays}. The owner and the reviewers of each matching change are
   * scored with {@code weight}, linearly decayed by the age of the matching change.
   */
  private void addTouchedFilesCandidates(
      ChangeNotes changeNotes,
      ProjectState projectState,
      @Nullable String query,
      double weight,
      Map<Account.Id, MutableDouble> candidateScores) {
    int maxFiles = config.getInt("suggest", "touchedFilesMaxFiles", 20);
    int maxAgeDays = config.getInt("suggest", "touchedFilesMaxAgeDays", 90);
    if (maxFiles <= 0 || maxAgeDays <= 0) {
      return;
    }

    List<String> touchedFiles;
    try {
      touchedFiles =
          changeDataFactory.create(changeNotes).currentFilePaths().stream()
              .limit(maxFiles)
              .collect(toList());
    } catch (StorageException e) {
      logger.atWarning().withCause(e).log(
          "Cannot get files of change %s", changeNotes.getChangeId());
      return;
    }
    if (touchedFiles.isEmpty()) {
      return;
    }
    logger.atFine().log("Touched files: %s", touchedFiles);

    Predicate<ChangeData> predicate =
        Predicate.and(
            ChangePredicates.project(projectState.getNameKey()),
            Predicate.or(
                touchedFiles.stream().map(ChangePredicates::path).collect(toImmutableList())),
            Predicate.or(
                ChangeStatusPredicate.open(),
                ChangeStatusPredicate.forStatus(Change.Status.MERGED)),
            Predicate.not(new AgePredicate(maxAgeDays + "d")));

    ImmutableList<ChangeData> relatedChanges;
    try {
      relatedChanges =
          queryProvider
              .get()
              .setLimit(config.getInt("suggest", "relevantChanges", 50))
              .setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.REVIEWER_SPEC)
              .query(predicate);
    } catch (StorageException e) {
      logger.atWarning().withCause(e).log("Cannot query changes touching the same files");
      return;
    }

    Map<Account.Id, MutableDouble> touchedFilesScores = new LinkedHashMap<>();
    Instant now = TimeUtil.now();
    for (ChangeData cd : relatedChanges) {
      if (cd.getId().equals(changeNotes.getChangeId())) {
        continue;
      }
      double score = weight * recencyFactor(cd.change().getLastUpdatedOn(), now, maxAgeDays);
      touchedFilesScores
          .computeIfAbsent(cd.change().getOwner(), (ignored) -> new MutableDouble(0))
          .add(score);
      for (Account.Id reviewerId : cd.reviewers().all()) {
        touchedFilesScores
            .computeIfAbsent(reviewerId, (ignored) -> new MutableDouble(0))
            .add(score);
      }
    }

    ImmutableMap<Account.Id, AccountState> accountStates =
        accountCache.get(ImmutableSet.copyOf(touchedFilesScores.keySet()));
    touchedFilesScores.forEach(
        (accountId, score) -> {
          if (accountMatchesQuery(accountStates.get(accountId), query)) {
            candidateScores
                .computeIfAbsent(accountId, (ignored) -> new MutableDouble(0))
                .add(score.doubleValue());
          }
        });
    logger.atFine().log("Candidate scores after touched files boost: %s", candidateScores);
  }

  /**
   * Returns a factor in [0.25, 1.0] that is 1.0 for changes updated now and decreases linearly to
   * 0.25 for changes at the maximum age.
   */
  private static double recencyFactor(Instant lastUpdated, Instant now, int maxAgeDays) {
    double ageDays = Math.max(0, Duration.between(lastUpdated, now).toDays());
    return Math.max(0.25, Math.min(1.0, 1.0 - (ageDays / maxAgeDays)));
  }

  private ImmutableList<ChangeData> queryRecentChanges(Predicate<ChangeData> predicate) {
    int numberOfRelevantChanges = config.getInt("suggest", "relevantChanges", 50);
    return queryProvider
        .get()
        .setLimit(numberOfRelevantChanges)
        .setRequestedFields(ChangeField.REVIEWER_SPEC)
        .query(predicate);
  }

  private ImmutableList<Account.Id> getReviewers(ImmutableList<ChangeData> changes) {
    return changes.stream().flatMap(cd -> cd.reviewers().all().stream()).collect(toImmutableList());
  }

  private ImmutableList<Account.Id> getMatchingReviewers(
      ImmutableList<ChangeData> changes, String query) {
    ImmutableList<Account.Id> reviewerIds = getReviewers(changes);
    ImmutableMap<Account.Id, AccountState> reviewerStates =
        accountCache.get(ImmutableSet.copyOf(reviewerIds));
    return reviewerIds.stream()
        .filter(reviewerId -> accountMatchesQuery(reviewerStates.get(reviewerId), query))
        .collect(toImmutableList());
  }

  private boolean accountMatchesQuery(AccountState accountState, String query) {
    if (accountState == null) {
      return false;
    }
    Account account = accountState.account();
    if (account.isActive()) {
      String lowerCaseQuery = Strings.nullToEmpty(query).toLowerCase(Locale.US);
      if (Strings.isNullOrEmpty(lowerCaseQuery)
          || (account.fullName() != null
              && account.fullName().toLowerCase(Locale.US).startsWith(lowerCaseQuery))
          || (account.preferredEmail() != null
              && account.preferredEmail().toLowerCase(Locale.US).startsWith(lowerCaseQuery))) {
        return true;
      }
    }
    return false;
  }
}
