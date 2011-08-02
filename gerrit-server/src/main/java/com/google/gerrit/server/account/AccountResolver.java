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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccountResolver {
  private final Realm realm;
  private final AccountByEmailCache byEmail;
  private final AccountCache byId;
  private final Provider<ReviewDb> schema;

  @Inject
  AccountResolver(final Realm realm, final AccountByEmailCache byEmail,
      final AccountCache byId, final Provider<ReviewDb> schema) {
    this.realm = realm;
    this.byEmail = byEmail;
    this.byId = byId;
    this.schema = schema;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format
   *        "Full Name &lt;email@example&gt;", just the email address
   *        ("email@example"), a full name ("Full Name"), an account id
   *        ("18419") or an user name ("username").
   * @return the single account that matches; null if no account matches or
   *         there are multiple candidates.
   */
  public Account find(final String nameOrEmail) throws OrmException {
    Set<Account.Id> r = findAll(nameOrEmail);
    return r.size() == 1 ? byId.get(r.iterator().next()).getAccount() : null;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format
   *        "Full Name &lt;email@example&gt;", just the email address
   *        ("email@example"), a full name ("Full Name"), an account id
   *        ("18419") or an user name ("username").
   * @return the accounts that match, empty collection if none.  Never null.
   */
  public Set<Account.Id> findAll(String nameOrEmail) throws OrmException {
    Matcher m = Pattern.compile("^.* \\(([1-9][0-9]*)\\)$").matcher(nameOrEmail);
    if (m.matches()) {
      return Collections.singleton(Account.Id.parse(m.group(1)));
    }

    if (nameOrEmail.matches("^[1-9][0-9]*$")) {
      return Collections.singleton(Account.Id.parse(nameOrEmail));
    }

    if (nameOrEmail.matches(Account.USER_NAME_PATTERN)) {
      AccountState who = byId.getByUsername(nameOrEmail);
      if (who != null) {
        return Collections.singleton(who.getAccount().getId());
      }
    }

    return findAllByNameOrEmail(nameOrEmail);
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format
   *        "Full Name &lt;email@example&gt;", just the email address
   *        ("email@example"), a full name ("Full Name").
   * @return the single account that matches; null if no account matches or
   *         there are multiple candidates.
   */
  public Account findByNameOrEmail(final String nameOrEmail)
      throws OrmException {
    Set<Account.Id> r = findAllByNameOrEmail(nameOrEmail);
    return r.size() == 1 ? byId.get(r.iterator().next()).getAccount() : null;
  }

  /**
   * Locate exactly one account matching the name or name/email string.
   *
   * @param nameOrEmail a string of the format
   *        "Full Name &lt;email@example&gt;", just the email address
   *        ("email@example"), a full name ("Full Name").
   * @return the accounts that match, empty collection if none. Never null.
   */
  public Set<Account.Id> findAllByNameOrEmail(final String nameOrEmail)
      throws OrmException {
    final int lt = nameOrEmail.indexOf('<');
    final int gt = nameOrEmail.indexOf('>');
    if (lt >= 0 && gt > lt && nameOrEmail.contains("@")) {
      final String fullName = nameOrEmail.substring(0, lt - 1);
      final String email = nameOrEmail.substring(lt + 1, gt);
      List<Account> m = schema.get().accounts().byFullName(fullName).toList();
      Set<Account.Id> s = new HashSet<Account.Id>();
      for (Account a : m) {
        if (a.getPreferredEmail().equals(email)) {
          s.add(a.getId());
        }
      }
      return s;
    }

    if (nameOrEmail.contains("@")) {
      return byEmail.get(nameOrEmail);
    }

    final Account.Id id = realm.lookup(nameOrEmail);
    if (id != null) {
      return Collections.singleton(id);
    }

    List<Account> m = schema.get().accounts().byFullName(nameOrEmail).toList();
    if (m.size() == 1) {
      return Collections.singleton(m.get(0).getId());
    }

    // At this point we have no clue. Just perform a whole bunch of suggestions
    // and pray we come up with a reasonable result list.
    //
    Set<Account.Id> result = new HashSet<Account.Id>();
    String a = nameOrEmail;
    String b = nameOrEmail + "\u9fa5";
    for (Account act : schema.get().accounts().suggestByFullName(a, b, 10)) {
      result.add(act.getId());
    }
    for (AccountExternalId extId : schema
        .get()
        .accountExternalIds()
        .suggestByKey(
            new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, a),
            new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, b), 10)) {
      result.add(extId.getAccountId());
    }
    for (AccountExternalId extId : schema.get().accountExternalIds()
        .suggestByEmailAddress(a, b, 10)) {
      result.add(extId.getAccountId());
    }
    return result;
  }
}
