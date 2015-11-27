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

import static com.google.gerrit.server.query.change.ChangeData.asChanges;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.strategy.SubmitStrategyFactory;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.util.Providers;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a query string meant to be applied to change objects.
 */
public class ChangeQueryBuilder extends QueryBuilder<ChangeData> {
  private static final Pattern PAT_LEGACY_ID = Pattern.compile("^[1-9][0-9]*$");
  private static final Pattern PAT_CHANGE_ID =
      Pattern.compile("^[iI][0-9a-f]{4,}.*$");
  private static final Pattern DEF_CHANGE = Pattern.compile(
      "^(?:[1-9][0-9]*|(?:[^~]+~[^~]+~)?[iI][0-9a-f]{4,}.*)$");

  // NOTE: As new search operations are added, please keep the
  // SearchSuggestOracle up to date.

  public static final String FIELD_ADDED = "added";
  public static final String FIELD_AFTER = "after";
  public static final String FIELD_AGE = "age";
  public static final String FIELD_BEFORE = "before";
  public static final String FIELD_BRANCH = "branch";
  public static final String FIELD_CHANGE = "change";
  public static final String FIELD_COMMENT = "comment";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_CONFLICTS = "conflicts";
  public static final String FIELD_DELETED = "deleted";
  public static final String FIELD_DELTA = "delta";
  public static final String FIELD_DRAFTBY = "draftby";
  public static final String FIELD_FILE = "file";
  public static final String FIELD_IS = "is";
  public static final String FIELD_HAS = "has";
  public static final String FIELD_HASHTAG = "hashtag";
  public static final String FIELD_LABEL = "label";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_MERGE = "merge";
  public static final String FIELD_MERGEABLE = "mergeable";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_OWNERIN = "ownerin";
  public static final String FIELD_PARENTPROJECT = "parentproject";
  public static final String FIELD_PATH = "path";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_PROJECTS = "projects";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_REVIEWERIN = "reviewerin";
  public static final String FIELD_STARREDBY = "starredby";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_TOPIC = "topic";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";

  public static final String ARG_ID_USER = "user";
  public static final String ARG_ID_GROUP = "group";


  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ChangeQueryBuilder.class);

  @VisibleForTesting
  public static class Arguments {
    final Provider<ReviewDb> db;
    final Provider<InternalChangeQuery> queryProvider;
    final Provider<ChangeQueryRewriter> rewriter;
    final IdentifiedUser.GenericFactory userFactory;
    final CapabilityControl.Factory capabilityControlFactory;
    final ChangeControl.GenericFactory changeControlGenericFactory;
    final ChangeData.Factory changeDataFactory;
    final FieldDef.FillArgs fillArgs;
    final PatchLineCommentsUtil plcUtil;
    final AccountResolver accountResolver;
    final GroupBackend groupBackend;
    final AllProjectsName allProjectsName;
    final PatchListCache patchListCache;
    final GitRepositoryManager repoManager;
    final ProjectCache projectCache;
    final Provider<ListChildProjects> listChildProjects;
    final IndexCollection indexes;
    final SubmitStrategyFactory submitStrategyFactory;
    final ConflictsCache conflictsCache;
    final TrackingFooters trackingFooters;
    final IndexConfig indexConfig;
    final Provider<ListMembers> listMembers;
    final boolean allowsDrafts;

    private final Provider<CurrentUser> self;

    @Inject
    @VisibleForTesting
    public Arguments(Provider<ReviewDb> db,
        Provider<InternalChangeQuery> queryProvider,
        Provider<ChangeQueryRewriter> rewriter,
        IdentifiedUser.GenericFactory userFactory,
        Provider<CurrentUser> self,
        CapabilityControl.Factory capabilityControlFactory,
        ChangeControl.GenericFactory changeControlGenericFactory,
        ChangeData.Factory changeDataFactory,
        FieldDef.FillArgs fillArgs,
        PatchLineCommentsUtil plcUtil,
        AccountResolver accountResolver,
        GroupBackend groupBackend,
        AllProjectsName allProjectsName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager,
        ProjectCache projectCache,
        Provider<ListChildProjects> listChildProjects,
        IndexCollection indexes,
        SubmitStrategyFactory submitStrategyFactory,
        ConflictsCache conflictsCache,
        TrackingFooters trackingFooters,
        IndexConfig indexConfig,
        Provider<ListMembers> listMembers,
        @GerritServerConfig Config cfg) {
      this(db, queryProvider, rewriter, userFactory, self,
          capabilityControlFactory, changeControlGenericFactory,
          changeDataFactory, fillArgs, plcUtil, accountResolver, groupBackend,
          allProjectsName, patchListCache, repoManager, projectCache,
          listChildProjects, indexes, submitStrategyFactory,
          conflictsCache, trackingFooters, indexConfig, listMembers,
          cfg == null ? true : cfg.getBoolean("change", "allowDrafts", true));
    }

    private Arguments(
        Provider<ReviewDb> db,
        Provider<InternalChangeQuery> queryProvider,
        Provider<ChangeQueryRewriter> rewriter,
        IdentifiedUser.GenericFactory userFactory,
        Provider<CurrentUser> self,
        CapabilityControl.Factory capabilityControlFactory,
        ChangeControl.GenericFactory changeControlGenericFactory,
        ChangeData.Factory changeDataFactory,
        FieldDef.FillArgs fillArgs,
        PatchLineCommentsUtil plcUtil,
        AccountResolver accountResolver,
        GroupBackend groupBackend,
        AllProjectsName allProjectsName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager,
        ProjectCache projectCache,
        Provider<ListChildProjects> listChildProjects,
        IndexCollection indexes,
        SubmitStrategyFactory submitStrategyFactory,
        ConflictsCache conflictsCache,
        TrackingFooters trackingFooters,
        IndexConfig indexConfig,
        Provider<ListMembers> listMembers,
        boolean allowsDrafts) {
     this.db = db;
     this.queryProvider = queryProvider;
     this.rewriter = rewriter;
     this.userFactory = userFactory;
     this.self = self;
     this.capabilityControlFactory = capabilityControlFactory;
     this.changeControlGenericFactory = changeControlGenericFactory;
     this.changeDataFactory = changeDataFactory;
     this.fillArgs = fillArgs;
     this.plcUtil = plcUtil;
     this.accountResolver = accountResolver;
     this.groupBackend = groupBackend;
     this.allProjectsName = allProjectsName;
     this.patchListCache = patchListCache;
     this.repoManager = repoManager;
     this.projectCache = projectCache;
     this.listChildProjects = listChildProjects;
     this.indexes = indexes;
     this.submitStrategyFactory = submitStrategyFactory;
     this.conflictsCache = conflictsCache;
     this.trackingFooters = trackingFooters;
     this.indexConfig = indexConfig;
     this.listMembers = listMembers;
     this.allowsDrafts = allowsDrafts;
    }

    Arguments asUser(CurrentUser otherUser) {
      return new Arguments(db, queryProvider, rewriter, userFactory,
          Providers.of(otherUser),
          capabilityControlFactory, changeControlGenericFactory,
          changeDataFactory, fillArgs, plcUtil, accountResolver, groupBackend,
          allProjectsName, patchListCache, repoManager, projectCache,
          listChildProjects, indexes, submitStrategyFactory, conflictsCache,
          trackingFooters, indexConfig, listMembers, allowsDrafts);
    }

    Arguments asUser(Account.Id otherId) {
      try {
        CurrentUser u = self.get();
        if (u.isIdentifiedUser()
            && otherId.equals(((IdentifiedUser) u).getAccountId())) {
          return this;
        }
      } catch (ProvisionException e) {
        // Doesn't match current user, continue.
      }
      return asUser(userFactory.create(db, otherId));
    }

    IdentifiedUser getIdentifiedUser() throws QueryParseException {
      try {
        CurrentUser u = getCurrentUser();
        if (u.isIdentifiedUser()) {
          return (IdentifiedUser) u;
        }
        throw new QueryParseException(NotSignedInException.MESSAGE);
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }

    CurrentUser getCurrentUser() throws QueryParseException {
      try {
        return self.get();
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }
  }

  private final Arguments args;

  @Inject
  ChangeQueryBuilder(Arguments args) {
    super(mydef);
    this.args = args;
  }

  @VisibleForTesting
  protected ChangeQueryBuilder(
      Definition<ChangeData, ? extends QueryBuilder<ChangeData>> def,
      Arguments args) {
    super(def);
    this.args = args;
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
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      return new LegacyChangeIdPredicate(Change.Id.parse(query));
    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      return new ChangeIdPredicate(parseChangeId(query));
    }
    Optional<ChangeTriplet> triplet = ChangeTriplet.parse(query);
    if (triplet.isPresent()) {
      return Predicate.and(
          project(triplet.get().project().get()),
          branch(triplet.get().branch().get()),
          new ChangeIdPredicate(parseChangeId(triplet.get().id().get())));
    }

    throw new QueryParseException("Invalid change format");
  }

  @Operator
  public Predicate<ChangeData> comment(String value) {
    ChangeIndex index = args.indexes.getSearchIndex();
    return new CommentPredicate(index, value);
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("reviewed".equalsIgnoreCase(statusName)) {
      return new IsReviewedPredicate();
    } else {
      return ChangeStatusPredicate.parse(statusName);
    }
  }

  public Predicate<ChangeData> status_open() {
    return ChangeStatusPredicate.open();
  }

  @Operator
  public Predicate<ChangeData> has(String value) throws QueryParseException {
    if ("star".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(args);
    }

    if ("draft".equalsIgnoreCase(value)) {
      return new HasDraftByPredicate(args, self());
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> is(String value) throws QueryParseException {
    if ("starred".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(args);
    }

    if ("watched".equalsIgnoreCase(value)) {
      return new IsWatchedByPredicate(args, false);
    }

    if ("visible".equalsIgnoreCase(value)) {
      return is_visible();
    }

    if ("reviewed".equalsIgnoreCase(value)) {
      return new IsReviewedPredicate();
    }

    if ("owner".equalsIgnoreCase(value)) {
      return new OwnerPredicate(self());
    }

    if ("reviewer".equalsIgnoreCase(value)) {
      return new ReviewerPredicate(self(), args.allowsDrafts);
    }

    if ("mergeable".equalsIgnoreCase(value)) {
      return new IsMergeablePredicate(schema(args.indexes), args.fillArgs);
    }

    try {
      return status(value);
    } catch (IllegalArgumentException e) {
      // not status: alias?
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> commit(String id) {
    return new CommitPredicate(AbbreviatedObjectId.fromString(id));
  }

  @Operator
  public Predicate<ChangeData> conflicts(String value) throws OrmException,
      QueryParseException {
    return new ConflictsPredicate(args, value, parseChange(value));
  }

  @Operator
  public Predicate<ChangeData> p(String name) {
    return project(name);
  }

  @Operator
  public Predicate<ChangeData> project(String name) {
    if (name.startsWith("^"))
      return new RegexProjectPredicate(name);
    return new ProjectPredicate(name);
  }

  @Operator
  public Predicate<ChangeData> projects(String name) {
    return new ProjectPrefixPredicate(name);
  }

  @Operator
  public Predicate<ChangeData> parentproject(String name) {
    return new ParentProjectPredicate(args.projectCache, args.listChildProjects,
        args.self, name);
  }

  @Operator
  public Predicate<ChangeData> branch(String name) {
    if (name.startsWith("^"))
      return ref("^" + branchToRef(name.substring(1)));
    return ref(branchToRef(name));
  }

  private static String branchToRef(String name) {
    if (!name.startsWith(Branch.R_HEADS))
      return Branch.R_HEADS + name;
    return name;
  }

  @Operator
  public Predicate<ChangeData> hashtag(String hashtag) {
    return new HashtagPredicate(hashtag);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    if (name.startsWith("^"))
      return new RegexTopicPredicate(name);
    return new TopicPredicate(name);
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) {
    if (ref.startsWith("^"))
      return new RegexRefPredicate(ref);
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
    } else {
      return EqualsFilePredicate.create(args, file);
    }
  }

  @Operator
  public Predicate<ChangeData> path(String path) {
    if (path.startsWith("^")) {
      return new RegexPathPredicate(path);
    } else {
      return new EqualsPathPredicate(FIELD_PATH, path);
    }
  }

  @Operator
  public Predicate<ChangeData> label(String name) throws QueryParseException,
      OrmException {
    Set<Account.Id> accounts = null;
    AccountGroup.UUID group = null;

    // Parse for:
    // label:CodeReview=1,user=jsmith or
    // label:CodeReview=1,jsmith or
    // label:CodeReview=1,group=android_approvers or
    // label:CodeReview=1,android_approvers
    //  user/groups without a label will first attempt to match user
    String[] splitReviewer = name.split(",", 2);
    name = splitReviewer[0];        // remove all but the vote piece, e.g.'CodeReview=1'

    if (splitReviewer.length == 2) {
      // process the user/group piece
      PredicateArgs lblArgs = new PredicateArgs(splitReviewer[1]);

      for (Map.Entry<String, String> pair : lblArgs.keyValue.entrySet()) {
        if (pair.getKey().equalsIgnoreCase(ARG_ID_USER)) {
          accounts = parseAccount(pair.getValue());
        } else if (pair.getKey().equalsIgnoreCase(ARG_ID_GROUP)) {
          group = parseGroup(pair.getValue()).getUUID();
        } else {
          throw new QueryParseException(
              "Invalid argument identifier '"   + pair.getKey() + "'");
        }
      }

      for (String value : lblArgs.positional) {
       if (accounts != null || group != null) {
          throw new QueryParseException("more than one user/group specified (" +
              value + ")");
        }
        try {
          accounts = parseAccount(value);
        } catch (QueryParseException qpex) {
          // If it doesn't match an account, see if it matches a group
          // (accounts get precedence)
          try {
            group = parseGroup(value).getUUID();
          } catch (QueryParseException e) {
            throw error("Neither user nor group " + value + " found");
          }
        }
      }
    }

    // expand a group predicate into multiple user predicates
    if (group != null) {
      Set<Account.Id> allMembers =
          new HashSet<>(Lists.transform(
              args.listMembers.get().setRecursive(true).apply(group),
              new Function<AccountInfo, Account.Id>() {
                @Override
                public Account.Id apply(AccountInfo accountInfo) {
                  return new Account.Id(accountInfo._accountId);
                }
              }));
      int maxTerms = args.indexConfig.maxLimit();
      if (allMembers.size() > maxTerms) {
        // limit the number of query terms otherwise Gerrit will barf
        accounts = ImmutableSet.copyOf(Iterables.limit(allMembers, maxTerms));
      } else {
        accounts = allMembers;
      }
    }

    return new LabelPredicate(args.projectCache,
        args.changeControlGenericFactory, args.userFactory, args.db,
        name, accounts, group);
  }

  @Operator
  public Predicate<ChangeData> message(String text) {
    ChangeIndex index = args.indexes.getSearchIndex();
    return new MessagePredicate(index, text);
  }

  @Operator
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, OrmException {
    if ("self".equals(who)) {
      return new IsStarredByPredicate(args);
    }
    Set<Account.Id> m = parseAccount(who);
    List<IsStarredByPredicate> p = Lists.newArrayListWithCapacity(m.size());
    for (Account.Id id : m) {
      p.add(new IsStarredByPredicate(args.asUser(id)));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> watchedby(String who)
      throws QueryParseException, OrmException {
    Set<Account.Id> m = parseAccount(who);
    List<IsWatchedByPredicate> p = Lists.newArrayListWithCapacity(m.size());

    Account.Id callerId;
    try {
      CurrentUser caller = args.self.get();
      if (caller.isIdentifiedUser()) {
        callerId = ((IdentifiedUser) caller).getAccountId();
      } else {
        callerId = null;
      }
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
  public Predicate<ChangeData> draftby(String who) throws QueryParseException,
      OrmException {
    Set<Account.Id> m = parseAccount(who);
    List<HasDraftByPredicate> p = Lists.newArrayListWithCapacity(m.size());
    for (Account.Id id : m) {
      p.add(new HasDraftByPredicate(args, id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, OrmException {
    if ("self".equals(who)) {
      return is_visible();
    }
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (!m.isEmpty()) {
      List<Predicate<ChangeData>> p = Lists.newArrayListWithCapacity(m.size());
      for (Account.Id id : m) {
        return visibleto(args.userFactory.create(args.db, id));
      }
      return Predicate.or(p);
    }

    // If its not an account, maybe its a group?
    //
    Collection<GroupReference> suggestions = args.groupBackend.suggest(who, null);
    if (!suggestions.isEmpty()) {
      HashSet<AccountGroup.UUID> ids = new HashSet<>();
      for (GroupReference ref : suggestions) {
        ids.add(ref.getUUID());
      }
      return visibleto(new SingleGroupUser(args.capabilityControlFactory, ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<ChangeData> visibleto(CurrentUser user) {
    return new IsVisibleToPredicate(args.db, //
        args.changeControlGenericFactory, //
        user);
  }

  public Predicate<ChangeData> is_visible() throws QueryParseException {
    return visibleto(args.getCurrentUser());
  }

  @Operator
  public Predicate<ChangeData> o(String who)
      throws QueryParseException, OrmException {
    return owner(who);
  }

  @Operator
  public Predicate<ChangeData> owner(String who) throws QueryParseException,
      OrmException {
    Set<Account.Id> m = parseAccount(who);
    List<OwnerPredicate> p = Lists.newArrayListWithCapacity(m.size());
    for (Account.Id id : m) {
      p.add(new OwnerPredicate(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> ownerin(String group)
      throws QueryParseException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return new OwnerinPredicate(args.db, args.userFactory, g.getUUID());
  }

  @Operator
  public Predicate<ChangeData> r(String who)
      throws QueryParseException, OrmException {
    return reviewer(who);
  }

  @Operator
  public Predicate<ChangeData> reviewer(String who)
      throws QueryParseException, OrmException {
    Set<Account.Id> m = parseAccount(who);
    List<ReviewerPredicate> p = Lists.newArrayListWithCapacity(m.size());
    for (Account.Id id : m) {
      p.add(new ReviewerPredicate(id, args.allowsDrafts));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> reviewerin(String group)
      throws QueryParseException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend, group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return new ReviewerinPredicate(args.db, args.userFactory, g.getUUID());
  }

  @Operator
  public Predicate<ChangeData> tr(String trackingId) {
    return new TrackingIdPredicate(args.trackingFooters, trackingId);
  }

  @Operator
  public Predicate<ChangeData> bug(String trackingId) {
    return tr(trackingId);
  }

  @Operator
  public Predicate<ChangeData> limit(String limit) throws QueryParseException {
    return new LimitPredicate(Integer.parseInt(limit));
  }

  @Operator
  public Predicate<ChangeData> added(String value)
      throws QueryParseException {
    return new AddedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> deleted(String value)
      throws QueryParseException {
    return new DeletedPredicate(value);
  }

  @Operator
  public Predicate<ChangeData> size(String value)
      throws QueryParseException {
    return delta(value);
  }

  @Operator
  public Predicate<ChangeData> delta(String value)
      throws QueryParseException {
    return new DeltaPredicate(value);
  }

  @Override
  protected Predicate<ChangeData> defaultField(String query) throws QueryParseException {
    if (query.startsWith("refs/")) {
      return ref(query);
    } else if (DEF_CHANGE.matcher(query).matches()) {
      try {
        return change(query);
      } catch (QueryParseException e) {
        // Skip.
      }
    }

    List<Predicate<ChangeData>> predicates = Lists.newArrayListWithCapacity(9);
    try {
      predicates.add(commit(query));
    } catch (IllegalArgumentException e) {
      // Skip.
    }
    try {
      predicates.add(owner(query));
    } catch (OrmException | QueryParseException e) {
      // Skip.
    }
    try {
      predicates.add(reviewer(query));
    } catch (OrmException | QueryParseException e) {
      // Skip.
    }
    predicates.add(file(query));
    try {
      predicates.add(label(query));
    } catch (OrmException | QueryParseException e) {
      // Skip.
    }
    predicates.add(message(query));
    predicates.add(comment(query));
    predicates.add(projects(query));
    predicates.add(ref(query));
    predicates.add(branch(query));
    predicates.add(topic(query));
    return Predicate.or(predicates);
  }

  private Set<Account.Id> parseAccount(String who)
      throws QueryParseException, OrmException {
    if ("self".equals(who)) {
      return Collections.singleton(self());
    }
    Set<Account.Id> matches = args.accountResolver.findAll(who);
    if (matches.isEmpty()) {
      throw error("User " + who + " not found");
    }
    return matches;
  }

  private GroupReference parseGroup(String group) throws QueryParseException {
    GroupReference g = GroupBackends.findBestSuggestion(args.groupBackend,
        group);
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return g;
  }

  private List<Change> parseChange(String value) throws OrmException,
      QueryParseException {
    if (PAT_LEGACY_ID.matcher(value).matches()) {
      return Collections.singletonList(args.db.get().changes()
          .get(Change.Id.parse(value)));
    } else if (PAT_CHANGE_ID.matcher(value).matches()) {
      List<Change> changes =
          asChanges(args.queryProvider.get().byKeyPrefix(parseChangeId(value)));
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

  private static Schema<ChangeData> schema(@Nullable IndexCollection indexes) {
    ChangeIndex index = indexes != null ? indexes.getSearchIndex() : null;
    return index != null ? index.getSchema() : null;
  }
}
