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
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.exceptions.NotSignedInException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
  private interface ChangeOperandFactory {
    Predicate<ChangeData> create(ChangeQueryBuilder builder) throws QueryParseException;
  }

  public interface ChangeHasOperandFactory extends ChangeOperandFactory {}

  private static final Pattern PAT_LEGACY_ID = Pattern.compile("^[1-9][0-9]*$");
  private static final Pattern PAT_CHANGE_ID = Pattern.compile(CHANGE_ID_PATTERN);
  private static final Pattern DEF_CHANGE =
      Pattern.compile("^(?:[1-9][0-9]*|(?:[^~]+~[^~]+~)?[iI][0-9a-f]{4,}.*)$");

  static final int MAX_ACCOUNTS_PER_DEFAULT_FIELD = 10;

  // NOTE: As new search operations are added, please keep the suggestions in
  // gr-search-bar.js up to date.

  public static final String FIELD_ADDED = "added";
  public static final String FIELD_AGE = "age";
  public static final String FIELD_ATTENTION_SET_USERS = "attentionusers";
  public static final String FIELD_ATTENTION_SET_FULL = "attentionfull";
  public static final String FIELD_ASSIGNEE = "assignee";
  public static final String FIELD_AUTHOR = "author";
  public static final String FIELD_EXACTAUTHOR = "exactauthor";
  public static final String FIELD_BEFORE = "before";
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
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_OWNERIN = "ownerin";
  public static final String FIELD_PARENTPROJECT = "parentproject";
  public static final String FIELD_PATH = "path";
  public static final String FIELD_PENDING_REVIEWER = "pendingreviewer";
  public static final String FIELD_PENDING_REVIEWER_BY_EMAIL = "pendingreviewerbyemail";
  public static final String FIELD_PRIVATE = "private";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_PROJECTS = "projects";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWEDBY = "reviewedby";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_REVIEWERIN = "reviewerin";
  public static final String FIELD_STAR = "star";
  public static final String FIELD_STARBY = "starby";
  public static final String FIELD_STARREDBY = "starredby";
  public static final String FIELD_STARTED = "started";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_SUBMISSIONID = "submissionid";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_UNRESOLVED_COMMENT_COUNT = "unresolved";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";
  public static final String FIELD_WIP = "wip";
  public static final String FIELD_REVERTOF = "revertof";
  public static final String FIELD_CHERRY_PICK_OF = "cherrypickof";
  public static final String FIELD_CHERRY_PICK_OF_CHANGE = "cherrypickofchange";
  public static final String FIELD_CHERRY_PICK_OF_PATCHSET = "cherrypickofpatchset";

  public static final String ARG_ID_USER = "user";
  public static final String ARG_ID_GROUP = "group";
  public static final String ARG_ID_OWNER = "owner";
  public static final Account.Id OWNER_ACCOUNT_ID = Account.id(0);

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
    final HasOperandAliasConfig hasOperandAliasConfig;

    private final Provider<CurrentUser> self;

    @Inject
    @VisibleForTesting
    public Arguments(
        Provider<InternalChangeQuery> queryProvider,
        ChangeIndexRewriter rewriter,
        DynamicMap<ChangeOperatorFactory> opFactories,
        DynamicMap<ChangeHasOperandFactory> hasOperands,
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
        HasOperandAliasConfig hasOperandAliasConfig,
        ChangeIsVisibleToPredicate.Factory changeIsVisbleToPredicateFactory) {
      this(
          queryProvider,
          rewriter,
          opFactories,
          hasOperands,
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
          hasOperandAliasConfig,
          changeIsVisbleToPredicateFactory);
    }

    private Arguments(
        Provider<InternalChangeQuery> queryProvider,
        ChangeIndexRewriter rewriter,
        DynamicMap<ChangeOperatorFactory> opFactories,
        DynamicMap<ChangeHasOperandFactory> hasOperands,
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
        HasOperandAliasConfig hasOperandAliasConfig,
        ChangeIsVisibleToPredicate.Factory changeIsVisbleToPredicateFactory) {
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
      this.groupMembers = groupMembers;
      this.changeIsVisbleToPredicateFactory = changeIsVisbleToPredicateFactory;
      this.operatorAliasConfig = operatorAliasConfig;
      this.indexMergeable = indexMergeable;
      this.hasOperandAliasConfig = hasOperandAliasConfig;
    }

    Arguments asUser(CurrentUser otherUser) {
      return new Arguments(
          queryProvider,
          rewriter,
          opFactories,
          hasOperands,
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
          hasOperandAliasConfig,
          changeIsVisbleToPredicateFactory);
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

    Schema<ChangeData> getSchema() {
      return index != null ? index.getSchema() : null;
    }
  }

  private final Arguments args;
  protected Map<String, String> hasOperandAliases = Collections.emptyMap();

  @Inject
  ChangeQueryBuilder(Arguments args) {
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

  public Arguments getArgs() {
    return args;
  }

  public ChangeQueryBuilder asUser(CurrentUser user) {
    return new ChangeQueryBuilder(builderDef, args.asUser(user));
  }

  @Operator
  public Predicate<ChangeData> age(String value) {
    return new AgePredicate(value);
  }

  @Operator
  public Predicate<ChangeData> before(String value) throws QueryParseException {
    return new BeforePredicate(value);
  }

  @Operator
  public Predicate<ChangeData> until(String value) throws QueryParseException {
    return before(value);
  }

  @Operator
  public Predicate<ChangeData> after(String value) throws QueryParseException {
    return new AfterPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> since(String value) throws QueryParseException {
    return after(value);
  }

  @Operator
  public Predicate<ChangeData> change(String query) throws QueryParseException {
    Optional<ChangeTriplet> triplet = ChangeTriplet.parse(query);
    if (triplet.isPresent()) {
      return Predicate.and(
          project(triplet.get().project().get()),
          branch(triplet.get().branch().branch()),
          new ChangeIdPredicate(parseChangeId(triplet.get().id().get())));
    }
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      Integer id = Ints.tryParse(query);
      if (id != null) {
        return args.getSchema().useLegacyNumericFields()
            ? new LegacyChangeIdPredicate(Change.id(id))
            : new LegacyChangeIdStrPredicate(Change.id(id));
      }
    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      return new ChangeIdPredicate(parseChangeId(query));
    }

    throw new QueryParseException("Invalid change format");
  }

  @Operator
  public Predicate<ChangeData> comment(String value) {
    return new CommentPredicate(args.index, value);
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("reviewed".equalsIgnoreCase(statusName)) {
      return IsReviewedPredicate.create();
    }
    return ChangeStatusPredicate.parse(statusName);
  }

  public Predicate<ChangeData> statusOpen() {
    return ChangeStatusPredicate.open();
  }

  @Operator
  public Predicate<ChangeData> has(String value) throws QueryParseException {
    value = hasOperandAliases.getOrDefault(value, value);
    if ("star".equalsIgnoreCase(value)) {
      return starredby(self());
    }

    if ("stars".equalsIgnoreCase(value)) {
      return new HasStarsPredicate(self());
    }

    if ("draft".equalsIgnoreCase(value)) {
      return draftby(self());
    }

    if ("edit".equalsIgnoreCase(value)) {
      return new EditByPredicate(self());
    }

    if ("unresolved".equalsIgnoreCase(value)) {
      return new IsUnresolvedPredicate();
    }

    // for plugins the value will be operandName_pluginName
    List<String> names = Lists.newArrayList(Splitter.on('_').split(value));
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
      return starredby(self());
    }

    if ("watched".equalsIgnoreCase(value)) {
      return new IsWatchedByPredicate(args, false);
    }

    if ("visible".equalsIgnoreCase(value)) {
      return isVisible();
    }

    if ("reviewed".equalsIgnoreCase(value)) {
      return IsReviewedPredicate.create();
    }

    if ("owner".equalsIgnoreCase(value)) {
      return new OwnerPredicate(self());
    }

    if ("reviewer".equalsIgnoreCase(value)) {
      if (args.getSchema().hasField(ChangeField.WIP)) {
        return Predicate.and(
            Predicate.not(new BooleanPredicate(ChangeField.WIP)),
            ReviewerPredicate.reviewer(self()));
      }
      return ReviewerPredicate.reviewer(self());
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
      if (args.getSchema().hasField(ChangeField.MERGE)) {
        return new BooleanPredicate(ChangeField.MERGE);
      }
      throw new QueryParseException("'is:merge' operator is not supported by change index version");
    }

    if ("private".equalsIgnoreCase(value)) {
      if (args.getSchema().hasField(ChangeField.PRIVATE)) {
        return new BooleanPredicate(ChangeField.PRIVATE);
      }
      throw new QueryParseException(
          "'is:private' operator is not supported by change index version");
    }

    if ("assigned".equalsIgnoreCase(value)) {
      return Predicate.not(new AssigneePredicate(Account.id(ChangeField.NO_ASSIGNEE)));
    }

    if ("unassigned".equalsIgnoreCase(value)) {
      return new AssigneePredicate(Account.id(ChangeField.NO_ASSIGNEE));
    }

    if ("submittable".equalsIgnoreCase(value)) {
      return new SubmittablePredicate(SubmitRecord.Status.OK);
    }

    if ("ignored".equalsIgnoreCase(value)) {
      return star("ignore");
    }

    if ("started".equalsIgnoreCase(value)) {
      if (args.getSchema().hasField(ChangeField.STARTED)) {
        return new BooleanPredicate(ChangeField.STARTED);
      }
      throw new QueryParseException(
          "'is:started' operator is not supported by change index version");
    }

    if ("wip".equalsIgnoreCase(value)) {
      if (args.getSchema().hasField(ChangeField.WIP)) {
        return new BooleanPredicate(ChangeField.WIP);
      }
      throw new QueryParseException("'is:wip' operator is not supported by change index version");
    }

    return status(value);
  }

  @Operator
  public Predicate<ChangeData> commit(String id) {
    return new CommitPredicate(id);
  }

  @Operator
  public Predicate<ChangeData> conflicts(String value) throws QueryParseException {
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
    return new ProjectPredicate(name);
  }

  @Operator
  public Predicate<ChangeData> projects(String name) {
    return new ProjectPrefixPredicate(name);
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
    return new HashtagPredicate(hashtag);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    return new ExactTopicPredicate(name);
  }

  @Operator
  public Predicate<ChangeData> intopic(String name) {
    if (name.startsWith("^")) {
      return new RegexTopicPredicate(name);
    }
    if (name.isEmpty()) {
      return new ExactTopicPredicate(name);
    }
    return new FuzzyTopicPredicate(name, args.index);
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) throws QueryParseException {
    if (ref.startsWith("^")) {
      return new RegexRefPredicate(ref);
    }
    return new RefPredicate(ref);
  }

  @Operator
  public Predicate<ChangeData> f(String file) {
    return file(file);
  }

  @Operator
  public Predicate<ChangeData> file(String file) {
    if (file.startsWith("^")) {
      return new RegexPathPredicate(file);
    }
    return EqualsFilePredicate.create(args, file);
  }

  @Operator
  public Predicate<ChangeData> path(String path) {
    if (path.startsWith("^")) {
      return new RegexPathPredicate(path);
    }
    return new EqualsPathPredicate(FIELD_PATH, path);
  }

  @Operator
  public Predicate<ChangeData> ext(String ext) throws QueryParseException {
    return extension(ext);
  }

  @Operator
  public Predicate<ChangeData> extension(String ext) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.EXTENSION)) {
      return new FileExtensionPredicate(ext);
    }
    throw new QueryParseException("'extension' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> onlyexts(String extList) throws QueryParseException {
    return onlyextensions(extList);
  }

  @Operator
  public Predicate<ChangeData> onlyextensions(String extList) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.ONLY_EXTENSIONS)) {
      return new FileExtensionListPredicate(extList);
    }
    throw new QueryParseException(
        "'onlyextensions' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> footer(String footer) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.FOOTER)) {
      return new FooterPredicate(footer);
    }
    throw new QueryParseException("'footer' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> dir(String directory) throws QueryParseException {
    return directory(directory);
  }

  @Operator
  public Predicate<ChangeData> directory(String directory) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.DIRECTORY)) {
      if (directory.startsWith("^")) {
        return new RegexDirectoryPredicate(directory);
      }
      return new DirectoryPredicate(directory);
    }
    throw new QueryParseException("'directory' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> label(String name)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> accounts = null;
    AccountGroup.UUID group = null;

    // Parse for:
    // label:Code-Review=1,user=jsmith or
    // label:Code-Review=1,jsmith or
    // label:Code-Review=1,group=android_approvers or
    // label:Code-Review=1,android_approvers
    // user/groups without a label will first attempt to match user
    // Special case: votes by owners can be tracked with ",owner":
    // label:Code-Review+2,owner
    // label:Code-Review+2,user=owner
    List<String> splitReviewer = Lists.newArrayList(Splitter.on(',').limit(2).split(name));
    name = splitReviewer.get(0); // remove all but the vote piece, e.g.'CodeReview=1'

    if (splitReviewer.size() == 2) {
      // process the user/group piece
      PredicateArgs lblArgs = new PredicateArgs(splitReviewer.get(1));

      for (Map.Entry<String, String> pair : lblArgs.keyValue.entrySet()) {
        if (pair.getKey().equalsIgnoreCase(ARG_ID_USER)) {
          if (pair.getValue().equals(ARG_ID_OWNER)) {
            accounts = Collections.singleton(OWNER_ACCOUNT_ID);
          } else {
            accounts = parseAccount(pair.getValue());
          }
        } else if (pair.getKey().equalsIgnoreCase(ARG_ID_GROUP)) {
          group = parseGroup(pair.getValue()).getUUID();
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
    if (args.getSchema().hasField(ChangeField.SUBMIT_RECORD) && eq > 0) {
      String statusName = name.substring(eq + 1).toUpperCase();
      if (!isInt(statusName)) {
        SubmitRecord.Label.Status status =
            Enums.getIfPresent(SubmitRecord.Label.Status.class, statusName).orNull();
        if (status == null) {
          throw error("Invalid label status " + statusName + " in " + name);
        }
        return SubmitRecordPredicate.create(name.substring(0, eq), status, accounts);
      }
    }

    return new LabelPredicate(args, name, accounts, group);
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
  public Predicate<ChangeData> message(String text) {
    return new MessagePredicate(args.index, text);
  }

  @Operator
  public Predicate<ChangeData> star(String label) throws QueryParseException {
    return new StarPredicate(self(), label);
  }

  @Operator
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return starredby(parseAccount(who));
  }

  private Predicate<ChangeData> starredby(Set<Account.Id> who) {
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(starredby(id));
    }
    return Predicate.or(p);
  }

  private Predicate<ChangeData> starredby(Account.Id who) {
    return new StarPredicate(who, StarredChangesUtil.DEFAULT_LABEL);
  }

  @Operator
  public Predicate<ChangeData> watchedby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> m = parseAccount(who);
    List<IsWatchedByPredicate> p = Lists.newArrayListWithCapacity(m.size());

    Account.Id callerId;
    try {
      CurrentUser caller = args.self.get();
      callerId = caller.isIdentifiedUser() ? caller.getAccountId() : null;
    } catch (ProvisionException e) {
      callerId = null;
    }

    for (Account.Id id : m) {
      // Each child IsWatchedByPredicate includes a visibility filter for the
      // corresponding user, to ensure that predicate subtree only returns
      // changes visible to that user. The exception is if one of the users is
      // the caller of this method, in which case visibility is already being
      // checked at the top level.
      p.add(new IsWatchedByPredicate(args.asUser(id), !id.equals(callerId)));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> draftby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    Set<Account.Id> m = parseAccount(who);
    List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(m.size());
    for (Account.Id id : m) {
      p.add(draftby(id));
    }
    return Predicate.or(p);
  }

  private Predicate<ChangeData> draftby(Account.Id who) {
    return new HasDraftByPredicate(who);
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
      return visibleto(new SingleGroupUser(ids));
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
    List<OwnerPredicate> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(new OwnerPredicate(id));
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
  public Predicate<ChangeData> attention(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    if (!args.index.getSchema().hasField(ChangeField.ATTENTION_SET_USERS)) {
      throw new QueryParseException(
          "'attention' operator is not supported by change index version");
    }
    return attention(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> attention(Set<Account.Id> who) {
    return Predicate.or(who.stream().map(AttentionSetPredicate::new).collect(toImmutableSet()));
  }

  @Operator
  public Predicate<ChangeData> assignee(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return assignee(parseAccount(who, (AccountState s) -> true));
  }

  private Predicate<ChangeData> assignee(Set<Account.Id> who) {
    List<AssigneePredicate> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(new AssigneePredicate(id));
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
    List<OwnerPredicate> p = Lists.newArrayListWithCapacity(accounts.size());
    for (Account.Id id : accounts) {
      p.add(new OwnerPredicate(id));
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
    if (args.getSchema().hasField(ChangeField.WIP)) {
      return Predicate.and(Predicate.not(new BooleanPredicate(ChangeField.WIP)), byState);
    }
    return byState;
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
    return new TrackingIdPredicate(trackingId);
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
    List<CommentByPredicate> p = Lists.newArrayListWithCapacity(who.size());
    for (Account.Id id : who) {
      p.add(new CommentByPredicate(id));
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
  public Predicate<ChangeData> query(String name) throws QueryParseException {
    try (Repository git = args.repoManager.openRepository(args.allUsersName)) {
      VersionedAccountQueries q = VersionedAccountQueries.forUser(self());
      q.load(args.allUsersName, git);
      String query = q.getQueryList().getQuery(name);
      if (query != null) {
        return parse(query);
      }
    } catch (RepositoryNotFoundException e) {
      throw new QueryParseException(
          "Unknown named query (no " + args.allUsersName + " repo): " + name, e);
    } catch (IOException | ConfigInvalidException e) {
      throw new QueryParseException("Error parsing named query: " + name, e);
    }
    throw new QueryParseException("Unknown named query: " + name);
  }

  @Operator
  public Predicate<ChangeData> reviewedby(String who)
      throws QueryParseException, IOException, ConfigInvalidException {
    return IsReviewedPredicate.create(parseAccount(who));
  }

  @Operator
  public Predicate<ChangeData> destination(String name) throws QueryParseException {
    try (Repository git = args.repoManager.openRepository(args.allUsersName)) {
      VersionedAccountDestinations d = VersionedAccountDestinations.forUser(self());
      d.load(args.allUsersName, git);
      Set<BranchNameKey> destinations = d.getDestinationList().getDestinations(name);
      if (destinations != null && !destinations.isEmpty()) {
        return new DestinationPredicate(destinations, name);
      }
    } catch (RepositoryNotFoundException e) {
      throw new QueryParseException(
          "Unknown named destination (no " + args.allUsersName + " repo): " + name, e);
    } catch (IOException | ConfigInvalidException e) {
      throw new QueryParseException("Error parsing named destination: " + name, e);
    }
    throw new QueryParseException("Unknown named destination: " + name);
  }

  @Operator
  public Predicate<ChangeData> author(String who) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.EXACT_AUTHOR)) {
      return getAuthorOrCommitterPredicate(
          who.trim(), ExactAuthorPredicate::new, AuthorPredicate::new);
    }
    return getAuthorOrCommitterFullTextPredicate(who.trim(), AuthorPredicate::new);
  }

  @Operator
  public Predicate<ChangeData> committer(String who) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.EXACT_COMMITTER)) {
      return getAuthorOrCommitterPredicate(
          who.trim(), ExactCommitterPredicate::new, CommitterPredicate::new);
    }
    return getAuthorOrCommitterFullTextPredicate(who.trim(), CommitterPredicate::new);
  }

  @Operator
  public Predicate<ChangeData> submittable(String str) throws QueryParseException {
    SubmitRecord.Status status =
        Enums.getIfPresent(SubmitRecord.Status.class, str.toUpperCase()).orNull();
    if (status == null) {
      throw error("invalid value for submittable:" + str);
    }
    return new SubmittablePredicate(status);
  }

  @Operator
  public Predicate<ChangeData> unresolved(String value) throws QueryParseException {
    return new IsUnresolvedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> revertof(String value) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.REVERT_OF)) {
      return new RevertOfPredicate(value);
    }
    throw new QueryParseException("'revertof' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> submissionId(String value) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.SUBMISSIONID)) {
      return new SubmissionIdPredicate(value);
    }
    throw new QueryParseException(
        "'submissionid' operator is not supported by change index version");
  }

  @Operator
  public Predicate<ChangeData> cherryPickOf(String value) throws QueryParseException {
    if (args.getSchema().hasField(ChangeField.CHERRY_PICK_OF_CHANGE)
        && args.getSchema().hasField(ChangeField.CHERRY_PICK_OF_PATCHSET)) {
      if (Ints.tryParse(value) != null) {
        return new CherryPickOfChangePredicate(value);
      }
      try {
        PatchSet.Id patchSetId = PatchSet.Id.parse(value);
        return Predicate.and(
            new CherryPickOfChangePredicate(patchSetId.changeId().toString()),
            new CherryPickOfPatchSetPredicate(patchSetId.getId()));
      } catch (IllegalArgumentException e) {
        throw new QueryParseException(
            "'"
                + value
                + "' is not a valid input. It must be in the 'ChangeNumber[,PatchsetNumber]' format.",
            e);
      }
    }
    throw new QueryParseException(
        "'cherrypickof' operator is not supported by change index version");
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
    if (PAT_LEGACY_ID.matcher(value).matches()) {
      Optional<Change.Id> id = Change.Id.tryParse(value);
      if (!id.isPresent()) {
        throw error("Invalid change id " + value);
      }
      return asChanges(args.queryProvider.get().byLegacyChangeId(id.get()));
    } else if (PAT_CHANGE_ID.matcher(value).matches()) {
      List<Change> changes = asChanges(args.queryProvider.get().byKeyPrefix(parseChangeId(value)));
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
    if (args.index.getSchema().hasField(ChangeField.REVIEWER_BY_EMAIL)) {
      Address address = Address.tryParse(who);
      if (address != null) {
        reviewerByEmailPredicate = ReviewerByEmailPredicate.forState(address, state);
      }
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
