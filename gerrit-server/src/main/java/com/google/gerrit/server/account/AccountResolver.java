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

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class AccountResolver {
  private final Realm realm;
  private final AccountByEmailCache byEmail;
  private final AccountCache byId;
  private final AccountIndexCollection accountIndexes;
  private final Provider<InternalAccountQuery> accountQueryProvider;

  @Inject
  AccountResolver(
      Realm realm,
      AccountByEmailCache byEmail,
      AccountCache byId,
      AccountIndexCollection accountIndexes,
      Provider<InternalAccountQuery> accountQueryProvider) {
    this.realm = realm;
    this.byEmail = byEmail;
    this.byId = byId;
    this.accountIndexes = accountIndexes;
    this.accountQueryProvider = accountQueryProvider;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name"), an account id ("18419") or an user
   *     name ("username").
   * @return the single account that matches; null if no account matches or there are multiple
   *     candidates.
   */
  public Account find(ReviewDb db, String nameOrEmail) throws OrmException {
    Set<Account.Id> r = findAll(db, nameOrEmail);
    if (r.size() == 1) {
      return byId.get(r.iterator().next()).getAccount();
    }

    Account match = null;
    for (Account.Id id : r) {
      Account account = byId.get(id).getAccount();
      if (!account.isActive()) {
        continue;
      }
      if (match != null) {
        return null;
      }
      match = account;
    }
    return match;
  }

  /**
   * Find all accounts matching the name or name/email string.
   *
   * @param db open database handle.
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name"), an account id ("18419") or an user
   *     name ("username").
   * @return the accounts that match, empty collection if none. Never null.
   */
  public Set<Account.Id> findAll(ReviewDb db, String nameOrEmail) throws OrmException {
    Matcher m = Pattern.compile("^.* \\(([1-9][0-9]*)\\)$").matcher(nameOrEmail);
    if (m.matches()) {
      Account.Id id = Account.Id.parse(m.group(1));
      if (exists(db, id)) {
        return Collections.singleton(id);
      }
      return Collections.emptySet();
    }

    if (nameOrEmail.matches("^[1-9][0-9]*$")) {
      Account.Id id = Account.Id.parse(nameOrEmail);
      if (exists(db, id)) {
        return Collections.singleton(id);
      }
      return Collections.emptySet();
    }

    if (nameOrEmail.matches(Account.USER_NAME_PATTERN)) {
      AccountState who = byId.getByUsername(nameOrEmail);
      if (who != null) {
        return Collections.singleton(who.getAccount().getId());
      }
    }

    return findAllByNameOrEmail(db, nameOrEmail);
  }

  private boolean exists(ReviewDb db, Account.Id id) throws OrmException {
    return db.accounts().get(id) != null;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param db open database handle.
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name").
   * @return the single account that matches; null if no account matches or there are multiple
   *     candidates.
   */
  public Account findByNameOrEmail(ReviewDb db, String nameOrEmail) throws OrmException {
    Set<Account.Id> r = findAllByNameOrEmail(db, nameOrEmail);
    return r.size() == 1 ? byId.get(r.iterator().next()).getAccount() : null;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param db open database handle.
   * @param nameOrEmail a string of the format "Full Name &lt;email@example&gt;", just the email
   *     address ("email@example"), a full name ("Full Name").
   * @return the accounts that match, empty collection if none. Never null.
   */
  public Set<Account.Id> findAllByNameOrEmail(ReviewDb db, String nameOrEmail) throws OrmException {
    int lt = nameOrEmail.indexOf('<');
    int gt = nameOrEmail.indexOf('>');
    if (lt >= 0 && gt > lt && nameOrEmail.contains("@")) {
      Set<Account.Id> ids = byEmail.get(nameOrEmail.substring(lt + 1, gt));
      if (ids.isEmpty() || ids.size() == 1) {
        return ids;
      }

      // more than one match, try to return the best one
      String name = nameOrEmail.substring(0, lt - 1);
      Set<Account.Id> nameMatches = new HashSet<>();
      for (Account.Id id : ids) {
        Account a = byId.get(id).getAccount();
        if (name.equals(a.getFullName())) {
          nameMatches.add(id);
        }
      }
      return nameMatches.isEmpty() ? ids : nameMatches;
    }

    if (nameOrEmail.contains("@")) {
      return byEmail.get(nameOrEmail);
    }

    Account.Id id = realm.lookup(nameOrEmail);
    if (id != null) {
      return Collections.singleton(id);
    }

    if (accountIndexes.getSearchIndex() != null) {
      List<AccountState> m = accountQueryProvider.get().byFullName(nameOrEmail);
      if (m.size() == 1) {
        return Collections.singleton(m.get(0).getAccount().getId());
      }

      // At this point we have no clue. Just perform a whole bunch of suggestions
      // and pray we come up with a reasonable result list.
      return accountQueryProvider
          .get()
          .byDefault(nameOrEmail)
          .stream()
          .map(a -> a.getAccount().getId())
          .collect(toSet());
    }

    List<Account> m = db.accounts().byFullName(nameOrEmail).toList();
    if (m.size() == 1) {
      return Collections.singleton(m.get(0).getId());
    }

    // At this point we have no clue. Just perform a whole bunch of suggestions
    // and pray we come up with a reasonable result list.
    Set<Account.Id> result = new HashSet<>();
    String a = nameOrEmail;
    String b = nameOrEmail + "\u9fa5";
    for (Account act : db.accounts().suggestByFullName(a, b, 10)) {
      result.add(act.getId());
    }
    for (AccountExternalId extId :
        db.accountExternalIds()
            .suggestByKey(
                new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, a),
                new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, b),
                10)) {
      result.add(extId.getAccountId());
    }
    for (AccountExternalId extId : db.accountExternalIds().suggestByEmailAddress(a, b, 10)) {
      result.add(extId.getAccountId());
    }
    return result;
  }
}
