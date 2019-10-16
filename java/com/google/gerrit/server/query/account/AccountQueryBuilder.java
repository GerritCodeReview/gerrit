// Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.NotSignedInException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.LimitPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/** Parses a query string meant to be applied to account objects. */
public class AccountQueryBuilder extends QueryBuilder<AccountState, AccountQueryBuilder> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String FIELD_ACCOUNT = "account";
  public static final String FIELD_CAN_SEE = "cansee";
  public static final String FIELD_EMAIL = "email";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_NAME = "name";
  public static final String FIELD_PREFERRED_EMAIL = "preferredemail";
  public static final String FIELD_PREFERRED_EMAIL_EXACT = "preferredemail_exact";
  public static final String FIELD_USERNAME = "username";
  public static final String FIELD_VISIBLETO = "visibleto";

  private static final QueryBuilder.Definition<AccountState, AccountQueryBuilder> mydef =
      new QueryBuilder.Definition<>(AccountQueryBuilder.class);

  public static class Arguments {
    final ChangeFinder changeFinder;
    final PermissionBackend permissionBackend;

    private final Provider<CurrentUser> self;
    private final AccountIndexCollection indexes;

    @Inject
    public Arguments(
        Provider<CurrentUser> self,
        AccountIndexCollection indexes,
        ChangeFinder changeFinder,
        PermissionBackend permissionBackend) {
      this.self = self;
      this.indexes = indexes;
      this.changeFinder = changeFinder;
      this.permissionBackend = permissionBackend;
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

    Schema<AccountState> schema() {
      Index<?, AccountState> index = indexes != null ? indexes.getSearchIndex() : null;
      return index != null ? index.getSchema() : null;
    }
  }

  private final Arguments args;

  @Inject
  AccountQueryBuilder(Arguments args) {
    super(mydef, null);
    this.args = args;
  }

  @Operator
  public Predicate<AccountState> cansee(String change)
      throws QueryParseException, PermissionBackendException {
    ChangeNotes changeNotes = args.changeFinder.findOne(change);
    if (changeNotes == null) {
      throw error(String.format("change %s not found", change));
    }

    try {
      args.permissionBackend.user(args.getUser()).change(changeNotes).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw error(String.format("change %s not found", change));
    }

    return AccountPredicates.cansee(args, changeNotes);
  }

  @Operator
  public Predicate<AccountState> email(String email)
      throws PermissionBackendException, QueryParseException {
    if (canSeeSecondaryEmails()) {
      return AccountPredicates.emailIncludingSecondaryEmails(email);
    }

    if (args.schema().hasField(AccountField.PREFERRED_EMAIL)) {
      return AccountPredicates.preferredEmail(email);
    }

    throw new QueryParseException("'email' operator is not supported by account index version");
  }

  @Operator
  public Predicate<AccountState> is(String value) throws QueryParseException {
    if ("active".equalsIgnoreCase(value)) {
      return AccountPredicates.isActive();
    }
    if ("inactive".equalsIgnoreCase(value)) {
      return AccountPredicates.isNotActive();
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
  public Predicate<AccountState> name(String name)
      throws PermissionBackendException, QueryParseException {
    if (canSeeSecondaryEmails()) {
      return AccountPredicates.equalsNameIncludingSecondaryEmails(name);
    }

    if (args.schema().hasField(AccountField.NAME_PART_NO_SECONDARY_EMAIL)) {
      return AccountPredicates.equalsName(name);
    }

    return AccountPredicates.fullName(name);
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
    Predicate<AccountState> defaultPredicate =
        AccountPredicates.defaultPredicate(args.schema(), checkedCanSeeSecondaryEmails(), query);
    if (query.startsWith("cansee:")) {
      try {
        return cansee(query.substring(7));
      } catch (StorageException | QueryParseException | PermissionBackendException e) {
        // Ignore, fall back to default query
      }
    }

    if ("self".equalsIgnoreCase(query) || "me".equalsIgnoreCase(query)) {
      try {
        return Predicate.or(defaultPredicate, AccountPredicates.id(args.schema(), self()));
      } catch (QueryParseException e) {
        // Skip.
      }
    }
    return defaultPredicate;
  }

  private Account.Id self() throws QueryParseException {
    return args.getIdentifiedUser().getAccountId();
  }

  private boolean canSeeSecondaryEmails() throws PermissionBackendException, QueryParseException {
    try {
      args.permissionBackend.user(args.getUser()).check(GlobalPermission.MODIFY_ACCOUNT);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private boolean checkedCanSeeSecondaryEmails() {
    try {
      return canSeeSecondaryEmails();
    } catch (PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Permission check failed");
      return false;
    } catch (QueryParseException e) {
      // User is not signed in.
      return false;
    }
  }
}
