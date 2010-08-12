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

package com.google.gerrit.server;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.util.CompoundFuture;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.List;

@Singleton
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final String BY_ACCOUNT_ID = "starred_user";
  private static final String BY_CHANGE_ID = "starred_change";

  protected static class StarredChangeList {
    @Column(id = 1)
    protected List<StarredChange> list;

    protected StarredChangeList() {
    }

    public StarredChangeList(List<StarredChange> list) {
      this.list = list;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Account.Id, StarredChangeList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, StarredChangeList>>() {};
        cache(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        final TypeLiteral<Cache<Change.Id, StarredChangeList>> byChangeIdType =
            new TypeLiteral<Cache<Change.Id, StarredChangeList>>() {};
        cache(byChangeIdType, BY_CHANGE_ID).populateWith(ByChangeIdLoader.class);

        bind(StarredChangesCacheImpl.class);
        bind(StarredChangesCache.class).to(StarredChangesCacheImpl.class);
      }
    };
  }

  private static final Function<StarredChangeList, List<StarredChange>> unpack =
      new Function<StarredChangeList, List<StarredChange>>() {
        @Override
        public List<StarredChange> apply(StarredChangeList in) {
          return in.list;
        }
      };

  private final Cache<Account.Id, StarredChangeList> byAccountId;
  private final Cache<Change.Id, StarredChangeList> byChangeId;

  @Inject
  StarredChangesCacheImpl(
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, StarredChangeList> byAccountId,
      @Named(BY_CHANGE_ID) Cache<Change.Id, StarredChangeList> byChangeId) {
    this.byAccountId = byAccountId;
    this.byChangeId = byChangeId;
  }

  @Override
  public ListenableFuture<List<StarredChange>> byAccount(Account.Id id) {
    return Futures.compose(byAccountId.get(id), unpack);
  }

  @Override
  public ListenableFuture<List<StarredChange>> byChange(Change.Id id) {
    return Futures.compose(byChangeId.get(id), unpack);
  }

  @Override
  public ListenableFuture<Void> evictAsync(StarredChange.Key key) {
    return CompoundFuture.wrap(byAccountId.removeAsync(key.getParentKey()),
        byChangeId.removeAsync(key.getChangeId()));
  }

  static class ByAccountIdLoader extends
      EntryCreator<Account.Id, StarredChangeList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByAccountIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public StarredChangeList createEntry(Account.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new StarredChangeList(db.starredChanges().byAccount(id).toList());
      } finally {
        db.close();
      }
    }
  }

  static class ByChangeIdLoader extends
      EntryCreator<Change.Id, StarredChangeList> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByChangeIdLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public StarredChangeList createEntry(Change.Id id) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return new StarredChangeList(db.starredChanges().byChange(id).toList());
      } finally {
        db.close();
      }
    }
  }
}
