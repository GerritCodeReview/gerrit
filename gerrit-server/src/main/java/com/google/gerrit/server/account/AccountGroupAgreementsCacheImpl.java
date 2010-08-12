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
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.AccountGroupAgreement.Key;
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
public class AccountGroupAgreementsCacheImpl implements
    AccountGroupAgreementsCache {
  private static final String BY_GROUP_ID = "account_group_agreements";

  protected static class AccountGroupAgreementList {
    @Column(id = 1)
    protected List<AccountGroupAgreement> list;

    protected AccountGroupAgreementList() {
    }

    public AccountGroupAgreementList(List<AccountGroupAgreement> list) {
      this.list = list;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, AccountGroupAgreementList>> byGroupIdType =
            new TypeLiteral<Cache<AccountGroup.Id, AccountGroupAgreementList>>() {};
        cache(byGroupIdType, BY_GROUP_ID).populateWith(ByGroupIdLoader.class);

        bind(AccountGroupAgreementsCacheImpl.class);
        bind(AccountGroupAgreementsCache.class).to(
            AccountGroupAgreementsCacheImpl.class);
      }
    };
  }

  private static final Function<AccountGroupAgreementList, List<AccountGroupAgreement>> unpack =
      new Function<AccountGroupAgreementList, List<AccountGroupAgreement>>() {
        public List<AccountGroupAgreement> apply(AccountGroupAgreementList in) {
          return in.list;
        }
      };

  private final Cache<AccountGroup.Id, AccountGroupAgreementList> byGroupId;

  @Inject
  AccountGroupAgreementsCacheImpl(
      @Named(BY_GROUP_ID) Cache<AccountGroup.Id, AccountGroupAgreementList> byGroupId) {
    this.byGroupId = byGroupId;
  }

  @Override
  public ListenableFuture<List<AccountGroupAgreement>> byGroup(
      AccountGroup.Id id) {
    return Futures.compose(byGroupId.get(id), unpack);
  }

  @Override
  public ListenableFuture<Void> evictAsync(Key key) {
    return byGroupId.removeAsync(key.getParentKey());
  }

  static class ByGroupIdLoader extends
      EntryCreator<AccountGroup.Id, AccountGroupAgreementList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByGroupIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountGroupAgreementList createEntry(AccountGroup.Id id)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountGroupAgreementList(db.accountGroupAgreements()
            .byGroup(id).toList());
      } finally {
        db.close();
      }
    }
  }
}
