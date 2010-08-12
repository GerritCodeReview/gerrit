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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.List;

@Singleton
public class AccountAgreementsCacheImpl implements AccountAgreementsCache {
  private static final String BY_ACCOUNT_ID = "account_agreements";

  protected static class AccountAgreementsList {
    @Column(id = 1)
    protected List<AccountAgreement> list;

    protected AccountAgreementsList() {
    }

    public AccountAgreementsList(List<AccountAgreement> list) {
      this.list = list;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountAgreementsList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, AccountAgreementsList>>() {};
        core(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        bind(AccountAgreementsCacheImpl.class);
        bind(AccountAgreementsCache.class).to(AccountAgreementsCacheImpl.class);
      }
    };
  }

  private static final Function<AccountAgreementsList, List<AccountAgreement>> unpack =
      new Function<AccountAgreementsList, List<AccountAgreement>>() {
        public List<AccountAgreement> apply(AccountAgreementsList in) {
          return in.list;
        }
      };

  private final Cache<Account.Id, AccountAgreementsList> byAccountId;

  @Inject
  AccountAgreementsCacheImpl(
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, AccountAgreementsList> byAccountId) {
    this.byAccountId = byAccountId;
  }

  @Override
  public ListenableFuture<List<AccountAgreement>> byAccount(Account.Id id) {
    return Futures.compose(byAccountId.get(id), unpack);
  }

  @Override
  public ListenableFuture<Void> evictAsync(AccountAgreement.Key key) {
    return byAccountId.removeAsync(key.getParentKey());
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, AccountAgreementsList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountAgreementsList createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountAgreementsList(db.accountAgreements().byAccount(id)
            .toList());
      } finally {
        db.close();
      }
    }
  }
}
