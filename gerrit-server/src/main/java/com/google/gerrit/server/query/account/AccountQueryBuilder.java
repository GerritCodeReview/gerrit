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

import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parses a query string meant to be applied to account objects.
 */
public class AccountQueryBuilder extends QueryBuilder<AccountState> {
  public interface ChangeOperatorFactory
      extends OperatorFactory<AccountState, AccountQueryBuilder> {
  }

  public static final String FIELD_ACCOUNT = "account";
  public static final String FIELD_LIMIT = "limit";

  private static final QueryBuilder.Definition<AccountState, AccountQueryBuilder> mydef =
      new QueryBuilder.Definition<>(AccountQueryBuilder.class);

  public static class Arguments {
    final AccountResolver accountResolver;

    private final Provider<CurrentUser> self;

    @Inject
    public Arguments(AccountResolver accountResolver,
        Provider<CurrentUser> self) {
      this.accountResolver = accountResolver;
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
  public Predicate<AccountState> account(String query)
      throws QueryParseException, OrmException {
    Set<Account.Id> ids = parseAccount(query);
    List<Predicate<AccountState>> p =
        Lists.newArrayListWithCapacity(ids.size());
    for (Account.Id id : ids) {
      p.add(new AccountIdPredicate(id));
    }
    return Predicate.or(p);
  }

  @Operator
  public Predicate<ChangeData> limit(String limit) throws QueryParseException {
    return new LimitPredicate<>(FIELD_LIMIT, Integer.parseInt(limit));
  }

  @Override
  protected Predicate<AccountState> defaultField(String query)
      throws QueryParseException {
    try {
      return account(query);
    } catch (OrmException e) {
      throw new QueryParseException("Invalid account query");
    }
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

  private Account.Id self() throws QueryParseException {
    return args.getIdentifiedUser().getAccountId();
  }
}
