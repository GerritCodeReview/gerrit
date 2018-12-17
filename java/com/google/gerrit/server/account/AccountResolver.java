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

package com.google.gerrit.server.account;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AccountResolver {
  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final Accounts accounts;
  private final AccountCache byId;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountControl.Factory accountControlFactory;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Emails emails;

  @Inject
  AccountResolver(
      Provider<CurrentUser> self,
      Realm realm,
      Accounts accounts,
      AccountCache byId,
      IdentifiedUser.GenericFactory userFactory,
      AccountControl.Factory accountControlFactory,
      Provider<InternalAccountQuery> accountQueryProvider,
      Emails emails) {
    this.self = self;
    this.realm = realm;
    this.accounts = accounts;
    this.byId = byId;
    this.userFactory = userFactory;
    this.accountControlFactory = accountControlFactory;
    this.accountQueryProvider = accountQueryProvider;
    this.emails = emails;
  }

  /**
   * Locate exactly one account matching the input string.
   *
   * @param input a string of the format "Full Name &lt;email@example&gt;", just the email address
   *     ("email@example"), a full name ("Full Name"), an account ID ("18419") or a user name
   *     ("username").
   * @return the single account that matches; null if no account matches or there are multiple
   *     candidates. If {@code input} is a numeric string, returns an account if and only if that
   *     number corresponds to an actual account ID.
   */
  public Account find(String input) throws OrmException, IOException, ConfigInvalidException {
    Set<Account.Id> r = findAll(input);
    if (r.size() == 1) {
      return byId.get(r.iterator().next()).map(AccountState::getAccount).orElse(null);
    }

    Account match = null;
    for (Account.Id id : r) {
      Optional<Account> account = byId.get(id).map(AccountState::getAccount);
      if (!account.map(Account::isActive).orElse(false)) {
        continue;
      }
      if (match != null) {
        return null;
      }
      match = account.get();
    }
    return match;
  }

  /**
   * Find all accounts matching the input string.
   *
   * @param input a string of the format "Full Name &lt;email@example&gt;", just the email address
   *     ("email@example"), a full name ("Full Name"), an account ID ("18419") or a user name
   *     ("username").
   * @return the accounts that match, empty set if none. Never null. If {@code input} is a numeric
   *     string, returns a singleton set if that number corresponds to a real account ID, and an
   *     empty set otherwise if it does not.
   */
  public Set<Account.Id> findAll(String input)
      throws OrmException, IOException, ConfigInvalidException {
    Matcher m = Pattern.compile("^.* \\(([1-9][0-9]*)\\)$").matcher(input);
    if (m.matches()) {
      Optional<Account.Id> id = Account.Id.tryParse(m.group(1));
      if (id.isPresent()) {
        return Streams.stream(accounts.get(id.get()))
            .map(a -> a.getAccount().getId())
            .collect(toImmutableSet());
      }
    }

    if (input.matches("^[1-9][0-9]*$")) {
      Optional<Account.Id> id = Account.Id.tryParse(input);
      if (id.isPresent()) {
        return Streams.stream(accounts.get(id.get()))
            .map(a -> a.getAccount().getId())
            .collect(toImmutableSet());
      }
    }

    if (ExternalId.isValidUsername(input)) {
      Optional<AccountState> who = byId.getByUsername(input);
      if (who.isPresent()) {
        return ImmutableSet.of(who.map(a -> a.getAccount().getId()).get());
      }
    }

    return findAllByNameOrEmail(input);
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name").
   * @return the single account that matches; null if no account matches or there are multiple
   *     candidates.
   */
  public Account findByNameOrEmail(String nameOrEmail) throws OrmException, IOException {
    Set<Account.Id> r = findAllByNameOrEmail(nameOrEmail);
    return r.size() == 1
        ? byId.get(r.iterator().next()).map(AccountState::getAccount).orElse(null)
        : null;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name").
   * @return the accounts that match, empty collection if none. Never null.
   */
  public Set<Account.Id> findAllByNameOrEmail(String nameOrEmail) throws OrmException, IOException {
    int lt = nameOrEmail.indexOf('<');
    int gt = nameOrEmail.indexOf('>');
    if (lt >= 0 && gt > lt && nameOrEmail.contains("@")) {
      Set<Account.Id> ids = emails.getAccountFor(nameOrEmail.substring(lt + 1, gt));
      if (ids.isEmpty() || ids.size() == 1) {
        return ids;
      }

      // more than one match, try to return the best one
      String name = nameOrEmail.substring(0, lt - 1);
      Set<Account.Id> nameMatches = new HashSet<>();
      for (Account.Id id : ids) {
        Optional<Account> a = byId.get(id).map(AccountState::getAccount);
        if (a.isPresent() && name.equals(a.get().getFullName())) {
          nameMatches.add(id);
        }
      }
      return nameMatches.isEmpty() ? ids : nameMatches;
    }

    if (nameOrEmail.contains("@")) {
      return emails.getAccountFor(nameOrEmail);
    }

    Account.Id id = realm.lookup(nameOrEmail);
    if (id != null) {
      return Collections.singleton(id);
    }

    List<AccountState> m = accountQueryProvider.get().byFullName(nameOrEmail);
    if (m.size() == 1) {
      return Collections.singleton(m.get(0).getAccount().getId());
    }

    // At this point we have no clue. Just perform a whole bunch of suggestions
    // and pray we come up with a reasonable result list.
    // TODO(dborowitz): This doesn't match the documentation; consider whether it's possible to be
    // more strict here.
    return accountQueryProvider
        .get()
        .byDefault(nameOrEmail)
        .stream()
        .map(a -> a.getAccount().getId())
        .collect(toSet());
  }

  /**
   * Parses a account ID from a request body and returns the user.
   *
   * @param id ID of the account, can be a string of the format "{@code Full Name
   *     <email@example.com>}", just the email address, a full name if it is unique, an account ID,
   *     a user name or "{@code self}" for the calling user
   * @return the user, never null.
   * @throws UnprocessableEntityException thrown if the account ID cannot be resolved or if the
   *     account is not visible to the calling user
   */
  public IdentifiedUser parse(String id)
      throws AuthException, UnprocessableEntityException, OrmException, IOException,
          ConfigInvalidException {
    return parseOnBehalfOf(null, id);
  }

  /**
   * Parses an account ID and returns the user without making any permission check whether the
   * current user can see the account.
   *
   * @param id ID of the account, can be a string of the format "{@code Full Name
   *     <email@example.com>}", just the email address, a full name if it is unique, an account ID,
   *     a user name or "{@code self}" for the calling user
   * @return the user, null if no user is found for the given account ID
   * @throws AuthException thrown if 'self' is used as account ID and the current user is not
   *     authenticated
   * @throws OrmException
   * @throws ConfigInvalidException
   * @throws IOException
   */
  public IdentifiedUser parseId(String id)
      throws AuthException, OrmException, IOException, ConfigInvalidException {
    return parseIdOnBehalfOf(null, id);
  }

  /**
   * Like {@link #parse(String)}, but also sets the {@link CurrentUser#getRealUser()} on the result.
   */
  public IdentifiedUser parseOnBehalfOf(@Nullable CurrentUser caller, String id)
      throws AuthException, UnprocessableEntityException, OrmException, IOException,
          ConfigInvalidException {
    IdentifiedUser user = parseIdOnBehalfOf(caller, id);
    if (user == null || !accountControlFactory.get().canSee(user.getAccount())) {
      throw new UnprocessableEntityException(
          String.format("Account '%s' is not found or ambiguous", id));
    }
    return user;
  }

  private IdentifiedUser parseIdOnBehalfOf(@Nullable CurrentUser caller, String id)
      throws AuthException, OrmException, IOException, ConfigInvalidException {
    if (id.equals("self")) {
      CurrentUser user = self.get();
      if (user.isIdentifiedUser()) {
        return user.asIdentifiedUser();
      } else if (user instanceof AnonymousUser) {
        throw new AuthException("Authentication required");
      } else {
        return null;
      }
    }

    Account match = find(id);
    if (match == null) {
      return null;
    }
    CurrentUser realUser = caller != null ? caller.getRealUser() : null;
    return userFactory.runAs(null, match.getId(), realUser);
  }
}
