// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.entities.Change.CHANGE_ID_PATTERN;
import static com.google.gerrit.server.account.AccountResolver.isSelf;
import static com.google.gerrit.server.query.change.ChangeData.asChanges;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.exceptions.NotSignedInException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.index.query.LimitPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryRequiresAuthException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.DestinationList;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.account.VersionedAccountDestinations;
import com.google.gerrit.server.account.VersionedAccountQueries;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.HasOperandAliasConfig;
import com.google.gerrit.server.config.OperatorAliasConfig;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.PredicateArgs.ValOp;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Parses a query string meant to be applied to change objects. */
public class ChangeQueryBuilder extends QueryBuilder<ChangeData, ChangeQueryBuilder> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface ChangeOperatorFactory extends OperatorFactory<ChangeData, ChangeQueryBuilder> {}

  /**
   * Converts a operand (operator value) passed to an operator into a {@link Predicate}.
   *
   * <p>Register a ChangeOperandFactory in a config Module like this (note, for an example we are
   * using the has predicate, when other predicate plugin operands are created they can be
   * registered in a similar manner):
   *
   * <p>bind(ChangeHasOperandFactory.class) .annotatedWith(Exports.named("your has operand"))
   * .to(YourClass.class);
   */
  public interface ChangeOperandFactory {
    Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException;
  }

  public interface ChangeHasOperandFactory extends ChangeOperandFactory {}

  public interface ChangeIsOperandFactory extends ChangeOperandFactory {}

  private static final Pattern PAT_LEGACY_ID = Pattern.compile("^[1-9][0-9]*$");
  private static final Pattern PAT_CHANGE_ID = Pattern.compile(CHANGE_ID_PATTERN);
  private static final Pattern DEF_CHANGE =
      Pattern.compile("^(?:[1-9][0-9]*|(?:[^~]+~[^~]+~)?[iI][0-9a-f]{4,}.*)$");

  static final int MAX_ACCOUNTS_PER_DEFAULT_FIELD = 10;

  // NOTE: As new search operations are added, please keep the suggestions in
  // gr-search-bar.ts up to date.

  public static final String FIELD_ADDED = "added";
  public static final String FIELD_AGE = "age";
  public static final String FIELD_ATTENTION_SET_USERS = "attentionusers";
  public static final String FIELD_ATTENTION_SET_USERS_COUNT = "attentionuserscount";
  public static final String FIELD_ATTENTION_SET_FULL = "attentionfull";
  public static final String FIELD_ASSIGNEE = "assignee";
  public static final String FIELD_AUTHOR = "author";
  public static final String FIELD_EXACTAUTHOR = "exactauthor";

  public static final String FIELD_CHANGE = "change";
  public static final String FIELD_CHANGE_ID = "change_id";
  public static final String FIELD_COMMENT = "comment";
  public static final String FIELD_COMMENTBY = "commentby";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_COMMITTER = "committer";
  public static final String FIELD_DIRECTORY = "directory";
  public static final String FIELD_EXACTCOMMITTER = "exactcommitter";
  public static final String FIELD_EXTENSION = "extension";
  public static final String FIELD_ONLY_EXTENSIONS = "onlyextensions";
  public static final String FIELD_FOOTER = "footer";
  public static final String FIELD_FOOTER_NAME = "footernames";
  public static final String FIELD_CONFLICTS = "conflicts";
  public static final String FIELD_DELETED = "deleted";
  public static final String FIELD_DELTA = "delta";
  public static final String FIELD_DESTINATION = "destination";
  public static final String FIELD_DRAFTBY = "draftby";
  public static final String FIELD_EDITBY = "editby";
  public static final String FIELD_EXACTCOMMIT = "exactcommit";
  public static final String FIELD_FILE = "file";
  public static final String FIELD_FILEPART = "filepart";
  public static final String FIELD_GROUP = "group";
  public static final String FIELD_HASHTAG = "hashtag";
  public static final String FIELD_LABEL = "label";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_MERGE = "merge";
  public static final String FIELD_MERGEABLE = "mergeable2";
  public static final String FIELD_MERGED_ON = "mergedon";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_MESSAGE_EXACT = "messageexact";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_OWNERIN = "ownerin";
  public static final String FIELD_PARENTOF = "parentof";
  public static final String FIELD_PARENTPROJECT = "parentproject";
  public static final String FIELD_PENDING_REVIEWER = "pendingreviewer";
  public static final String FIELD_PENDING_REVIEWER_BY_EMAIL = "pendingreviewerbyemail";
  public static final String FIELD_PRIVATE = "private";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_PROJECTS = "projects";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWEDBY = "reviewedby";
  public static final String FIELD_REVIEWERIN = "reviewerin";
  public static final String FIELD_STAR = "star";
  public static final String FIELD_STARBY = "starby";
  public static final String FIELD_STARTED = "started";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_SUBMISSIONID = "submissionid";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_UNRESOLVED_COMMENT_COUNT = "unresolved";
  public static final String FIELD_UPLOADER = "uploader";
  public static final String FIELD_UPLOADERIN = "uploaderin";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";
  public static final String FIELD_WIP = "wip";
  public static final String FIELD_REVERTOF = "revertof";
  public static final String FIELD_PURE_REVERT = "ispurerevert";
  public static final String FIELD_CHERRYPICK = "cherrypick";
  public static final String FIELD_CHERRY_PICK_OF_CHANGE = "cherrypickofchange";
  public static final String FIELD_CHERRY_PICK_OF_PATCHSET = "cherrypickofpatchset";
  public static final String FIELD_IS_SUBMITTABLE = "issubmittable";

  public static final String ARG_ID_NAME = "name";
  public static final String ARG_ID_USER = "user";
  public static final String ARG_ID_GROUP = "group";
  public static final String ARG_ID_OWNER = "owner";
  public static final String ARG_ID_NON_UPLOADER = "non_uploader";
  public static final String ARG_COUNT = "count";
  public static final Account.Id OWNER_ACCOUNT_ID = Account.id(0);
  public static final Account.Id NON_UPLOADER_ACCOUNT_ID = Account.id(-1);

  public static final String OPERATOR_MERGED_BEFORE = "mergedbefore";
  public static final String OPERATOR_MERGED_AFTER = "mergedafter";

  // Operators to match on the last time the change was updated. Naming for legacy reasons.
  public static final String OPERATOR_BEFORE = "before";
  public static final String OPERATOR_AFTER = "after";

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ChangeQueryBuilder.class);

  @VisibleForTesting
  public static class Arguments {
    final AccountCache accountCache;
    final AccountResolver accountResolver;
    final AllProjectsName allProjectsName;
    final AllUsersName allUsersName;
    final PermissionBackend permissionBackend;
    final ChangeData.Factory changeDataFactory;
    final ChangeIndex index;
    final ChangeIndexRewriter rewriter;
    final CommentsUtil commentsUtil;
    final ConflictsCache conflictsCache;
    final DynamicMap<ChangeHasOperandFactory> hasOperands;
    final DynamicMap<ChangeIsOperandFactory> isOperands;
    final DynamicMap<ChangeOperatorFactory> opFactories;
    final GitRepositoryManager repoManager;
    final GroupBackend groupBackend;
    final IdentifiedUser.GenericFactory userFactory;
    final IndexConfig indexConfig;
    final PatchListCache patchListCache;
    final ProjectCache projectCache;
    final Provider<InternalChangeQuery> queryProvider;
    final ChildProjects childProjects;
    final StarredChangesUtil starredChangesUtil;
    final SubmitDryRun submitDryRun;
    final GroupMembers groupMembers;
    final ChangeIsVisibleToPredicate.Factory changeIsVisbleToPredicateFactory;
    final OperatorAliasConfig operatorAliasConfig;
    final boolean indexMergeable;
    final boolean conflictsPredicateEnabled;
    final ExperimentFeatures experimentFeatures;
    final HasOperandAliasConfig hasOperandAliasConfig;
    final PluginSetContext<SubmitRule> submitRules;

    private final Provider<CurrentUser> self;

    @Inject
    @VisibleForTesting
    public Arguments(
        Provider<InternalChangeQuery> queryProvider,
        ChangeIndexRewriter rewriter,
        DynamicMap<ChangeOperatorFactory> opFactories,
        DynamicMap<ChangeHasOperandFactory> hasOperands,
        DynamicMap<ChangeIsOperandFactory> isOperands,
        IdentifiedUser.GenericFactory userFactory,
        Provider<CurrentUser> self,
        PermissionBackend permissionBackend,
        ChangeData.Factory changeDataFactory,
        CommentsUtil commentsUtil,
        AccountResolver accountResolver,
        GroupBackend groupBackend,
        AllProjectsName allProjectsName,
        AllUsersName allUsersName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager,
        ProjectCache projectCache,
        ChildProjects childProjects,
        ChangeIndexCollection indexes,
        SubmitDryRun submitDryRun,
        ConflictsCache conflictsCache,
        IndexConfig indexConfig,
        StarredChangesUtil starredChangesUtil,
        AccountCache accountCache,
        GroupMembers groupMembers,
        OperatorAliasConfig operatorAliasConfig,
        @GerritServerConfig Config gerritConfig,
        ExperimentFeatures experimentFeatures,
        HasOperandAliasConfig hasOperandAliasConfig,
        ChangeIsVisibleToPredicate.Factory changeIsVisbleToPredicateFactory,
        PluginSetContext<SubmitRule> submitRules) {
      this(
          queryProvider,
          rewriter,
          opFactories,
          hasOperands,
          isOperands,
          userFactory,
          self,
          permissionBackend,
          changeDataFactory,
          commentsUtil,
          accountResolver,
          groupBackend,
          allProjectsName,
          allUsersName,
          patchListCache,
          repoManager,
          projectCache,
          childProjects,
          submitDryRun,
          conflictsCache,
          indexes != null ? indexes.getSearchIndex() : null,
          indexConfig,
          starredChangesUtil,
          accountCache,
          groupMembers,
          operatorAliasConfig,
          MergeabilityComputationBehavior.fromConfig(gerritConfig).includeInIndex(),
          gerritConfig.getBoolean("change", null, "conflictsPredicateEnabled", true),
          experimentFeatures,
          hasOperandAliasConfig,
          changeIsVisbleToPredicateFactory,
          submitRules);
    }

    private Arguments(
        Provider<InternalChangeQuery> queryProvider,
        ChangeIndexRewriter rewriter,
        DynamicMap<ChangeOperatorFactory> opFactories,
        DynamicMap<ChangeHasOperandFactory> hasOperands,
        DynamicMap<ChangeIsOperandFactory> isOperands,
        IdentifiedUser.GenericFactory userFactory,
        Provider<CurrentUser> self,
        PermissionBackend permissionBackend,
        ChangeData.Factory changeDataFactory,
        CommentsUtil commentsUtil,
        AccountResolver accountResolver,
        GroupBackend groupBackend,
        AllProjectsName allProjectsName,
        AllUsersName allUsersName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager,
        ProjectCache projectCache,
        ChildProjects childProjects,
        SubmitDryRun submitDryRun,
        ConflictsCache conflictsCache,
        ChangeIndex index,
        IndexConfig indexConfig,
        StarredChangesUtil starredChangesUtil,
        AccountCache accountCache,
        GroupMembers groupMembers,
        OperatorAliasConfig operatorAliasConfig,
        boolean indexMergeable,
        boolean conflictsPredicateEnabled,
        ExperimentFeatures experimentFeatures,
        HasOperandAliasConfig hasOperandAliasConfig,
        ChangeIsVisibleToPredicate.Factory changeIsVisbleToPredicateFactory,
        PluginSetContext<SubmitRule> submitRules) {
      this.queryProvider = queryProvider;
      this.rewriter = rewriter;
      this.opFactories = opFactories;
      this.userFactory = userFactory;
      this.self = self;
      this.permissionBackend = permissionBackend;
      this.changeDataFactory = changeDataFactory;
      this.commentsUtil = commentsUtil;
      this.accountResolver = accountResolver;
      this.groupBackend = groupBackend;
      this.allProjectsName = allProjectsName;
      this.allUsersName = allUsersName;
      this.patchListCache = patchListCache;
      this.repoManager = repoManager;
      this.projectCache = projectCache;
      this.childProjects = childProjects;
      this.submitDryRun = submitDryRun;
      this.conflictsCache = conflictsCache;
      this.index = index;
      this.indexConfig = indexConfig;
      this.starredChangesUtil = starredChangesUtil;
      this.accountCache = accountCache;
      this.hasOperands = hasOperands;
      this.isOperands = isOperands;
      this.groupMembers = groupMembers;
      this.changeIsVisbleToPredicateFactory = changeIsVisbleToPredicateFactory;
      this.operatorAliasConfig = operatorAliasConfig;
      this.indexMergeable = indexMergeable;
      this.conflictsPredicateEnabled = conflictsPredicateEnabled;
      this.experimentFeatures = experimentFeatures;
      this.hasOperandAliasConfig = hasOperandAliasConfig;
      this.submitRules = submitRules;
    }

    public Arguments asUser(CurrentUser otherUser) {
      return new Arguments(
          queryProvider,
          rewriter,
          opFactories,
          hasOperands,
          isOperands,
          userFactory,
          Providers.of(otherUser),
          permissionBackend,
          changeDataFactory,
          commentsUtil,
          accountResolver,
          groupBackend,
          allProjectsName,
          allUsersName,
          patchListCache,
          repoManager,
          projectCache,
          childProjects,
          submitDryRun,
          conflictsCache,
          index,
          indexConfig,
          starredChangesUtil,
          accountCache,
          groupMembers,
          operatorAliasConfig,
          indexMergeable,
          conflictsPredicateEnabled,
          experimentFeatures,
          hasOperandAliasConfig,
          changeIsVisbleToPredicateFactory,
          submitRules);
    }

    Arguments asUser(Account.Id otherId) {
      try {
        CurrentUser u = self.get();
        if (u.isIdentifiedUser() && otherId.equals(u.getAccountId())) {
          return this;
        }
      } catch (ProvisionException e) {
        // Doesn't match current user, continue.
      }
      return asUser(userFactory.create(otherId));
    }

    IdentifiedUser getIdentifiedUser() throws QueryRequiresAuthException {
      try {
        CurrentUser u = getUser();
        if (u.isIdentifiedUser()) {
          return u.asIdentifiedUser();
        }
        throw new QueryRequiresAuthException(NotSignedInException.MESSAGE);
      } catch (ProvisionException e) {
        throw new QueryRequiresAuthException(NotSignedInException.MESSAGE, e);
      }
    }

    CurrentUser getUser() throws QueryRequiresAuthException {
      try {
        return self.get();
      } catch (ProvisionException e) {
        throw new QueryRequiresAuthException(NotSignedInException.MESSAGE, e);
      }
    }

    @Nullable
    Schema<ChangeData> getSchema() {
      return index != null ? index.getSchema() : null;
    }
  }

  private final Arguments args;
  protected Map<String, String> hasOperandAliases = Collections.emptyMap();
  private Map<Account.Id, DestinationList> destinationListByAccount = new HashMap<>();

  private static final Splitter RULE_SPLITTER = Splitter.on("=");
  private static final Splitter PLUGIN_SPLITTER = Splitter.on("_");
  private static final Splitter LABEL_SPLITTER = Splitter.on(",");

  @Inject
  protected ChangeQueryBuilder(Arguments args) {
    this(mydef, args);
    setupAliases();
  }

  @VisibleForTesting
  protected ChangeQueryBuilder(Definition<ChangeData, ChangeQueryBuilder> def, Arguments args) {
    super(def, args.opFactories);
    this.args = args;
  }

  private void setupAliases() {
    setOperatorAliases(args.operatorAliasConfig.getChangeQueryOperatorAliases());
    hasOperandAliases = args.hasOperandAliasConfig.getChangeQueryHasOperandAliases();
  }

  public ChangeQueryBuilder asUser(CurrentUser user) {
    return new ChangeQueryBuilder(builderDef, args.asUser(user));
  }

  public Arguments getArgs() {
    return args;
  }

  @Operator
  public Predicate<ChangeData> age(String value) {
    return new AgePredicate(value);
  }

  @Operator
  public Predicate<ChangeData> before(String value) throws QueryParseException {
    return new BeforePredicate(ChangeField.UPDATED, ChangeQueryBuilder.OPERATOR_BEFORE, value);
  }

  @Operator
  public Predicate<ChangeData> until(String value) throws QueryParseException {
    return before(value);
  }

  @Operator
  public Predicate<ChangeData> after(String value) throws QueryParseException {
    return new AfterPredicate(ChangeField.UPDATED, ChangeQueryBuilder.OPERATOR_AFTER, value);
  }

  @Operator
  public Predicate<ChangeData> since(String value) throws QueryParseException {
    return after(value);
  }

  @Operator
  public Predicate<ChangeData> mergedBefore(String value) throws QueryParseException {
    checkFieldAvailable(ChangeField.MERGED_ON_SPEC, OPERATOR_MERGED_BEFORE);
    return new BeforePredicate(
        ChangeField.MERGED_ON_SPEC, ChangeQueryBuilder.OPERATOR_MERGED_BEFORE, value);
  }

  @Operator
  public Predicate<ChangeData> mergedAfter(String value) throws QueryParseException {
    checkFieldAvailable(ChangeField.MERGED_ON_SPEC, OPERATOR_MERGED_AFTER);
    return new AfterPredicate(
        ChangeField.MERGED_ON_SPEC, ChangeQueryBuilder.OPERATOR_MERGED_AFTER, value);
  }

  @Operator
  public Predicate<ChangeData> change(String query) throws QueryParseException {
    Optional<ChangeTriplet> triplet = ChangeTriplet.parse(query);
    if (triplet.isPresent()) {
      return Predicate.and(
          project(triplet.get().project().get()),
          branch(triplet.get().branch().branch()),
          ChangePredicates.idPrefix(parseChangeId(triplet.get().id().get())));
    }
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      Integer id = Ints.tryParse(query);
      if (id != null) {
        return ChangePredicates.idStr(Change.id(id));
      }
    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      return ChangePredicates.idPrefix(parseChangeId(query));
    }

    throw new QueryParseException("Invalid change format");
  }

  @Operator
  public Predicate<ChangeData> comment(String value) {
    return ChangePredicates.comment(value);
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) throws QueryParseException {
    if ("reviewed".equalsIgnoreCase(statusName)) {
      return ChangePredicates.unreviewed();
    }
    return ChangeStatusPredicate.parse(statusName);
  }

  public Predicate<ChangeData> statusOpen() {
    return ChangeStatusPredicate.open();
  }

  @Operator
  public Predicate<ChangeData> rule(String value) throws QueryParseException {
    String ruleNameArg = value;
    String statusArg = null;
    List<String> queryArgs = RULE_SPLITTER.splitToList(value);
    if (queryArgs.size() > 2) {
      throw new QueryParseException(
          "Invalid query arguments. Correct format is 'rule:<rule_name>=<status>' "
              + "with <rule_name> in the form of <plugin>~<rule>. For Gerrit core rules, "
              + "rule name should be specified as gerrit~<rule>.");
    }
    if (queryArgs.size() == 2) {
      ruleNameArg = queryArgs.get(0);
      statusArg = queryArgs.get(1);
    }

    return statusArg == null
        ? Predicate.or(
            Arrays.asList(
                ChangePredicates.submitRuleStatus(ruleNameArg + "=" + SubmitRecord.Status.OK),
                ChangePredicates.submitRuleStatus(ruleNameArg + "=" + SubmitRecord.Status.FORCED)))
        : ChangePredicates.submitRuleStatus(ruleNameArg + "=" + statusArg);
  }

  @Operator
  public Predicate<ChangeData> has(String value) throws QueryParseException {
    value = hasOperandAliases.getOrDefault(value, value);
    if ("star".equalsIgnoreCase(value)) {
      return starredBySelf();
    }

    if ("draft".equalsIgnoreCase(value)) {
      return draftBySelf();
    }

    if ("edit".equalsIgnoreCase(value)) {
      return ChangePredicates.editBy(self());
    }

    if ("attention".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.ATTENTION_SET_USERS, "has:attention");
      return new IsAttentionPredicate();
    }

    if ("unresolved".equalsIgnoreCase(value)) {
      return new IsUnresolvedPredicate();
    }

    // for plugins the value will be operandName_pluginName
    List<String> names = PLUGIN_SPLITTER.splitToList(value);
    if (names.size() == 2) {
      ChangeHasOperandFactory op = args.hasOperands.get(names.get(1), names.get(0));
      if (op != null) {
        return op.create(this);
      }
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> is(String value) throws QueryParseException {
    if ("starred".equalsIgnoreCase(value)) {
      return starredBySelf();
    }

    if ("watched".equalsIgnoreCase(value)) {
      return new IsWatchedByPredicate(args);
    }

    if ("visible".equalsIgnoreCase(value)) {
      return isVisible();
    }

    if ("reviewed".equalsIgnoreCase(value)) {
      return ChangePredicates.unreviewed();
    }

    if ("owner".equalsIgnoreCase(value)) {
      return ChangePredicates.owner(self());
    }

    if ("uploader".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.UPLOADER_SPEC, "is:uploader");
      return ChangePredicates.uploader(self());
    }

    if ("reviewer".equalsIgnoreCase(value)) {
      return Predicate.and(
          Predicate.not(new BooleanPredicate(ChangeField.WIP)), ReviewerPredicate.reviewer(self()));
    }

    if ("cc".equalsIgnoreCase(value)) {
      return ReviewerPredicate.cc(self());
    }

    if ("mergeable".equalsIgnoreCase(value)) {
      if (!args.indexMergeable) {
        throw new QueryParseException("'is:mergeable' operator is not supported by server");
      }
      return new BooleanPredicate(ChangeField.MERGEABLE);
    }

    if ("merge".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.MERGE, "is:merge");
      return new BooleanPredicate(ChangeField.MERGE);
    }

    if ("private".equalsIgnoreCase(value)) {
      return new BooleanPredicate(ChangeField.PRIVATE);
    }

    if ("attention".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.ATTENTION_SET_USERS, "is:attention");
      return new IsAttentionPredicate();
    }

    if ("assigned".equalsIgnoreCase(value)) {
      return Predicate.not(ChangePredicates.assignee(Account.id(ChangeField.NO_ASSIGNEE)));
    }

    if ("unassigned".equalsIgnoreCase(value)) {
      return ChangePredicates.assignee(Account.id(ChangeField.NO_ASSIGNEE));
    }

    if ("pure-revert".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.IS_PURE_REVERT_SPEC, "is:pure-revert");
      return ChangePredicates.pureRevert("1");
    }

    if ("submittable".equalsIgnoreCase(value)) {
      if (!args.index.getSchema().hasField(ChangeField.IS_SUBMITTABLE_SPEC)) {
        // SubmittablePredicate will match if *any* of the submit records are OK,
        // but we need to check that they're *all* OK, so check that none of the
        // submit records match any of the negative cases. To avoid checking yet
        // more negative cases for CLOSED and FORCED, instead make sure at least
        // one submit record is OK.
        return Predicate.and(
            new SubmittablePredicate(SubmitRecord.Status.OK),
            Predicate.not(new SubmittablePredicate(SubmitRecord.Status.NOT_READY)),
            Predicate.not(new SubmittablePredicate(SubmitRecord.Status.RULE_ERROR)));
      }
      checkFieldAvailable(ChangeField.IS_SUBMITTABLE_SPEC, "is:submittable");
      return new IsSubmittablePredicate();
    }

    if ("started".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.STARTED, "is:started");
      return new BooleanPredicate(ChangeField.STARTED);
    }

    if ("wip".equalsIgnoreCase(value)) {
      return new BooleanPredicate(ChangeField.WIP);
    }

    if ("cherrypick".equalsIgnoreCase(value)) {
      checkFieldAvailable(ChangeField.CHERRY_PICK, "is:cherrypick");
      return new BooleanPredicate(ChangeField.CHERRY_PICK);
    }

    // for plugins the value will be operandName_pluginName
    List<String> names = PLUGIN_SPLITTER.splitToList(value);
    if (names.size() == 2) {
      ChangeIsOperandFactory op = args.isOperands.get(names.get(1), names.get(0));
      if (op != null) {
        return op.create(this);
      }
    }
    return status(value);
  }

  @Operator
  public Predicate<ChangeData> commit(String id) {
    return ChangePredicates.commitPrefix(id);
  }

  @Operator
  public Predicate<ChangeData> conflicts(String value) throws QueryParseException {
    if (!args.conflictsPredicateEnabled) {
      throw new QueryParseException("'conflicts:' operator is not supported by server");
    }
    List<Change> changes = parseChange(value);
    List<Predicate<ChangeData>> or = new ArrayList<>(changes.size());
    for (Change c : changes) {
      or.add(ConflictsPredicate.create(args, value, c));
    }
    return Predicate.or(or);
  }

  @Operator
  public Predicate<ChangeData> p(String name) {
    return project(name);
  }

  @Operator
  public Predicate<ChangeData> project(String name) {
    if (name.startsWith("^")) {
      return new RegexProjectPredicate(name);
    }
    return ChangePredicates.project(Project.nameKey(name));
  }

  @Operator
  public Predicate<ChangeData> projects(String name) {
    return ChangePredicates.projectPrefix(name);
  }

  @Operator
  public Predicate<ChangeData> parentof(String value) throws QueryParseException {
    List<ChangeData> changes = parseChangeData(value);
    List<Predicate<ChangeData>> or = new ArrayList<>(changes.size());
    for (ChangeData c : changes) {
      for (RevCommit revCommit : getParents(c)) {
        or.add(ChangePredicates.commitPrefix(revCommit.getId().getName()));
      }
    }
    return Predicate.or(or);
  }

  private Set<RevCommit> getParents(ChangeData change) {
    PatchSet ps = change.currentPatchSet();
    try (Repository repo = args.repoManager.openRepository(change.project());
        RevWalk walk = new RevWalk(repo)) {
      RevCommit c = walk.parseCommit(ps.commitId());
      return Sets.newHashSet(c.getParents());
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Loading commit %s for ps %d of change %d failed.",
              ps.commitId(), ps.id().get(), ps.id().changeId().get()),
          e);
    }
  }

  @Operator
  public Predicate<ChangeData> parentproject(String name) {
    return new ParentProjectPredicate(args.projectCache, args.childProjects, name);
  }

  @Operator
  public Predicate<ChangeData> repository(String name) {
    return project(name);
  }

  @Operator
  public Predicate<ChangeData> repositories(String name) {
    return projects(name);
  }

  @Operator
  public Predicate<ChangeData> parentrepository(String name) {
    return parentproject(name);
  }

  @Operator
  public Predicate<ChangeData> repo(String name) {
    return project(name);
  }

  @Operator
  public Predicate<ChangeData> repos(String name) {
    return projects(name);
  }

  @Operator
  public Predicate<ChangeData> parentrepo(String name) {
    return parentproject(name);
  }

  @Operator
  public Predicate<ChangeData> branch(String name) throws QueryParseException {
    if (name.startsWith("^")) {
      return ref("^" + RefNames.fullName(name.substring(1)));
    }
    return ref(RefNames.fullName(name));
  }

  @Operator
  public Predicate<ChangeData> hashtag(String hashtag) {
    return ChangePredicates.hashtag(hashtag);
  }

  @Operator
  public Predicate<ChangeData> inhashtag(String hashtag) throws QueryParseException {
    if (hashtag.startsWith("^")) {
      return new RegexHashtagPredicate(hashtag);
    }
    if (hashtag.isEmpty()) {
      return ChangePredicates.hashtag(hashtag);
    }

    checkFieldAvailable(ChangeField.FUZZY_HASHTAG, "inhashtag");
    return ChangePredicates.fuzzyHashtag(hashtag);
  }

  @Operator
  public Predicate<ChangeData> prefixhashtag(String hashtag) throws QueryParseException {
    if (hashtag.isEmpty()) {
      return ChangePredicates.hashtag(hashtag);
    }

    checkFieldAvailable(ChangeField.PREFIX_HASHTAG, "prefixhashtag");
    return ChangePredicates.prefixHashtag(hashtag);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    return ChangePredicates.exactTopic(name);
  }

  @Operator
  public Predicate<ChangeData> intopic(String name) {
    if (name.startsWith("^")) {
      return new RegexTopicPredicate(name);
    }
    if (name.isEmpty()) {
      return ChangePredicates.exactTopic(name);
    }
    return ChangePredicates.fuzzyTopic(name);
  }

  @Operator
  public Predicate<ChangeData> prefixtopic(String name) throws QueryParseException {
    if (name.isEmpty()) {
      return ChangePredicates.exactTopic(name);
    }

    checkFieldAvailable(ChangeField.PREFIX_TOPIC, "prefixtopic");
    return ChangePredicates.prefixTopic(name);
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) throws QueryParseException {
    if (ref.startsWith("^")) {
      return new RegexRefPredicate(ref);
    }
    return ChangePredicates.ref(ref);
  }

  @Operator
  public Predicate<ChangeData> f(String file) throws QueryParseException {
    return file(file);
  }

  /**
   * Creates a predicate to match changes by file.
   *
   * @param file the value of the {@code file} query operator
   * @throws QueryParseException thrown if parsing the value fails (may be thrown by subclasses)
   */
  @Operator
  public Predicate<ChangeData> file(String file) throws QueryParseException {
    if (file.startsWith("^")) {
      return new RegexPathPredicate(file);
    }
    return ChangePredicates.file(args, file);
  }

  @Operator
  public Predicate<ChangeData> path(String path) {
    if (path.startsWith("^")) {
      return new RegexPathPredicate(path);
    }
    return ChangePredicates.path(path);
  }

  @Operator
  public Predicate<ChangeData> ext(String ext) {
    return extension(ext);
  }

  @Operator
  public Predicate<ChangeData> extension(String ext) {
    return new FileExtensionPredicate(ext);
  }

  @Operator
  public Predicate<ChangeData> onlyexts(String extList) {
    return onlyextensions(extList);
  }

  @Operator
  public Predicate<ChangeData> onlyextensions(String extList) {
    return new FileExtensionListPredicate(extList);
  }

  @Operator
  public Predicate<ChangeData> footer(String footer) {
    return ChangePredicates.footer(footer);
  }

  @Operator
  public Predicate<ChangeData> hasfooter(String footerName) throws QueryParseException {
    checkFieldAvailable(ChangeField.FOOTER_NAME, "hasfooter");
    return ChangePredicates.hasFooter(footerName);
  }

  @Operator
  public Predicate<ChangeData> dir(String directory) {
    return directory(directory);
  }

  @Operator
  public Predicate<ChangeData> directory(String directory) {
    if (directory.startsWith("^")) {
      return new RegexDirectoryPredicate(directory);
    }
    return ChangePredicates.directory(directory);
  }

  @Operator
  public Predicate<ChangeData> label(String name)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> accounts = null;
    AccountGroup.UUID group = null;
    Integer count = null;
    PredicateArgs.Operator countOp = null;

    // Parse for:
    // label:Code-Review=1,user=jsmith or
    // label:Code-Review=1,jsmith or
    // label:Code-Review=1,group=android_approvers or
    // label:Code-Review=1,android_approvers
    // user/groups without a label will first attempt to match user
    // Special case: votes by owners can be tracked with ",owner":
    // label:Code-Review+2,owner
    // label:Code-Review+2,user=owner
    // label:Code-Review+1,count=2
    List<String> splitReviewer = LABEL_SPLITTER.limit(2).splitToList(name);
    name = splitReviewer.get(0); // remove all but the vote piece, e.g.'CodeReview=1'

    if (splitReviewer.size() == 2) {
      // process the user/group piece
      PredicateArgs lblArgs = new PredicateArgs(splitReviewer.get(1));

      // Disallow using the "count=" arg in conjunction with the "user=" or "group=" args. to avoid
      // unnecessary complexity.
      assertDisjunctive(lblArgs, ARG_COUNT, ARG_ID_USER);
      assertDisjunctive(lblArgs, ARG_COUNT, ARG_ID_GROUP);

      for (Map.Entry<String, ValOp> pair : lblArgs.keyValue.entrySet()) {
        String key = pair.getKey();
        String value = pair.getValue().value();
        PredicateArgs.Operator operator = pair.getValue().operator();
        if (key.equalsIgnoreCase(ARG_ID_USER)) {
          if (value.equals(ARG_ID_OWNER)) {
            accounts = Collections.singleton(OWNER_ACCOUNT_ID);
          } else if (value.equals(ARG_ID_NON_UPLOADER)) {
            accounts = Collections.singleton(NON_UPLOADER_ACCOUNT_ID);
          } else {
            accounts = parseAccount(value);
          }
        } else if (key.equalsIgnoreCase(ARG_ID_GROUP)) {
          group = parseGroup(value).getUUID();
        } else if (key.equalsIgnoreCase(ARG_COUNT)) {
          if (!isInt(value)) {
            throw new QueryParseException("Invalid count argument. Value should be an integer");
          }
          count = Integer.parseInt(value);
          countOp = operator;
          if (count == 0) {
            throw new QueryParseException("Argument count=0 is not allowed.");
          }
          if (count > LabelPredicate.MAX_COUNT) {
            throw new QueryParseException(
                String.format(
                    "count=%d is not allowed. Maximum allowed value for count is %d.",
                    count, LabelPredicate.MAX_COUNT));
          }
        } else {
          throw new QueryParseException("Invalid argument identifier '" + pair.getKey() + "'");
        }
      }

      for (String value : lblArgs.positional) {
        if (accounts != null || group != null) {
          throw new QueryParseException("more than one user/group specified (" + value + ")");
        }
        try {
          if (value.equals(ARG_ID_OWNER)) {
            accounts = Collections.singleton(OWNER_ACCOUNT_ID);
          } else if (value.equals(ARG_ID_NON_UPLOADER)) {
            accounts = Collections.singleton(NON_UPLOADER_ACCOUNT_ID);
          } else {
            accounts = parseAccount(value);
          }
        } catch (QueryParseException qpex) {
          // If it doesn't match an account, see if it matches a group
          // (accounts get precedence)
          try {
            group = parseGroup(value).getUUID();
          } catch (QueryParseException e) {
            throw error("Neither user nor group " + value + " found", e);
          }
        }
      }
    }

    if (group != null) {
      accounts = getMembers(group);
    }

    // If the vote piece looks like Code-Review=NEED with a valid non-numeric
    // submit record status, interpret as a submit record query.
    int eq = name.indexOf('=');
    if (eq > 0) {
      String statusName = name.substring(eq + 1).toUpperCase();
      if (!isInt(statusName) && !MagicLabelValue.tryParse(statusName).isPresent()) {
        SubmitRecord.Label.Status status =
            Enums.getIfPresent(SubmitRecord.Label.Status.class, statusName).orNull();
        if (status == null) {
          throw error("Invalid label status " + statusName + " in " + name);
        }
        return SubmitRecordPredicate.create(name.substring(0, eq), status, accounts);
      }
    }

    return new LabelPredicate(args, name, accounts, group, count, countOp);
  }

  /** Assert that keys {@code k1} and {@code k2} do not exist in {@code labelArgs} together. */
  private void assertDisjunctive(PredicateArgs labelArgs, String k1, String k2)
      throws QueryParseException {
    Map<String, ValOp> keyValArgs = labelArgs.keyValue;
    if (keyValArgs.containsKey(k1) && keyValArgs.containsKey(k2)) {
      throw new QueryParseException(
          String.format(
              "Cannot use the '%s' argument in conjunction with the '%s' argument", k1, k2));
    }
  }

  private static boolean isInt(String s) {
    if (s == null) {
      return false;
    }
    if (s.startsWith("+")) {
      s = s.substring(1);
    }
    return Ints.tryParse(s) != null;
  }

  @Operator
  public Predicate<ChangeData> message(String text) throws QueryParseException {
    if (text.startsWith("^")) {
      checkFieldAvailable(ChangeField.COMMIT_MESSAGE_EXACT, "messageexact");
      return new RegexMessagePredicate(text);
    }
    return ChangePredicates.message(text);
  }

  private Predicate<ChangeData> starredBySelf() throws QueryParseException {
    return ChangePredicates.starBy(
        args.starredChangesUtil, self(), StarredChangesUtil.DEFAULT_LABEL);
  }

  private Predicate<ChangeData> draftBySelf() throws QueryParseException {
    return ChangePredicates.draftBy(args.commentsUtil, self());
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    if (isSelf(who)) {
      return isVisible();
    }
    Set<Account.Id> accounts = null;
    try {
      accounts = parseAccount(who);
    } catch (QueryParseException e) {
      if (e instanceof QueryRequiresAuthException) {
        throw e;
      }
    }
    if (accounts != null) {
      if (accounts.size() == 1) {
        return visibleto(args.userFactory.create(Iterables.getOnlyElement(accounts)));
      } else if (accounts.size() > 1) {
        throw error(String.format("\"%s\" resolves to multiple accounts", who));
      }
    }

    // If its not an account, maybe its a group?
    Collection<GroupReference> suggestions = args.groupBackend.suggest(who, null);
    if (!suggestions.isEmpty()) {
      HashSet<AccountGroup.UUID> ids = new HashSet<>();
      for (GroupReference ref : suggestions) {
        ids.add(ref.getUUID());
      }
      return visibleto(new GroupBackedUser(ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<ChangeData> visibleto(CurrentUser user) {
    return args.changeIsVisbleToPredicateFactory.forUser(user);
  }

  public Predicate<ChangeData> isVisible() throws QueryParseException {
    return visibleto(args.getUser());
  }

  @Operator
  public Predicate<ChangeData> o(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return owner(who);
  }

  @Operator
  public Predicate<ChangeData> owner(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return owner(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> owner(Set<Account.Id> who) {
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(ChangePredicates.owner(id));
    }
    return Predicate.or(p);
  }

  private Predicate<ChangeData> ownerDefaultField(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> accounts = parseAccount(who);
    if (accounts.size() > MAX_ACCOUNTS_PER_DEFAULT_FIELD) {
      return Predicate.any();
    }
    return owner(accounts);
  }

  @Operator
  public Predicate<ChangeData> uploader(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    checkFieldAvailable(ChangeField.UPLOADER_SPEC, "uploader");
    return uploader(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> uploader(Set<Account.Id> who) {
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(ChangePredicates.uploader(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> attention(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    checkFieldAvailable(ChangeField.ATTENTION_SET_USERS, "attention");
    return attention(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> attention(Set<Account.Id> who) {
    return Predicate.or(who.stream().map(ChangePredicates::attentionSet).collect(toImmutableSet()));
  }

  @Operator
  public Predicate<ChangeData> assignee(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return assignee(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> assignee(Set<Account.Id> who) {
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(ChangePredicates.assignee(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> ownerin(String group) throws QueryParseException, IOException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }

    AccountGroup.UUID groupId = g.getUUID();
    GroupDescription.Basic groupDescription = args.groupBackend.get(groupId);
    if (!(groupDescription instanceof GroupDescription.Internal)) {
      return new OwnerinPredicate(args.userFactory, groupId);
    }

    Set<Account.Id> accounts = getMembers(groupId);
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(accounts.size());
    for (Account.Id id : accounts) {
      p.add(ChangePredicates.owner(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> uploaderin(String group) throws QueryParseException, IOException {
    checkFieldAvailable(ChangeField.UPLOADER_SPEC, "uploaderin");

    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }

    AccountGroup.UUID groupId = g.getUUID();
    GroupDescription.Basic groupDescription = args.groupBackend.get(groupId);
    if (!(groupDescription instanceof GroupDescription.Internal)) {
      return new UploaderinPredicate(args.userFactory, groupId);
    }

    Set<Account.Id> accounts = getMembers(groupId);
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(accounts.size());
    for (Account.Id id : accounts) {
      p.add(ChangePredicates.uploader(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> r(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return reviewer(who);
  }

  @Operator
  public Predicate<ChangeData> reviewer(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return reviewer(who, false);
  }

  private Predicate<ChangeData> reviewerDefaultField(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return reviewer(who, true);
  }

  private Predicate<ChangeData> reviewer(String who, boolean forDefaultField)
      throws QueryParseException, IOException, ConfigInvalidException {
    Predicate<ChangeData> byState =
        reviewerByState(who, ReviewerStateInternal.REVIEWER, forDefaultField);
    if (Objects.equals(byState, Predicate.<ChangeData>any())) {
      return Predicate.any();
    }
    return Predicate.and(Predicate.not(new BooleanPredicate(ChangeField.WIP)), byState);
  }

  @Operator
  public Predicate<ChangeData> cc(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return reviewerByState(who, ReviewerStateInternal.CC, false);
  }

  @Operator
  public Predicate<ChangeData> reviewerin(String group) throws QueryParseException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return new ReviewerinPredicate(args.userFactory, g.getUUID());
  }

  @Operator
  public Predicate<ChangeData> tr(String trackingId) {
    return ChangePredicates.trackingId(trackingId);
  }

  @Operator
  public Predicate<ChangeData> bug(String trackingId) {
    return tr(trackingId);
  }

  @Operator
  public Predicate<ChangeData> limit(String query) throws QueryParseException {
    Integer limit = Ints.tryParse(query);
    if (limit == null) {
      throw error("Invalid limit: " + query);
    }
    return new LimitPredicate<>(FIELD_LIMIT, limit);
  }

  @Operator
  public Predicate<ChangeData> added(String value) throws QueryParseException {
    return new AddedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> deleted(String value) throws QueryParseException {
    return new DeletedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> size(String value) throws QueryParseException {
    return delta(value);
  }

  @Operator
  public Predicate<ChangeData> delta(String value) throws QueryParseException {
    return new DeltaPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> commentby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return commentby(parseAccount(who));
  }

  private Predicate<ChangeData> commentby(Set<Account.Id> who) {
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(ChangePredicates.commentBy(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> from(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> ownerIds = parseAccount(who);
    return Predicate.or(owner(ownerIds), commentby(ownerIds));
  }

  @Operator
  public Predicate<ChangeData> query(String value) throws QueryParseException {
    // [name=]<name>[,user=<user>] || [user=<user>,][name=]<name>
    PredicateArgs inputArgs = new PredicateArgs(value);
    String name = null;
    Account.Id account = null;

    try (Repository git = args.repoManager.openRepository(args.allUsersName)) {
      // [name=]<name>
      if (inputArgs.keyValue.containsKey(ARG_ID_NAME)) {
        name = inputArgs.keyValue.get(ARG_ID_NAME).value();
      } else if (inputArgs.positional.size() == 1) {
        name = Iterables.getOnlyElement(inputArgs.positional);
      } else if (inputArgs.positional.size() > 1) {
        throw new QueryParseException("Error parsing named query: " + value);
      }

      // [,user=<user>]
      if (inputArgs.keyValue.containsKey(ARG_ID_USER)) {
        Set<Account.Id> accounts = parseAccount(inputArgs.keyValue.get(ARG_ID_USER).value());
        if (accounts != null && accounts.size() > 1) {
          throw error(
              String.format(
                  "\"%s\" resolves to multiple accounts", inputArgs.keyValue.get(ARG_ID_USER)));
        }
        account = (accounts == null ? self() : Iterables.getOnlyElement(accounts));
      } else {
        account = self();
      }

      VersionedAccountQueries q = VersionedAccountQueries.forUser(account);
      q.load(args.allUsersName, git);
      String query = q.getQueryList().getQuery(name);
      if (query != null) {
        return parse(query);
      }
    } catch (RepositoryNotFoundException e) {
      throw new QueryParseException(
          "Unknown named query (no " + args.allUsersName + " repo): " + name, e);
    } catch (IOException | ConfigInvalidException e) {
      throw new QueryParseException("Error parsing named query: " + value, e);
    }
    throw new QueryParseException("Unknown named query: " + name);
  }

  @Operator
  public Predicate<ChangeData> reviewedby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return ChangePredicates.reviewedBy(parseAccount(who));
  }

  @Operator
  public Predicate<ChangeData> destination(String value) throws QueryParseException {
    // [name=]<name>[,user=<user>] || [user=<user>,][name=]<name>
    PredicateArgs inputArgs = new PredicateArgs(value);
    String name = null;
    Account.Id account = null;

    try (Repository git = args.repoManager.openRepository(args.allUsersName)) {
      // [name=]<name>
      if (inputArgs.keyValue.containsKey(ARG_ID_NAME)) {
        name = inputArgs.keyValue.get(ARG_ID_NAME).value();
      } else if (inputArgs.positional.size() == 1) {
        name = Iterables.getOnlyElement(inputArgs.positional);
      } else if (inputArgs.positional.size() > 1) {
        throw new QueryParseException("Error parsing named destination: " + value);
      }

      // [,user=<user>]
      if (inputArgs.keyValue.containsKey(ARG_ID_USER)) {
        Set<Account.Id> accounts = parseAccount(inputArgs.keyValue.get(ARG_ID_USER).value());
        if (accounts != null && accounts.size() > 1) {
          throw error(
              String.format(
                  "\"%s\" resolves to multiple accounts", inputArgs.keyValue.get(ARG_ID_USER)));
        }
        account = (accounts == null ? self() : Iterables.getOnlyElement(accounts));
      } else {
        account = self();
      }

      Set<BranchNameKey> destinations = getDestinationList(git, account).getDestinations(name);
      if (destinations != null && !destinations.isEmpty()) {
        return new DestinationPredicate(destinations, value);
      }
    } catch (RepositoryNotFoundException e) {
      throw new QueryParseException(
          "Unknown named destination (no " + args.allUsersName + " repo): " + name, e);
    } catch (IOException | ConfigInvalidException e) {
      throw new QueryParseException("Error parsing named destination: " + value, e);
    }
    throw new QueryParseException("Unknown named destination: " + name);
  }

  protected DestinationList getDestinationList(Repository git, Account.Id account)
      throws ConfigInvalidException, RepositoryNotFoundException, IOException {
    DestinationList dl = destinationListByAccount.get(account);
    if (dl == null) {
      dl = loadDestinationList(git, account);
      destinationListByAccount.put(account, dl);
    }
    return dl;
  }

  protected DestinationList loadDestinationList(Repository git, Account.Id account)
      throws ConfigInvalidException, RepositoryNotFoundException, IOException {
    VersionedAccountDestinations d = VersionedAccountDestinations.forUser(account);
    d.load(args.allUsersName, git);
    return d.getDestinationList();
  }

  @Operator
  public Predicate<ChangeData> author(String who) throws QueryParseException {
    return getAuthorOrCommitterPredicate(
        who.trim(), ChangePredicates::exactAuthor, ChangePredicates::author);
  }

  @Operator
  public Predicate<ChangeData> committer(String who) throws QueryParseException {
    return getAuthorOrCommitterPredicate(
        who.trim(), ChangePredicates::exactCommitter, ChangePredicates::committer);
  }

  @Operator
  public Predicate<ChangeData> unresolved(String value) throws QueryParseException {
    return new IsUnresolvedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> revertof(String value) throws QueryParseException {
    if (value == null || Ints.tryParse(value) == null) {
      throw new QueryParseException("'revertof' must be an integer");
    }
    return ChangePredicates.revertOf(Change.id(Ints.tryParse(value)));
  }

  @Operator
  public Predicate<ChangeData> submissionId(String value) {
    return ChangePredicates.submissionId(value);
  }

  @Operator
  public Predicate<ChangeData> cherryPickOf(String value) throws QueryParseException {
    checkFieldAvailable(ChangeField.CHERRY_PICK_OF_CHANGE, "cherryPickOf");
    checkFieldAvailable(ChangeField.CHERRY_PICK_OF_PATCHSET, "cherryPickOf");
    if (Ints.tryParse(value) != null) {
      return ChangePredicates.cherryPickOf(Change.id(Ints.tryParse(value)));
    }
    try {
      PatchSet.Id patchSetId = PatchSet.Id.parse(value);
      return ChangePredicates.cherryPickOf(patchSetId);
    } catch (IllegalArgumentException e) {
      throw new QueryParseException(
          "'"
              + value
              + "' is not a valid input. It must be in the 'ChangeNumber[,PatchsetNumber]' format.",
          e);
    }
  }

  @Override
  protected Predicate<ChangeData> defaultField(String query) throws QueryParseException {
    if (query.startsWith("refs/")) {
      return ref(query);
    } else if (DEF_CHANGE.matcher(query).matches()) {
      List<Predicate<ChangeData>> predicates = Lists.newArrayListWithCapacity(2);
      try {
        predicates.add(change(query));
      } catch (QueryParseException e) {
        // Skip.
      }

      // For PAT_LEGACY_ID, it may also be the prefix of some commits.
      if (query.length() >= 6 && PAT_LEGACY_ID.matcher(query).matches()) {
        predicates.add(commit(query));
      }

      return Predicate.or(predicates);
    }

    // Adapt the capacity of this list when adding more default predicates.
    List<Predicate<ChangeData>> predicates = Lists.newArrayListWithCapacity(11);
    try {
      Predicate<ChangeData> p = ownerDefaultField(query);
      if (!Objects.equals(p, Predicate.<ChangeData>any())) {
        predicates.add(p);
      }
    } catch (StorageException | IOException | ConfigInvalidException | QueryParseException e) {
      // Skip.
    }
    try {
      Predicate<ChangeData> p = reviewerDefaultField(query);
      if (!Objects.equals(p, Predicate.<ChangeData>any())) {
        predicates.add(p);
      }
    } catch (StorageException | IOException | ConfigInvalidException | QueryParseException e) {
      // Skip.
    }
    predicates.add(file(query));
    try {
      predicates.add(label(query));
    } catch (StorageException | IOException | ConfigInvalidException | QueryParseException e) {
      // Skip.
    }
    predicates.add(commit(query));
    predicates.add(message(query));
    predicates.add(comment(query));
    predicates.add(projects(query));
    predicates.add(ref(query));
    predicates.add(branch(query));
    predicates.add(topic(query));
    // Adapt the capacity of the "predicates" list when adding more default
    // predicates.
    return Predicate.or(predicates);
  }

  protected void checkFieldAvailable(SchemaField<ChangeData, ?> field, String operator)
      throws QueryParseException {
    if (!args.index.getSchema().hasField(field)) {
      throw new QueryParseException(
          String.format("'%s' operator is not supported by change index version", operator));
    }
  }

  private Predicate<ChangeData> getAuthorOrCommitterPredicate(
      String who,
      Function<String, Predicate<ChangeData>> exactPredicateFunc,
      Function<String, Predicate<ChangeData>> fullPredicateFunc)
      throws QueryParseException {
    if (Address.tryParse(who) != null) {
      return exactPredicateFunc.apply(who);
    }
    return getAuthorOrCommitterFullTextPredicate(who, fullPredicateFunc);
  }

  private Predicate<ChangeData> getAuthorOrCommitterFullTextPredicate(
      String who, Function<String, Predicate<ChangeData>> fullPredicateFunc)
      throws QueryParseException {
    if (isSelf(who)) {
      IdentifiedUser me = args.getIdentifiedUser();
      List<Predicate<ChangeData>> predicates =
          me.getEmailAddresses().stream().map(fullPredicateFunc).collect(toList());
      return Predicate.or(predicates);
    }
    Set<String> parts = SchemaUtil.getNameParts(who);
    if (parts.isEmpty()) {
      throw error("invalid value");
    }

    List<Predicate<ChangeData>> predicates =
        parts.stream().map(fullPredicateFunc).collect(toList());
    return Predicate.and(predicates);
  }

  private Set<Account.Id> getMembers(AccountGroup.UUID g) throws IOException {
    Set<Account.Id> accounts;
    Set<Account.Id> allMembers =
        args.groupMembers.listAccounts(g).stream().map(Account::id).collect(toSet());
    int maxTerms = args.indexConfig.maxTerms();
    if (allMembers.size() > maxTerms) {
      // limit the number of query terms otherwise Gerrit will barf
      accounts = allMembers.stream().limit(maxTerms).collect(toSet());
    } else {
      accounts = allMembers;
    }
    return accounts;
  }

  private Set<Account.Id> parseAccount(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    try {
      return args.accountResolver.resolve(who).asNonEmptyIdSet();
    } catch (UnresolvableAccountException e) {
      if (e.isSelf()) {
        throw new QueryRequiresAuthException(e.getMessage(), e);
      }
      throw new QueryParseException(e.getMessage(), e);
    }
  }

  private Set<Account.Id> parseAccount(
      String who, java.util.function.Predicate<AccountState> activityFilter)
      throws QueryParseException, IOException, ConfigInvalidException {
    try {
      return args.accountResolver.resolve(who, activityFilter).asNonEmptyIdSet();
    } catch (UnresolvableAccountException e) {
      if (e.isSelf()) {
        throw new QueryRequiresAuthException(e.getMessage(), e);
      }
      throw new QueryParseException(e.getMessage(), e);
    }
  }

  private GroupReference parseGroup(String group) throws QueryParseException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return g;
  }

  private List<Change> parseChange(String value) throws QueryParseException {
    return asChanges(parseChangeData(value));
  }

  private List<ChangeData> parseChangeData(String value) throws QueryParseException {
    if (PAT_LEGACY_ID.matcher(value).matches()) {
      Optional<Change.Id> id = Change.Id.tryParse(value);
      if (!id.isPresent()) {
        throw error("Invalid change id " + value);
      }
      return args.queryProvider.get().byLegacyChangeId(id.get());
    } else if (PAT_CHANGE_ID.matcher(value).matches()) {
      List<ChangeData> changes = args.queryProvider.get().byKeyPrefix(parseChangeId(value));
      if (changes.isEmpty()) {
        throw error("Change " + value + " not found");
      }
      return changes;
    }

    throw error("Change " + value + " not found");
  }

  private static String parseChangeId(String value) {
    if (value.charAt(0) == 'i') {
      value = "I" + value.substring(1);
    }
    return value;
  }

  private Account.Id self() throws QueryParseException {
    return args.getIdentifiedUser().getAccountId();
  }

  public Predicate<ChangeData> reviewerByState(
      String who, ReviewerStateInternal state, boolean forDefaultField)
      throws QueryParseException, IOException, ConfigInvalidException {
    Predicate<ChangeData> reviewerByEmailPredicate = null;
    Address address = Address.tryParse(who);
    if (address != null) {
      reviewerByEmailPredicate = ReviewerByEmailPredicate.forState(address, state);
    }

    Predicate<ChangeData> reviewerPredicate = null;
    try {
      Set<Account.Id> accounts = parseAccount(who);
      if (!forDefaultField || accounts.size() <= MAX_ACCOUNTS_PER_DEFAULT_FIELD) {
        reviewerPredicate =
            Predicate.or(
                accounts.stream()
                    .map(id -> ReviewerPredicate.forState(id, state))
                    .collect(toList()));
      } else {
        logger.atFine().log(
            "Skipping reviewer predicate for %s in default field query"
                + " because the number of matched accounts (%d) exceeds the limit of %d",
            who, accounts.size(), MAX_ACCOUNTS_PER_DEFAULT_FIELD);
      }
    } catch (QueryParseException e) {
      logger.atFine().log("Parsing %s as account failed: %s", who, e.getMessage());
      // Propagate this exception only if we can't use 'who' to query by email
      if (reviewerByEmailPredicate == null) {
        throw e;
      }
    }

    if (reviewerPredicate != null && reviewerByEmailPredicate != null) {
      return Predicate.or(reviewerPredicate, reviewerByEmailPredicate);
    } else if (reviewerPredicate != null) {
      return reviewerPredicate;
    } else if (reviewerByEmailPredicate != null) {
      return reviewerByEmailPredicate;
    } else {
      return Predicate.any();
    }
  }
}
