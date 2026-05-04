// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.restapi;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.IS_EXPLICITLY_MENTIONED_SCORING_VALUE;
import static com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore.NOT_EXPLICITLY_MENTIONED_SCORING_VALUE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnersInfo;
import com.google.gerrit.plugins.codeowners.backend.CodeOwner;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerAnnotation;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolver;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerResolverResult;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScore;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScoring;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerScorings;
import com.google.gerrit.plugins.codeowners.backend.DebugMessage;
import com.google.gerrit.plugins.codeowners.backend.Pair;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.kohsuke.args4j.Option;

/**
 * Abstract base class for REST endpoints that get the code owners for an arbitrary path in a branch
 * or a revision of a change.
 */
public abstract class AbstractGetCodeOwnersForPath<R extends AbstractPathResource>
    implements RestReadView<R> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final int DEFAULT_LIMIT = 10;

  private final AccountVisibility accountVisibility;
  private final Accounts accounts;
  private final AccountControl.Factory accountControlFactory;
  private final PermissionBackend permissionBackend;
  private final CheckCodeOwnerCapability checkCodeOwnerCapability;
  private final CodeOwnerMetrics codeOwnerMetrics;
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;
  private final Provider<CodeOwnerResolver> codeOwnerResolver;
  private final CodeOwnerJson.Factory codeOwnerJsonFactory;
  private final CodeOwnerConfigFileJson codeOwnerConfigFileJson;
  private final EnumSet<ListAccountsOption> options;
  private final Set<String> hexOptions;

  private int limit = DEFAULT_LIMIT;
  private Optional<Long> seed = Optional.empty();
  private boolean resolveAllUsers;
  private boolean highestScoreOnly;
  private boolean debug;

  @Option(
      name = "-o",
      usage = "Options to control which fields should be populated for the returned account")
  public void addOption(ListAccountsOption o) {
    options.add(o);
  }

  @Option(
      name = "-O",
      usage =
          "Options to control which fields should be populated for the returned account, in hex")
  void setOptionFlagsHex(String hex) {
    hexOptions.add(hex);
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of code owners to list (default = " + DEFAULT_LIMIT + ")")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--seed",
      usage = "seed that should be used to shuffle code owners that have the same score")
  public void setSeed(long seed) {
    this.seed = Optional.of(seed);
  }

  @Option(
      name = "--resolve-all-users",
      usage =
          "whether code ownerships that are assigned to all users should be resolved to random"
              + " users")
  public void setResolveAllUsers(boolean resolveAllUsers) {
    this.resolveAllUsers = resolveAllUsers;
  }

  @Option(
      name = "--highest-score-only",
      usage = "whether only code owners with the highest score should be returned")
  public void setHighestScoreOnly(boolean highestScoreOnly) {
    this.highestScoreOnly = highestScoreOnly;
  }

  @Option(
      name = "--debug",
      usage =
          "whether debug logs should be included into the response"
              + " (requires the 'Check Code Owner' global capability)")
  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  protected AbstractGetCodeOwnersForPath(
      AccountVisibility accountVisibility,
      Accounts accounts,
      AccountControl.Factory accountControlFactory,
      PermissionBackend permissionBackend,
      CheckCodeOwnerCapability checkCodeOwnerCapability,
      CodeOwnerMetrics codeOwnerMetrics,
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerConfigHierarchy codeOwnerConfigHierarchy,
      Provider<CodeOwnerResolver> codeOwnerResolver,
      CodeOwnerJson.Factory codeOwnerJsonFactory,
      CodeOwnerConfigFileJson codeOwnerConfigFileJson) {
    this.accountVisibility = accountVisibility;
    this.accounts = accounts;
    this.accountControlFactory = accountControlFactory;
    this.permissionBackend = permissionBackend;
    this.checkCodeOwnerCapability = checkCodeOwnerCapability;
    this.codeOwnerMetrics = codeOwnerMetrics;
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerConfigHierarchy = codeOwnerConfigHierarchy;
    this.codeOwnerResolver = codeOwnerResolver;
    this.codeOwnerJsonFactory = codeOwnerJsonFactory;
    this.codeOwnerConfigFileJson = codeOwnerConfigFileJson;
    this.options = EnumSet.noneOf(ListAccountsOption.class);
    this.hexOptions = new HashSet<>();
  }

  protected Response<CodeOwnersInfo> applyImpl(R rsrc)
      throws AuthException, BadRequestException, PermissionBackendException {
    parseHexOptions();
    validateLimit();

    if (debug) {
      permissionBackend.currentUser().check(checkCodeOwnerCapability.getPermission());
    }

    if (!seed.isPresent()) {
      seed = getDefaultSeed(rsrc);
    }

    codeOwnerMetrics.countCodeOwnerSuggestions.increment(resolveAllUsers);

    // The distance that applies to code owners that are defined in the root code owner
    // configuration.
    int rootDistance = rsrc.getPath().getNameCount();

    int defaultOwnersDistance = rootDistance + 1;
    int globalOwnersDistance = defaultOwnersDistance + 1;
    int maxDistance = globalOwnersDistance;

    CodeOwnerScoring.Builder distanceScoring = CodeOwnerScore.DISTANCE.createScoring(maxDistance);
    CodeOwnerScoring.Builder isExplicitlyMentionedScoring =
        CodeOwnerScore.IS_EXPLICITLY_MENTIONED.createScoring();

    Set<CodeOwner> codeOwners = new HashSet<>();
    ListMultimap<CodeOwner, CodeOwnerAnnotation> annotations = LinkedListMultimap.create();
    AtomicBoolean ownedByAllUsers = new AtomicBoolean(false);
    ImmutableList.Builder<DebugMessage> debugLogsBuilder = ImmutableList.builder();
    ImmutableList.Builder<CodeOwnerConfigFileInfo> codeOwnerConfigFileInfosBuilder =
        ImmutableList.builder();
    codeOwnerConfigHierarchy.visit(
        rsrc.getBranch(),
        rsrc.getRevision(),
        rsrc.getPath(),
        codeOwnerConfig -> {
          CodeOwnerResolverResult pathCodeOwners =
              codeOwnerResolver.get().resolvePathCodeOwners(codeOwnerConfig, rsrc.getPath());

          codeOwnerConfigFileInfosBuilder.add(
              codeOwnerConfigFileJson.format(
                  codeOwnerConfig,
                  pathCodeOwners.resolvedImports(),
                  pathCodeOwners.unresolvedImports()));

          debugLogsBuilder.addAll(pathCodeOwners.messages());
          codeOwners.addAll(pathCodeOwners.codeOwners());
          annotations.putAll(pathCodeOwners.annotations());

          int distance =
              codeOwnerConfig.key().branchNameKey().branch().equals(RefNames.REFS_CONFIG)
                  ? defaultOwnersDistance
                  : rootDistance - codeOwnerConfig.key().folderPath().getNameCount();
          pathCodeOwners
              .codeOwners()
              .forEach(
                  localCodeOwner -> {
                    distanceScoring.putValueForCodeOwner(localCodeOwner, distance);
                    isExplicitlyMentionedScoring.putValueForCodeOwner(
                        localCodeOwner, IS_EXPLICITLY_MENTIONED_SCORING_VALUE);
                  });

          if (pathCodeOwners.ownedByAllUsers()) {
            ownedByAllUsers.set(true);
            ImmutableSet<CodeOwner> addedCodeOwners = fillUpWithRandomUsers(codeOwners, limit);
            addedCodeOwners.forEach(
                localCodeOwner -> {
                  distanceScoring.putValueForCodeOwner(localCodeOwner, distance);
                  isExplicitlyMentionedScoring.putValueForCodeOwner(
                      localCodeOwner, NOT_EXPLICITLY_MENTIONED_SCORING_VALUE);
                });

            if (codeOwners.size() < limit) {
              logger.atFine().log(
                  "tried to fill up the suggestion list with random users,"
                      + " but didn't find enough visible accounts"
                      + " (wanted number of suggestions = %d, got = %d",
                  limit, codeOwners.size());
            }
          }

          // We always need to iterate over all relevant OWNERS files (even if the limit has already
          // been reached).
          // This is needed to collect distance scores for code owners that are mentioned in the
          // more distant OWNERS files. Those become relevant if further scores are applied later
          // (e.g. the score for current reviewers of the change).
          return true;
        });

    if (!ownedByAllUsers.get()) {
      CodeOwnerResolverResult globalCodeOwners = getGlobalCodeOwners(rsrc.getBranch().project());

      debugLogsBuilder.add(DebugMessage.createMessage("resolve global code owners"));
      debugLogsBuilder.addAll(globalCodeOwners.messages());

      globalCodeOwners
          .codeOwners()
          .forEach(
              codeOwner -> {
                distanceScoring.putValueForCodeOwner(codeOwner, globalOwnersDistance);
                isExplicitlyMentionedScoring.putValueForCodeOwner(
                    codeOwner, IS_EXPLICITLY_MENTIONED_SCORING_VALUE);
              });
      codeOwners.addAll(globalCodeOwners.codeOwners());

      if (globalCodeOwners.ownedByAllUsers()) {
        ownedByAllUsers.set(true);
        ImmutableSet<CodeOwner> addedCodeOwners = fillUpWithRandomUsers(codeOwners, limit);
        addedCodeOwners.forEach(
            codeOwner -> {
              distanceScoring.putValueForCodeOwner(codeOwner, globalOwnersDistance);
              isExplicitlyMentionedScoring.putValueForCodeOwner(
                  codeOwner, NOT_EXPLICITLY_MENTIONED_SCORING_VALUE);
            });
      }
    }

    ImmutableSet<CodeOwner> filteredCodeOwners =
        filterCodeOwners(
            rsrc,
            ImmutableMultimap.copyOf(annotations),
            ImmutableSet.copyOf(codeOwners),
            debugLogsBuilder);
    CodeOwnerScorings codeOwnerScorings =
        createScorings(
            rsrc,
            filteredCodeOwners,
            distanceScoring.build(),
            isExplicitlyMentionedScoring.build());
    ImmutableMap<CodeOwner, Double> scoredCodeOwners =
        codeOwnerScorings.getScorings(filteredCodeOwners);

    ImmutableList<CodeOwner> sortedAndLimitedCodeOwners = sortAndLimit(scoredCodeOwners);

    if (highestScoreOnly) {
      Optional<Double> highestScore =
          scoredCodeOwners.values().stream().max(Comparator.naturalOrder());
      if (highestScore.isPresent()) {
        sortedAndLimitedCodeOwners =
            sortedAndLimitedCodeOwners.stream()
                .filter(codeOwner -> scoredCodeOwners.get(codeOwner).equals(highestScore.get()))
                .collect(toImmutableList());
      }
    }
    ImmutableMap<CodeOwner, ImmutableMap<CodeOwnerScore, Integer>> codeOwnerToScorings =
        sortedAndLimitedCodeOwners.stream()
            .map(codeOwner -> Pair.of(codeOwner, codeOwnerScorings.getScoringsByScore(codeOwner)))
            .collect(toImmutableMap(Pair::key, Pair::value));
    ImmutableList<CodeOwnerInfo> codeOwnersInfoList =
        codeOwnerJsonFactory
            .create(getFillOptions())
            .format(sortedAndLimitedCodeOwners, codeOwnerToScorings);

    CodeOwnersInfo codeOwnersInfo = new CodeOwnersInfo();
    codeOwnersInfo.codeOwners = codeOwnersInfoList;
    codeOwnersInfo.ownedByAllUsers = ownedByAllUsers.get() ? true : null;
    codeOwnersInfo.codeOwnerConfigs = codeOwnerConfigFileInfosBuilder.build();
    ImmutableList<DebugMessage> debugLogs = debugLogsBuilder.build();
    codeOwnersInfo.debugLogs =
        debug
            ? debugLogs.stream().map(DebugMessage::adminMessage).collect(toImmutableList())
            : null;
    logger.atFine().log("debug logs: %s", debugLogs);

    return Response.ok(codeOwnersInfo);
  }

  private CodeOwnerScorings createScorings(
      R rsrc, ImmutableSet<CodeOwner> codeOwners, CodeOwnerScoring... scorings) {
    ImmutableSet.Builder<CodeOwnerScoring> codeOwnerScorings = ImmutableSet.builder();
    codeOwnerScorings.addAll(ImmutableSet.copyOf(scorings));
    codeOwnerScorings.addAll(getCodeOwnerScorings(rsrc, codeOwners));
    return CodeOwnerScorings.create(codeOwnerScorings.build());
  }

  private CodeOwnerResolverResult getGlobalCodeOwners(Project.NameKey projectName) {
    CodeOwnerResolverResult globalCodeOwners =
        codeOwnerResolver
            .get()
            .resolve(
                codeOwnersPluginConfiguration.getProjectConfig(projectName).getGlobalCodeOwners());
    logger.atFine().log("including global code owners = %s", globalCodeOwners);
    return globalCodeOwners;
  }

  /**
   * Get further code owner scorings.
   *
   * <p>To be overridden by subclasses to include further scorings.
   *
   * @param rsrc resource on which the request is being performed
   * @param codeOwners the code owners
   */
  protected ImmutableSet<CodeOwnerScoring> getCodeOwnerScorings(
      R rsrc, ImmutableSet<CodeOwner> codeOwners) {
    return ImmutableSet.of();
  }

  /**
   * Filters out code owners that should not be suggested.
   *
   * <p>The following code owners are filtered out:
   *
   * <ul>
   *   <li>code owners that cannot see the branch: Code owners that cannot see the branch cannot
   *       approve paths in this branch. Hence returning them to the client is not useful.
   *   <li>code owners that are service users: Requesting a code owner approval from a service user
   *       normally doesn't make sense since they will not react to review requests.
   * </ul>
   */
  private ImmutableSet<CodeOwner> filterCodeOwners(
      R rsrc,
      ImmutableMultimap<CodeOwner, CodeOwnerAnnotation> annotations,
      ImmutableSet<CodeOwner> codeOwners,
      ImmutableList.Builder<DebugMessage> debugLogs) {
    return filterCodeOwners(rsrc, annotations, getVisibleCodeOwners(rsrc, codeOwners), debugLogs)
        .collect(toImmutableSet());
  }

  /**
   * To be overridden by subclasses to filter out additional code owners.
   *
   * @param rsrc resource on which the request is being performed
   * @param annotations annotations that were set on the code owners
   * @param codeOwners stream of code owners that should be filtered
   * @param debugLogs builder to collect debug logs that may be returned to the caller
   * @return the filtered stream of code owners
   */
  protected Stream<CodeOwner> filterCodeOwners(
      R rsrc,
      ImmutableMultimap<CodeOwner, CodeOwnerAnnotation> annotations,
      Stream<CodeOwner> codeOwners,
      ImmutableList.Builder<DebugMessage> debugLogs) {
    return codeOwners;
  }

  /**
   * Returns the seed that should by default be used for sorting, if none was specified on the
   * request.
   *
   * <p>If {@link Optional#empty()} is returned, a random seed will be used.
   *
   * @param rsrc resource on which the request is being performed
   */
  protected Optional<Long> getDefaultSeed(R rsrc) {
    return Optional.empty();
  }

  private Stream<CodeOwner> getVisibleCodeOwners(R rsrc, ImmutableSet<CodeOwner> allCodeOwners) {
    return allCodeOwners.stream()
        .filter(
            codeOwner -> {
              if (isVisibleTo(rsrc, codeOwner)) {
                return true;
              }
              logger.atFine().log(
                  "Filtering out %s because this code owner cannot see the branch %s",
                  codeOwner, rsrc.getBranch().branch());
              return false;
            });
  }

  /** Whether the given resource is visible to the given code owner. */
  private boolean isVisibleTo(R rsrc, CodeOwner codeOwner) {
    // We always check for the visibility of the branch.
    // This is also correct for the GetCodeOwnersForPathInChange subclass where branch is the
    // destination branch of the change. For changes the intention of the visibility check is to
    // check whether the code owner could be added as reviewer to the change. For this it is
    // important whether the destination branch is visible to the code owner, rather than whether
    // the change is visible to the code owner. E.g. private changes are not visible to other users
    // unless they are added as a reviewer. This means, for private changes we want to suggest code
    // owners that cannot see the change, since adding them as a reviewer is possible. By adding the
    // code owner as a reviewer to the private change, the change becomes visible to them. This
    // behavior is consistent with the suggest reviewer implementation (see
    // SuggestChangeReviewers#getVisibility(ChangeControl).
    return permissionBackend
        .absentUser(codeOwner.accountId())
        .ref(rsrc.getBranch())
        .testOrFalse(RefPermission.READ);
  }

  private void parseHexOptions() throws BadRequestException {
    for (String hexOption : hexOptions) {
      try {
        options.addAll(
            ListOption.fromBits(ListAccountsOption.class, Integer.parseInt(hexOption, 16)));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(
            String.format("\"%s\" is not a valid value for \"-O\"", hexOption), e);
      }
    }
  }

  private void validateLimit() throws BadRequestException {
    if (limit <= 0) {
      throw new BadRequestException("limit must be positive");
    }
  }

  private Set<FillOptions> getFillOptions() throws AuthException, PermissionBackendException {
    Set<FillOptions> fillOptions = EnumSet.of(FillOptions.ID);
    if (options.contains(ListAccountsOption.DETAILS)) {
      fillOptions.addAll(AccountLoader.DETAILED_OPTIONS);
    }
    if (options.contains(ListAccountsOption.ALL_EMAILS)) {
      // Secondary emails are only visible to users that have the 'Modify Account' global
      // capability.
      permissionBackend.currentUser().check(GlobalPermission.VIEW_SECONDARY_EMAILS);
      fillOptions.add(FillOptions.EMAIL);
      fillOptions.add(FillOptions.SECONDARY_EMAILS);
    }
    return fillOptions;
  }

  private ImmutableList<CodeOwner> sortAndLimit(ImmutableMap<CodeOwner, Double> scoredCodeOwners) {
    return sortCodeOwners(seed, scoredCodeOwners).limit(limit).collect(toImmutableList());
  }

  /**
   * Sorts the code owners.
   *
   * <p>Code owners with higher score are returned first.
   *
   * <p>The order of code owners with the same score is random.
   *
   * @param seed seed that should be used to randomize the order
   * @param scoredCodeOwners the code owners with their scores
   * @return the sorted code owners
   */
  private Stream<CodeOwner> sortCodeOwners(
      Optional<Long> seed, ImmutableMap<CodeOwner, Double> scoredCodeOwners) {
    return randomizeOrder(seed, scoredCodeOwners.keySet())
        .sorted(Comparator.comparingDouble(scoredCodeOwners::get).reversed());
  }

  /**
   * Returns the entries from the given set in a random order.
   *
   * @param seed seed that should be used to randomize the order
   * @param set the set for which the entries should be returned in a random order
   * @return the entries from the given set in a random order
   */
  private static <T> Stream<T> randomizeOrder(Optional<Long> seed, Set<T> set) {
    List<T> randomlyOrderedCodeOwners = new ArrayList<>(set);
    Collections.shuffle(
        randomlyOrderedCodeOwners, seed.isPresent() ? new Random(seed.get()) : new Random());
    return randomlyOrderedCodeOwners.stream();
  }

  /**
   * If the limit is not reached yet, add random visible users as code owners to the given code
   * owner set.
   *
   * <p>Must be only used to complete the suggestion list when it is found that the path is owned by
   * all user.
   *
   * <p>No-op if code ownership for all users should not be resolved.
   *
   * @return the added code owners
   */
  private ImmutableSet<CodeOwner> fillUpWithRandomUsers(Set<CodeOwner> codeOwners, int limit) {
    if (!resolveAllUsers || codeOwners.size() >= limit) {
      // code ownership for all users should not be resolved or the limit has already been reached
      // so that we don't need to add further suggestions
      return ImmutableSet.of();
    }

    logger.atFine().log("filling up with random users");
    ImmutableSet<CodeOwner> codeOwnersToAdd =
        // ask for 2 times the number of users that we need so that we still have enough
        // suggestions when some users are removed on the filter step later or if the returned users
        // were already present in codeOwners
        getRandomVisibleUsers(2 * limit - codeOwners.size())
            .map(CodeOwner::create)
            .collect(toImmutableSet())
            .stream()
            .filter(codeOwner -> !codeOwners.contains(codeOwner))
            .collect(toImmutableSet());
    codeOwners.addAll(codeOwnersToAdd);
    return codeOwnersToAdd;
  }

  /**
   * Returns random visible users, at most as many as specified by the limit.
   *
   * <p>It's possible that this method returns less users than the limit although further visible
   * users exist. This is because we may inspect only a random set of users, instead of all users,
   * for performance reasons.
   *
   * @param limit the max number of users that should be returned
   * @return random visible users
   */
  private Stream<Account.Id> getRandomVisibleUsers(int limit) {
    try {
      if (permissionBackend.currentUser().test(GlobalPermission.VIEW_ALL_ACCOUNTS)) {
        return getRandomUsers(limit);
      }

      switch (accountVisibility) {
        case ALL:
          return getRandomUsers(limit);
        case SAME_GROUP:
        case VISIBLE_GROUP:
          // We cannot afford to inspect all relevant users and test their visibility for
          // performance reasons, hence we use a random sample of users that is 3 times the limit.
          return getRandomUsers(3 * limit)
              .filter(accountId -> accountControlFactory.get().canSee(accountId))
              .limit(limit);
        case NONE:
          return Stream.of();
      }

      throw new IllegalStateException("unknown account visibility setting: " + accountVisibility);
    } catch (IOException | PermissionBackendException e) {
      throw new StorageException("failed to get visible users", e);
    }
  }

  /**
   * Returns random users, at most as many as specified by the limit.
   *
   * <p>No visibility check is performed.
   */
  private Stream<Account.Id> getRandomUsers(int limit) throws IOException {
    return accounts
        .randomNIds(
            limit, seed.isPresent() ? seed.get() : ThreadLocalRandom.current().nextLong())
        .stream();
  }
}
