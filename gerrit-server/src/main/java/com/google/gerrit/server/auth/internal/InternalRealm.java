// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.auth.internal;

import static com.google.gerrit.reviewdb.client.Account.FieldName.FULL_NAME;
import static com.google.gerrit.reviewdb.client.Account.FieldName.REGISTER_NEW_EMAIL;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_GERRIT;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_INTERNAL;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup.ExternalNameKey;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.Collections;
import java.util.Set;

public class InternalRealm implements Realm {

  private final SchemaFactory<ReviewDb> schema;

  private final Cache<String, Account.Id> usernameCache;

  @Inject
  InternalRealm(
      SchemaFactory<ReviewDb> schema,
      @Named(InternalModule.USERNAME_CACHE) Cache<String, Account.Id> usernameCache) {
    this.schema = schema;
    this.usernameCache = usernameCache;
  }

  @Override
  public boolean allowsEdit(FieldName field) {
    return FULL_NAME == field || REGISTER_NEW_EMAIL == field;
  }

  @Override
  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    String login = who.getUserName();
    String password = who.getPassword();
    AccountExternalId.Key id = new AccountExternalId.Key(SCHEME_INTERNAL, login);

    try {
      ReviewDb db = schema.open();
      try {
        AccountExternalId authData = db.accountExternalIds().get(id);
        if (authData != null) {
          String hash = InternalPasswordHasher.hashPassword(authData, password);
          if (validatePassword(authData, hash)) {
            return who;
          }
        }

        throw new AccountException("Wrong user name or password");
      } finally {
        db.close();
      }
    } catch (OrmException err) {
      throw new AccountException("Cannot connect to database", err);
    }
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {
    usernameCache.put(who.getLocalUser(), account.getId());
  }

  @Override
  public Set<UUID> groups(AccountState who) {
    return who.getInternalGroups();
  }

  @Override
  public Account.Id lookup(String accountName) {
    return usernameCache.get(accountName);
  }

  @Override
  public Set<ExternalNameKey> lookupGroups(String name) {
    return Collections.emptySet();
  }

  @Override
  public AuthRequest link(ReviewDb db, Id to, AuthRequest who)
      throws AccountException {
    return who;
  }

  static class UserLoader extends EntryCreator<String, Account.Id> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    UserLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public Account.Id createEntry(final String username) throws Exception {
      try {
        final ReviewDb db = schema.open();
        try {
          final AccountExternalId extId =
              db.accountExternalIds().get(
                  new AccountExternalId.Key(SCHEME_GERRIT, username));
          return extId != null ? extId.getAccountId() : null;
        } finally {
          db.close();
        }
      } catch (OrmException e) {
        return null;
      }
    }
  }

  private boolean validatePassword(AccountExternalId authData, String hash) {
    final boolean notNullId = authData != null;
    final boolean passwordEquals;
    if (notNullId) {
      passwordEquals = hash.equals(authData.getPassword());
    } else {
      passwordEquals = false;
    }

    return notNullId && passwordEquals;
  }

}
