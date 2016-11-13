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

package com.google.gerrit.server.query.account;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/** Parses a query string meant to be applied to account objects. */
public class AccountQueryBuilder extends QueryBuilder<AccountState> {
  public interface ChangeOperatorFactory
      extends OperatorFactory<AccountState, AccountQueryBuilder> {}

  public static final String FIELD_ACCOUNT = "account";
  public static final String FIELD_EMAIL = "email";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_NAME = "name";
  public static final String FIELD_USERNAME = "username";
  public static final String FIELD_VISIBLETO = "visibleto";

  private static final QueryBuilder.Definition<AccountState, AccountQueryBuilder> mydef =
      new QueryBuilder.Definition<>(AccountQueryBuilder.class);

  public static class Arguments {
    private final Provider<CurrentUser> self;

    @Inject
    public Arguments(Provider<CurrentUser> self) {
      this.self = self;
    }

    IdentifiedUser getIdentifiedUser() throws QueryParseException {
      try {
        CurrentUser u = getUser();
        if (u.isIdentifiedUser()) {
          return u.asIdentifiedUser();
        }
        throw new QueryParseException(NotSignedInException.MESSAGE);
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }

    CurrentUser getUser() throws QueryParseException {
      try {
        return self.get();
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }
  }

  private final Arguments args;

  @Inject
  AccountQueryBuilder(Arguments args) {
    super(mydef);
    this.args = args;
  }

  @Operator
  public Predicate<AccountState> email(String email) {
    return AccountPredicates.email(email);
  }

  @Operator
  public Predicate<AccountState> is(String value) throws QueryParseException {
    if ("active".equalsIgnoreCase(value)) {
      return AccountPredicates.isActive();
    }
    if ("inactive".equalsIgnoreCase(value)) {
      return AccountPredicates.isInactive();
    }
    throw error("Invalid query");
  }

  @Operator
  public Predicate<AccountState> limit(String query) throws QueryParseException {
    Integer limit = Ints.tryParse(query);
    if (limit == null) {
      throw error("Invalid limit: " + query);
    }
    return new LimitPredicate<>(FIELD_LIMIT, limit);
  }

  @Operator
  public Predicate<AccountState> name(String name) {
    return AccountPredicates.equalsName(name);
  }

  @Operator
  public Predicate<AccountState> username(String username) {
    return AccountPredicates.username(username);
  }

  public Predicate<AccountState> defaultQuery(String query) {
    return Predicate.and(
        Lists.transform(
            Splitter.on(' ').omitEmptyStrings().splitToList(query), this::defaultField));
  }

  @Override
  protected Predicate<AccountState> defaultField(String query) {
    Predicate<AccountState> defaultPredicate = AccountPredicates.defaultPredicate(query);
    if ("self".equalsIgnoreCase(query)) {
      try {
        return Predicate.or(defaultPredicate, AccountPredicates.id(self()));
      } catch (QueryParseException e) {
        // Skip.
      }
    }
    return defaultPredicate;
  }

  private Account.Id self() throws QueryParseException {
    return args.getIdentifiedUser().getAccountId();
  }
}
