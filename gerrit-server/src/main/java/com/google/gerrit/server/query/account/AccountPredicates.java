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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;

public class AccountPredicates {
  public static boolean hasActive(Predicate<AccountState> p) {
    return QueryBuilder.find(p, AccountPredicate.class,
        AccountField.ACTIVE.getName()) != null;
  }

  static Predicate<AccountState> id(Account.Id accountId) {
    return new AccountPredicate(AccountField.ID,
        AccountQueryBuilder.FIELD_ACCOUNT, accountId.toString());
  }

  static Predicate<AccountState> email(String email) {
    return new AccountPredicate(AccountField.EMAIL,
        AccountQueryBuilder.FIELD_EMAIL, email);
  }

  static Predicate<AccountState> equalsName(String name) {
    return new AccountPredicate(AccountField.NAME_PART,
        AccountQueryBuilder.FIELD_NAME, name);
  }

  public static Predicate<AccountState> isActive() {
    return new AccountPredicate(AccountField.ACTIVE, "1");
  }

  static Predicate<AccountState> isInactive() {
    return new AccountPredicate(AccountField.ACTIVE, "0");
  }

  static Predicate<AccountState> username(String username) {
    return new AccountPredicate(AccountField.USERNAME,
        AccountQueryBuilder.FIELD_USERNAME, username);
  }

  static class AccountPredicate extends IndexPredicate<AccountState> {
    AccountPredicate(FieldDef<AccountState, ?> def, String value) {
      super(def, value);
    }

    AccountPredicate(FieldDef<AccountState, ?> def, String name, String value) {
      super(def, name, value);
    }
  }

  private AccountPredicates() {
  }
}
