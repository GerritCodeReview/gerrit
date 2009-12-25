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

package com.google.gerrit.server.auth.userpass;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Account.FieldName;
import com.google.gerrit.reviewdb.AccountGroup.ExternalNameKey;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.password.Password;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

@Singleton
class UserPassRealm implements Realm {
  private final SchemaFactory<ReviewDb> schema;
  private final DefaultRealm defaultRealm;
  private final Password password;

  @Inject
  UserPassRealm(SchemaFactory<ReviewDb> schema, DefaultRealm defaultRealm,
      Password password) {
    this.schema = schema;
    this.defaultRealm = defaultRealm;
    this.password = password;
  }

  public boolean allowsEdit(FieldName field) {
    return defaultRealm.allowsEdit(field);
  }

  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    if (who.getUserName() == null) {
      throw new AccountException("Invalid username or password");
    }
    if (who.getPassword() == null) {
      throw new AccountException("Invalid username or password");
    }

    try {
      final ReviewDb db = schema.open();
      try {
        final Account a = db.accounts().byUserName(who.getUserName());
        if (a == null) {
          throw new AccountException("Invalid username or password");
        }
        if (!password.check(who.getPassword(), a.getPasswordHash())) {
          throw new AccountException("Invalid username or password");
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new AccountException("Authentication error", e);
    }

    return defaultRealm.authenticate(who);
  }

  public void onCreateAccount(AuthRequest who, Account account) {
    defaultRealm.onCreateAccount(who, account);
  }

  public Set<AccountGroup.Id> groups(AccountState who) {
    return defaultRealm.groups(who);
  }

  public Account.Id lookup(String accountName) {
    return defaultRealm.lookup(accountName);
  }

  public Set<ExternalNameKey> lookupGroups(String name) {
    return defaultRealm.lookupGroups(name);
  }
}
