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

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a query string meant to be applied to change objects.
 */
public class ChangeQueryBuilder extends QueryBuilder<ChangeData> {
  private static final Pattern PAT_LEGACY_ID = Pattern.compile("^[1-9][0-9]*$");
  private static final Pattern PAT_CHANGE_ID =
      Pattern.compile("^[iI][0-9a-f]{4,}.*$");
  private static final Pattern DEF_CHANGE =
      Pattern.compile("^([1-9][0-9]*|[iI][0-9a-f]{4,}.*)$");

  private static final Pattern PAT_COMMIT =
      Pattern.compile("^([0-9a-fA-F]{4," + RevId.LEN + "})$");
  private static final Pattern PAT_EMAIL =
      Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+");

  private static final Pattern PAT_LABEL =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*((=|>=|<=)[+-]?|[+-])\\d+$");

  public static final String FIELD_AGE = "age";
  public static final String FIELD_BRANCH = "branch";
  public static final String FIELD_CHANGE = "change";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_DRAFTBY = "draftby";
  public static final String FIELD_FILE = "file";
  public static final String FIELD_IS = "is";
  public static final String FIELD_HAS = "has";
  public static final String FIELD_LABEL = "label";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_OWNERIN = "ownerin";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_REVIEWERIN = "reviewerin";
  public static final String FIELD_STARREDBY = "starredby";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_TOPIC = "topic";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> mydef =
      new QueryBuilder.Definition<ChangeData, ChangeQueryBuilder>(
          ChangeQueryBuilder.class);

  static class Arguments {
    final Provider<ReviewDb> dbProvider;
    final Provider<ChangeQueryRewriter> rewriter;
    final IdentifiedUser.GenericFactory userFactory;
    final ChangeControl.Factory changeControlFactory;
    final ChangeControl.GenericFactory changeControlGenericFactory;
    final AccountResolver accountResolver;
    final GroupCache groupCache;
    final AuthConfig authConfig;
    final ApprovalTypes approvalTypes;
    final Project.NameKey wildProjectName;
    final PatchListCache patchListCache;
    final GitRepositoryManager repoManager;

    @Inject
    Arguments(Provider<ReviewDb> dbProvider,
        Provider<ChangeQueryRewriter> rewriter,
        IdentifiedUser.GenericFactory userFactory,
        ChangeControl.Factory changeControlFactory,
        ChangeControl.GenericFactory changeControlGenericFactory,
        AccountResolver accountResolver, GroupCache groupCache,
        AuthConfig authConfig, ApprovalTypes approvalTypes,
        @WildProjectName Project.NameKey wildProjectName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager) {
      this.dbProvider = dbProvider;
      this.rewriter = rewriter;
      this.userFactory = userFactory;
      this.changeControlFactory = changeControlFactory;
      this.changeControlGenericFactory = changeControlGenericFactory;
      this.accountResolver = accountResolver;
      this.groupCache = groupCache;
      this.authConfig = authConfig;
      this.approvalTypes = approvalTypes;
      this.wildProjectName = wildProjectName;
      this.patchListCache = patchListCache;
      this.repoManager = repoManager;
    }
  }

  public interface Factory {
    ChangeQueryBuilder create(CurrentUser user);
  }

  private final Arguments args;
  private final CurrentUser currentUser;
  private boolean allowsFile;

  @Inject
  ChangeQueryBuilder(Arguments args, @Assisted CurrentUser currentUser) {
    super(mydef);
    this.args = args;
    this.currentUser = currentUser;
  }

  public void setAllowFile(boolean on) {
    allowsFile = on;
  }

  @Operator
  public Predicate<ChangeData> age(String value) {
    return new AgePredicate(args.dbProvider, value);
  }

  @Operator
  public Predicate<ChangeData> change(String query) {
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      return new LegacyChangeIdPredicate(args.dbProvider, Change.Id
          .parse(query));

    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      if (query.charAt(0) == 'i') {
        query = "I" + query.substring(1);
      }
      return new ChangeIdPredicate(args.dbProvider, query);
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("open".equals(statusName)) {
      return status_open();

    } else if ("closed".equals(statusName)) {
      return ChangeStatusPredicate.closed(args.dbProvider);

    } else if ("reviewed".equalsIgnoreCase(statusName)) {
      return new IsReviewedPredicate(args.dbProvider);

    } else {
      return new ChangeStatusPredicate(args.dbProvider, statusName);
    }
  }

  public Predicate<ChangeData> status_open() {
    return ChangeStatusPredicate.open(args.dbProvider);
  }

  @Operator
  public Predicate<ChangeData> has(String value) {
    if ("star".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(args.dbProvider, currentUser);
    }

    if ("draft".equalsIgnoreCase(value)) {
      if (currentUser instanceof IdentifiedUser) {
        return new HasDraftByPredicate(args.dbProvider,
            ((IdentifiedUser) currentUser).getAccountId());
      }
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> is(String value) {
    if ("starred".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(args.dbProvider, currentUser);
    }

    if ("watched".equalsIgnoreCase(value)) {
      return new IsWatchedByPredicate(args, currentUser);
    }

    if ("visible".equalsIgnoreCase(value)) {
      return is_visible();
    }

    if ("reviewed".equalsIgnoreCase(value)) {
      return new IsReviewedPredicate(args.dbProvider);
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
    return new CommitPredicate(args.dbProvider, AbbreviatedObjectId
        .fromString(id));
  }

  @Operator
  public Predicate<ChangeData> project(String name) {
    if (name.startsWith("^"))
      return new RegexProjectPredicate(args.dbProvider, name);
    return new ProjectPredicate(args.dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> branch(String name) {
    if (name.startsWith("^"))
      return new RegexBranchPredicate(args.dbProvider, name);
    return new BranchPredicate(args.dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    if (name.startsWith("^"))
      return new RegexTopicPredicate(args.dbProvider, name);
    return new TopicPredicate(args.dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) {
    if (ref.startsWith("^"))
      return new RegexRefPredicate(args.dbProvider, ref);
    return new RefPredicate(args.dbProvider, ref);
  }

  @Operator
  public Predicate<ChangeData> file(String file) throws QueryParseException {
    if (!allowsFile) {
      throw error("operator not permitted here: file:" + file);
    }

    if (file.startsWith("^")) {
      return new RegexFilePredicate(args.dbProvider, args.patchListCache, file);
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> label(String name) {
    return new LabelPredicate(args.changeControlGenericFactory,
        args.userFactory, args.dbProvider, args.approvalTypes, name);
  }

  @Operator
  public Predicate<ChangeData> message(String text) {
    return new MessagePredicate(args.dbProvider, args.repoManager, text);
  }

  @Operator
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsStarredByPredicate(args.dbProvider, //
        args.userFactory.create(args.dbProvider, account.getId()));
  }

  @Operator
  public Predicate<ChangeData> watchedby(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsWatchedByPredicate(args, args.userFactory.create(
        args.dbProvider, account.getId()));
  }

  @Operator
  public Predicate<ChangeData> draftby(String who) throws QueryParseException,
      OrmException {
    Account account = args.accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new HasDraftByPredicate(args.dbProvider, account.getId());
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account != null) {
      return visibleto(args.userFactory
          .create(args.dbProvider, account.getId()));
    }

    // If its not an account, maybe its a group?
    //
    AccountGroup g = args.groupCache.get(new AccountGroup.NameKey(who));
    if (g != null) {
      return visibleto(new SingleGroupUser(args.authConfig, g.getId()));
    }

    Collection<AccountGroup> matches =
        args.groupCache.get(new AccountGroup.ExternalNameKey(who));
    if (matches != null && !matches.isEmpty()) {
      HashSet<AccountGroup.Id> ids = new HashSet<AccountGroup.Id>();
      for (AccountGroup group : matches) {
        ids.add(group.getId());
      }
      return visibleto(new SingleGroupUser(args.authConfig, ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<ChangeData> visibleto(CurrentUser user) {
    return new IsVisibleToPredicate(args.dbProvider, //
        args.changeControlGenericFactory, //
        user);
  }

  public Predicate<ChangeData> is_visible() {
    return visibleto(currentUser);
  }

  @Operator
  public Predicate<ChangeData> owner(String who) throws QueryParseException,
      OrmException {
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (m.isEmpty()) {
      throw error("User " + who + " not found");
    } else if (m.size() == 1) {
      Account.Id id = m.iterator().next();
      return new OwnerPredicate(args.dbProvider, id);
    } else {
      List<OwnerPredicate> p = new ArrayList<OwnerPredicate>(m.size());
      for (Account.Id id : m) {
        p.add(new OwnerPredicate(args.dbProvider, id));
      }
      return Predicate.or(p);
    }
  }

  @Operator
  public Predicate<ChangeData> ownerin(String group) throws QueryParseException,
      OrmException {
    AccountGroup g = args.groupCache.get(new AccountGroup.NameKey(group));
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return new OwnerinPredicate(args.dbProvider, args.userFactory, g.getId());
  }

  @Operator
  public Predicate<ChangeData> reviewer(String who)
      throws QueryParseException, OrmException {
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (m.isEmpty()) {
      throw error("User " + who + " not found");
    } else if (m.size() == 1) {
      Account.Id id = m.iterator().next();
      return new ReviewerPredicate(args.dbProvider, id);
    } else {
      List<ReviewerPredicate> p = new ArrayList<ReviewerPredicate>(m.size());
      for (Account.Id id : m) {
        p.add(new ReviewerPredicate(args.dbProvider, id));
      }
      return Predicate.or(p);
    }
  }

  @Operator
  public Predicate<ChangeData> reviewerin(String group)
      throws QueryParseException, OrmException {
    AccountGroup g = args.groupCache.get(new AccountGroup.NameKey(group));
    if (g == null) {
      throw error("Group " + group + " not found");
    }
    return new ReviewerinPredicate(args.dbProvider, args.userFactory, g.getId());
  }

  @Operator
  public Predicate<ChangeData> tr(String trackingId) {
    return new TrackingIdPredicate(args.dbProvider, trackingId);
  }

  @Operator
  public Predicate<ChangeData> bug(String trackingId) {
    return tr(trackingId);
  }

  @Operator
  public Predicate<ChangeData> limit(String limit) {
    return limit(Integer.parseInt(limit));
  }

  public Predicate<ChangeData> limit(int limit) {
    return new IntPredicate<ChangeData>(FIELD_LIMIT, limit) {
      @Override
      public boolean match(ChangeData object) {
        return true;
      }

      @Override
      public int getCost() {
        return 0;
      }
    };
  }

  @Operator
  public Predicate<ChangeData> sortkey_after(String sortKey) {
    return new SortKeyPredicate.After(args.dbProvider, sortKey);
  }

  @Operator
  public Predicate<ChangeData> sortkey_before(String sortKey) {
    return new SortKeyPredicate.Before(args.dbProvider, sortKey);
  }

  @Operator
  public Predicate<ChangeData> resume_sortkey(String sortKey) {
    return sortkey_before(sortKey);
  }

  @SuppressWarnings("unchecked")
  public boolean hasLimit(Predicate<ChangeData> p) {
    return find(p, IntPredicate.class, FIELD_LIMIT) != null;
  }

  @SuppressWarnings("unchecked")
  public int getLimit(Predicate<ChangeData> p) {
    return ((IntPredicate) find(p, IntPredicate.class, FIELD_LIMIT)).intValue();
  }

  public boolean hasSortKey(Predicate<ChangeData> p) {
    return find(p, SortKeyPredicate.class, "sortkey_after") != null
        || find(p, SortKeyPredicate.class, "sortkey_before") != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Predicate<ChangeData> defaultField(String query)
      throws QueryParseException {
    if (query.startsWith("refs/")) {
      return ref(query);

    } else if (DEF_CHANGE.matcher(query).matches()) {
      return change(query);

    } else if (PAT_COMMIT.matcher(query).matches()) {
      return commit(query);

    } else if (PAT_EMAIL.matcher(query).find()) {
      try {
        return Predicate.or(owner(query), reviewer(query));
      } catch (OrmException err) {
        throw error("Cannot lookup user", err);
      }

    } else if (PAT_LABEL.matcher(query).find()) {
      return label(query);

    } else {
      // Try to match a project name by substring query.
      final List<ProjectPredicate> predicate =
          new ArrayList<ProjectPredicate>();
      try {
        for (final Project p : args.dbProvider.get().projects().all()) {
          if (p.getName().toLowerCase().contains(query.toLowerCase())) {
            predicate.add(new ProjectPredicate(args.dbProvider, p.getName()));
          }
        }

        // If two or more projects contains "query" as substring create an
        // OrPredicate holding predicates for all these projects, otherwise if
        // only one contains that, return only that one predicate by itself.
        if (predicate.size() == 1) {
          return predicate.get(0);
        } else if (predicate.size() > 1) {
          return Predicate.or(predicate);
        }
      } catch (OrmException e) {
        throw error("Cannot lookup project.", e);
      }

      throw error("Unsupported query:" + query);
    }
  }
}
