package com.google.gerrit.server;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.reviewdb.StarredChange.Key;
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
public class StarredChangesCacheImpl implements StarredChangesCache {
  private static final String BY_KEY = "starred_change_key";
  private static final String BY_ACCOUNT_ID = "starred_change_accountId";
  private static final String BY_CHANGE_ID = "starred_change_changeId";

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
        final TypeLiteral<Cache<StarredChange.Key, StarredChange>> byKeyType =
            new TypeLiteral<Cache<StarredChange.Key, StarredChange>>() {};
        core(byKeyType, BY_KEY).populateWith(ByKeyLoader.class);

        final TypeLiteral<Cache<Account.Id, StarredChangeList>> byAccountIdType =
            new TypeLiteral<Cache<Account.Id, StarredChangeList>>() {};
        core(byAccountIdType, BY_ACCOUNT_ID).populateWith(
            ByAccountIdLoader.class);

        final TypeLiteral<Cache<Change.Id, StarredChangeList>> byChangeIdType =
            new TypeLiteral<Cache<Change.Id, StarredChangeList>>() {};
        core(byChangeIdType, BY_CHANGE_ID).populateWith(ByChangeIdLoader.class);

        bind(StarredChangesCacheImpl.class);
        bind(StarredChangesCache.class).to(StarredChangesCacheImpl.class);
      }
    };
  }

  private final Cache<StarredChange.Key, StarredChange> byKey;
  private final Cache<Account.Id, StarredChangeList> byAccountId;
  private final Cache<Change.Id, StarredChangeList> byChangeId;

  @Inject
  StarredChangesCacheImpl(
      @Named(BY_KEY) Cache<StarredChange.Key, StarredChange> byKey,
      @Named(BY_ACCOUNT_ID) Cache<Account.Id, StarredChangeList> byAccountId,
      @Named(BY_CHANGE_ID) Cache<Change.Id, StarredChangeList> byChangeId) {
    this.byKey = byKey;
    this.byAccountId = byAccountId;
    this.byChangeId = byChangeId;
  }

  @Override
  public List<StarredChange> byAccount(Account.Id id) {
    return byAccountId.get(id).list;
  }

  @Override
  public List<StarredChange> byChange(Change.Id id) {
    return byChangeId.get(id).list;
  }

  @Override
  public StarredChange get(Key key) {
    return byKey.get(key);
  }

  @Override
  public void evict(StarredChange.Key key) {
    byKey.remove(key);
    byAccountId.remove(key.getParentKey());
    byChangeId.remove(key.getChangeId());
  }

  static class ByKeyLoader extends
      EntryCreator<StarredChange.Key, StarredChange> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByKeyLoader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public StarredChange createEntry(Key key) throws Exception {
      return schema.open().starredChanges().get(key);
    }
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
