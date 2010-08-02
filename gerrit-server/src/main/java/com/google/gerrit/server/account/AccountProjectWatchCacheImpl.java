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
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Project;
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
public class AccountProjectWatchCacheImpl implements AccountProjectWatchCache {
  private static final String BY_ACCOUNT_ID = "apw_account";
  private static final String BY_PROJECT_NAME = "apw_project";

  protected static class AccountProjectWatchList {
    @Column(id = 1)
    protected List<AccountProjectWatch> list;

    protected AccountProjectWatchList() {
    }

    public AccountProjectWatchList(List<AccountProjectWatch> list) {
      this.list = list;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, AccountProjectWatchList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, AccountProjectWatchList>>() {};
        core(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        final TypeLiteral<Cache<Project.NameKey, AccountProjectWatchList>> byProjectNameType =
            new TypeLiteral<Cache<Project.NameKey, AccountProjectWatchList>>() {};
        core(byProjectNameType, BY_PROJECT_NAME).populateWith(
            ByProjectNameLoader.class);

        bind(AccountProjectWatchCacheImpl.class);
        bind(AccountProjectWatchCache.class).to(
            AccountProjectWatchCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Id, AccountProjectWatchList> byAccountId;
  private final Cache<Project.NameKey, AccountProjectWatchList> byProjectName;

  @Inject
  AccountProjectWatchCacheImpl(
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, AccountProjectWatchList> byAccountId,
      @Named(BY_PROJECT_NAME) Cache<Project.NameKey, AccountProjectWatchList> byProjectName) {
    this.byAccountId = byAccountId;
    this.byProjectName = byProjectName;
  }

  @Override
  public List<AccountProjectWatch> byAccount(Account.Id id) {
    return byAccountId.get(id).list;
  }

  @Override
  public List<AccountProjectWatch> byProject(Project.NameKey name) {
    return byProjectName.get(name).list;
  }

  @Override
  public void evict(AccountProjectWatch.Key key) {
    byAccountId.remove(key.getParentKey());
    byProjectName.remove(key.getProjectName());
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, AccountProjectWatchList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountProjectWatchList createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountProjectWatchList(db.accountProjectWatches()
            .byAccount(id).toList());
      } finally {
        db.close();
      }
    }
  }

  static class ByProjectNameLoader extends
      EntryCreator<Project.NameKey, AccountProjectWatchList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByProjectNameLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public AccountProjectWatchList createEntry(Project.NameKey name)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new AccountProjectWatchList(db.accountProjectWatches()
            .byProject(name).toList());
      } finally {
        db.close();
      }
    }
  }
}
