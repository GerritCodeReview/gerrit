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
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Parses a query string meant to be applied to change objects.
 */
@RequestScoped
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
  public static final String FIELD_IS = "is";
  public static final String FIELD_HAS = "has";
  public static final String FIELD_LABEL = "label";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_STARREDBY = "starredby";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_TOPIC = "topic";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> mydef =
      new QueryBuilder.Definition<ChangeData, ChangeQueryBuilder>(
          ChangeQueryBuilder.class);

  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> currentUser;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountResolver accountResolver;
  private final GroupCache groupCache;
  private final AuthConfig authConfig;
  private final ApprovalTypes approvalTypes;
  private final Project.NameKey wildProjectName;

  @Inject
  ChangeQueryBuilder(Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> currentUser,
      IdentifiedUser.GenericFactory userFactory,
      ChangeControl.Factory changeControlFactory,
      AccountResolver accountResolver, GroupCache groupCache,
      AuthConfig authConfig, ApprovalTypes approvalTypes,
      @WildProjectName Project.NameKey wildProjectName) {
    super(mydef);
    this.dbProvider = dbProvider;
    this.currentUser = currentUser;
    this.userFactory = userFactory;
    this.changeControlFactory = changeControlFactory;
    this.accountResolver = accountResolver;
    this.groupCache = groupCache;
    this.authConfig = authConfig;
    this.approvalTypes = approvalTypes;
    this.wildProjectName = wildProjectName;
  }

  Provider<ReviewDb> getReviewDbProvider() {
    return dbProvider;
  }

  @Operator
  public Predicate<ChangeData> age(String value) {
    return new AgePredicate(dbProvider, value);
  }

  @Operator
  public Predicate<ChangeData> change(String query) {
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      return new LegacyChangeIdPredicate(dbProvider, Change.Id.parse(query));

    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      if (query.charAt(0) == 'i') {
        query = "I" + query.substring(1);
      }
      return new ChangeIdPredicate(dbProvider, query);
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("open".equals(statusName)) {
      return status_open();

    } else if ("closed".equals(statusName)) {
      return ChangeStatusPredicate.closed(dbProvider);

    } else if ("reviewed".equalsIgnoreCase(statusName)) {
      return new IsReviewedPredicate(dbProvider);

    } else {
      return new ChangeStatusPredicate(dbProvider, statusName);
    }
  }

  public Predicate<ChangeData> status_open() {
    return ChangeStatusPredicate.open(dbProvider);
  }

  @Operator
  public Predicate<ChangeData> has(String value) {
    if ("star".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(dbProvider, currentUser.get());
    }

    if ("draft".equalsIgnoreCase(value)) {
      if (currentUser.get() instanceof IdentifiedUser) {
        return new HasDraftByPredicate(dbProvider,
            ((IdentifiedUser) currentUser.get()).getAccountId());
      }
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> is(String value) {
    if ("starred".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(dbProvider, currentUser.get());
    }

    if ("watched".equalsIgnoreCase(value)) {
      return new IsWatchedByPredicate(dbProvider, wildProjectName, //
          currentUser.get());
    }

    if ("visible".equalsIgnoreCase(value)) {
      return new IsVisibleToPredicate(dbProvider, changeControlFactory,
          currentUser.get());
    }

    if ("reviewed".equalsIgnoreCase(value)) {
      return new IsReviewedPredicate(dbProvider);
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
    return new CommitPredicate(dbProvider, AbbreviatedObjectId.fromString(id));
  }

  @Operator
  public Predicate<ChangeData> project(String name) {
    return new ProjectPredicate(dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> branch(String name) {
    return new BranchPredicate(dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    return new TopicPredicate(dbProvider, name);
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) {
    return new RefPredicate(dbProvider, ref);
  }

  @Operator
  public Predicate<ChangeData> label(String name) {
    return new LabelPredicate(dbProvider, approvalTypes, name);
  }

  @Operator
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsStarredByPredicate(dbProvider, //
        userFactory.create(dbProvider, account.getId()));
  }

  @Operator
  public Predicate<ChangeData> watchedby(String who)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsWatchedByPredicate(dbProvider, wildProjectName, //
        userFactory.create(dbProvider, account.getId()));
  }

  @Operator
  public Predicate<ChangeData> draftby(String who) throws QueryParseException,
      OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new HasDraftByPredicate(dbProvider, account.getId());
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(who);
    if (account != null) {
      return visibleto(userFactory.create(dbProvider, account.getId()));
    }

    // If its not an account, maybe its a group?
    //
    AccountGroup g = groupCache.get(new AccountGroup.NameKey(who));
    if (g != null) {
      return visibleto(new SingleGroupUser(authConfig, g.getId()));
    }

    Collection<AccountGroup> matches =
        groupCache.get(new AccountGroup.ExternalNameKey(who));
    if (matches != null && !matches.isEmpty()) {
      HashSet<AccountGroup.Id> ids = new HashSet<AccountGroup.Id>();
      for (AccountGroup group : matches) {
        ids.add(group.getId());
      }
      return visibleto(new SingleGroupUser(authConfig, ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<ChangeData> visibleto(CurrentUser user) {
    return new IsVisibleToPredicate(dbProvider, changeControlFactory, user);
  }

  @Operator
  public Predicate<ChangeData> owner(String who) throws QueryParseException,
      OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new OwnerPredicate(dbProvider, account.getId());
  }

  @Operator
  public Predicate<ChangeData> reviewer(String nameOrEmail)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(nameOrEmail);
    if (account == null) {
      throw error("Reviewer " + nameOrEmail + " not found");
    }
    return new ReviewerPredicate(dbProvider, account.getId());
  }

  @Operator
  public Predicate<ChangeData> tr(String trackingId) {
    return new TrackingIdPredicate(dbProvider, trackingId);
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
    return new IntPredicate<ChangeData>("limit", limit) {
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
    return new SortKeyPredicate.After(dbProvider, sortKey);
  }

  @Operator
  public Predicate<ChangeData> sortkey_before(String sortKey) {
    return new SortKeyPredicate.Before(dbProvider, sortKey);
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
      throw error("Unsupported query:" + query);
    }
  }
}
