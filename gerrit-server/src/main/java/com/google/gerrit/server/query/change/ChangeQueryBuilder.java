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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

/**
 * Parses a query string meant to be applied to change objects.
 */
public class ChangeQueryBuilder extends QueryBuilder<ChangeData> {
  public static final String FIELD_CHANGE = "change";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_IS = "is";

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> mydef =
      new QueryBuilder.Definition<ChangeData, ChangeQueryBuilder>(
          ChangeQueryBuilder.class);

  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> currentUser;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountResolver accountResolver;

  @Inject
  ChangeQueryBuilder(Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> currentUser,
      IdentifiedUser.GenericFactory userFactory,
      final ChangeControl.Factory changeControlFactory,
      AccountResolver accountResolver) {
    super(mydef);
    this.dbProvider = dbProvider;
    this.currentUser = currentUser;
    this.userFactory = userFactory;
    this.changeControlFactory = changeControlFactory;
    this.accountResolver = accountResolver;
  }

  Provider<ReviewDb> getReviewDbProvider() {
    return dbProvider;
  }

  @Operator
  public Predicate<ChangeData> change(final String value) {
    return new LegacyChangeIdPredicate(Change.Id.parse(value));
  }

  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("open".equals(statusName)) {
      return ChangeStatusPredicate.open(dbProvider);

    } else if ("closed".equals(statusName)) {
      return ChangeStatusPredicate.closed(dbProvider);

    } else {
      return new ChangeStatusPredicate(dbProvider, statusName);
    }
  }

  @Operator
  public Predicate<ChangeData> is(String value) {
    if ("starred".equalsIgnoreCase(value)) {
      return new IsStarredByPredicate(currentUser.get());
    }

    if ("visible".equalsIgnoreCase(value)) {
      return new IsVisibleToPredicate(dbProvider, changeControlFactory,
          currentUser.get());
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
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsStarredByPredicate(userFactory.create(account.getId()));
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, OrmException {
    Account account = accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return new IsVisibleToPredicate(dbProvider, changeControlFactory,
        userFactory.create(account.getId()));
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
  public Predicate<ChangeData> limit(String limit) {
    return new IntPredicate<ChangeData>("limit", limit) {
      @Override
      public boolean match(ChangeData object) {
        return true;
      }
    };
  }

  public Predicate<ChangeData> limit(int limit) {
    return new IntPredicate<ChangeData>("limit", limit) {
      @Override
      public boolean match(ChangeData object) {
        return true;
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

  @Override
  protected Predicate<ChangeData> defaultField(String value)
      throws QueryParseException {
    if (value.matches("^[1-9][0-9]*$")) {
      return change(value);

    } else if (value.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      return commit(value);

    } else {
      throw error("Unsupported query:" + value);
    }
  }
}
