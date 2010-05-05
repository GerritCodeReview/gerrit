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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

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
   *        ("email@example"), a full name ("Full Name"), or an account id
   *        ("18419").
   * @return the single account that matches; null if no account matches or
   *         there are multiple candidates.
   */
  public Account find(final String nameOrEmail) throws OrmException {
    Matcher m = Pattern.compile("^.* \\(([1-9][0-9]*)\\)$").matcher(nameOrEmail);
    if (m.matches()) {
      return byId.get(Account.Id.parse(m.group(1))).getAccount();
    }

    if (nameOrEmail.matches("^[1-9][0-9]*$")) {
      return byId.get(Account.Id.parse(nameOrEmail)).getAccount();
    }

    return findByNameOrEmail(nameOrEmail);
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
    final int lt = nameOrEmail.indexOf('<');
    final int gt = nameOrEmail.indexOf('>');
    if (lt >= 0 && gt > lt && nameOrEmail.contains("@")) {
      return findByEmail(nameOrEmail.substring(lt + 1, gt));
    }

    if (nameOrEmail.contains("@")) {
      return findByEmail(nameOrEmail);
    }

    final Account.Id id = realm.lookup(nameOrEmail);
    if (id != null) {
      return byId.get(id).getAccount();
    }

    return oneAccount(schema.get().accounts().byFullName(nameOrEmail));
  }

  private Account findByEmail(final String email) {
    final Set<Account.Id> candidates = byEmail.get(email);
    if (1 == candidates.size()) {
      return byId.get(candidates.iterator().next()).getAccount();
    }
    return null;
  }

  private static Account oneAccount(final ResultSet<Account> rs) {
    final List<Account> r = rs.toList();
    return r.size() == 1 ? r.get(0) : null;
  }
}
