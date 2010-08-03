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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
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

import java.util.ArrayList;
import java.util.List;

@Singleton
public class AccountExternalIdCacheImpl implements AccountExternalIdCache {
  private static final String BY_KEY = "ext_id_key";
  private static final String BY_ACCOUNT_ID = "ext_id_account";
  private static final String BY_EMAIL = "ext_id_email";

  protected static class AccountExternalIdList {
    @Column(id = 1)
    protected List<AccountExternalId> list;

    protected AccountExternalIdList() {
    }

    public AccountExternalIdList(List<AccountExternalId> list) {
      this.list = list;
    }
  }

  protected static class EmailWrapper {
    @Column(id = 1)
    protected String email;

    protected EmailWrapper() {
    }

    public EmailWrapper(String email) {
      this.email = email;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountExternalId.Key, AccountExternalId>> byKeyType =
            new TypeLiteral<Cache<AccountExternalId.Key, AccountExternalId>>() {};
        core(byKeyType, BY_KEY).populateWith(ByKeyLoader.class);

        final TypeLiteral<Cache<Account.Id, AccountExternalIdList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, AccountExternalIdList>>() {};
        core(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        final TypeLiteral<Cache<EmailWrapper, AccountExternalIdList>> byEmailType =
            new TypeLiteral<Cache<EmailWrapper, AccountExternalIdList>>() {};
        core(byEmailType, BY_EMAIL).populateWith(ByEmailLoader.class);

        bind(AccountExternalIdCacheImpl.class);
        bind(AccountExternalIdCache.class).to(AccountExternalIdCacheImpl.class);
      }
    };
  }

  private final Cache<AccountExternalId.Key, AccountExternalId> byKey;
  private final Cache<Account.Id, AccountExternalIdList> byAccountId;
  private final Cache<EmailWrapper, AccountExternalIdList> byEmail;

  @Inject
  AccountExternalIdCacheImpl(
      @Named(BY_KEY) Cache<AccountExternalId.Key, AccountExternalId> byKey,
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, AccountExternalIdList> byAccountId,
      @Named(BY_EMAIL) Cache<EmailWrapper, AccountExternalIdList> byEmail) {
    this.byKey = byKey;
    this.byAccountId = byAccountId;
    this.byEmail = byEmail;
  }

  @Override
  public AccountExternalId get(AccountExternalId.Key key) {
    return byKey.get(key);
  }

  @Override
  public List<AccountExternalId> byAccount(Account.Id id) {
    return byAccountId.get(id).list;
  }

  @Override
  public List<AccountExternalId> byAccountEmail(Account.Id id, String email) {
    List<AccountExternalId> accIds = byAccountId.get(id).list;
    ArrayList<AccountExternalId> out = new ArrayList<AccountExternalId>(accIds.size());
    for (AccountExternalId extId : accIds){
      if(extId.getEmailAddress().equals(email)){
        out.add(extId);
      }
    }
    return out;
  }

  @Override
  public List<AccountExternalId> byEmailAddress(String email) {
    return byEmail.get(new EmailWrapper(email)).list;
  }

  @Override
  public void evict(AccountExternalId id) {
    byKey.remove(id.getKey());
    byAccountId.remove(id.getAccountId());
    byEmail.remove(new EmailWrapper(id.getEmailAddress()));
  }

  static class ByKeyLoader extends
      EntryCreator<AccountExternalId.Key, AccountExternalId> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByKeyLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountExternalId createEntry(AccountExternalId.Key key)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        return db.accountExternalIds().get(key);
      } finally {
        db.close();
      }
    }
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, AccountExternalIdList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountExternalIdList createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountExternalIdList(db.accountExternalIds().byAccount(id)
            .toList());
      } finally {
        db.close();
      }
    }
  }

  static class ByEmailLoader extends
      EntryCreator<EmailWrapper, AccountExternalIdList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByEmailLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountExternalIdList createEntry(EmailWrapper email)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountExternalIdList(db.accountExternalIds()
            .byEmailAddress(email.email).toList());
      } finally {
        db.close();
      }
    }
  }
}
