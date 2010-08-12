// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

@Singleton
public class AccountDiffPreferencesCacheImpl implements
    AccountDiffPreferencesCache {
  private static final String BY_ACCOUNT_ID = "diff_pref";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountDiffPreference>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, AccountDiffPreference>>() {};
        cache(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        bind(AccountDiffPreferencesCacheImpl.class);
        bind(AccountDiffPreferencesCache.class).to(
            AccountDiffPreferencesCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Id, AccountDiffPreference> byAccountId;

  @Inject
  AccountDiffPreferencesCacheImpl(
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, AccountDiffPreference> byAccountId) {
    this.byAccountId = byAccountId;
  }

  @Override
  public ListenableFuture<AccountDiffPreference> get(Account.Id key) {
    return byAccountId.get(key);
  }

  @Override
  public ListenableFuture<Void> evictAsync(Account.Id key) {
    return byAccountId.removeAsync(key);
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, AccountDiffPreference> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountDiffPreference createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return db.accountDiffPreferences().get(id);
      } finally {
        db.close();
      }
    }

    @Override
    public AccountDiffPreference missing(Account.Id key) {
      return AccountDiffPreference.createDefault(key);
    }

  }
}
