// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.index.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake secondary index implementation for usage in tests. All values are kept in-memory.
 *
 * <p>This class is thread-safe.
 */
public abstract class AbstractFakeIndex<K, V> implements Index<K, V> {
  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final String indexName;
  private final Map<K, Object> indexedDocuments;

  AbstractFakeIndex(Schema<V> schema, SitePaths sitePaths, String indexName) {
    this.schema = schema;
    this.sitePaths = sitePaths;
    this.indexName = indexName;
    this.indexedDocuments = new HashMap<>();
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    // No-op
  }

  @Override
  public void replace(V doc) {
    synchronized (indexedDocuments) {
      indexedDocuments.put(keyFor(doc), docFor(doc));
    }
  }

  @Override
  public void delete(K key) {
    synchronized (indexedDocuments) {
      indexedDocuments.remove(key);
    }
  }

  @Override
  public void deleteAll() {
    synchronized (indexedDocuments) {
      indexedDocuments.clear();
    }
  }

  @Override
  public DataSource<V> getSource(Predicate<V> p, QueryOptions opts) {
    List<V> results;
    synchronized (indexedDocuments) {
      results =
          indexedDocuments.values().stream()
              .map(doc -> valueFor(doc))
              .filter(doc -> p.asMatchable().match(doc))
              .sorted(sortingComparator())
              .skip(opts.start())
              .limit(opts.limit())
              .collect(toImmutableList());
    }
    return new DataSource<V>() {
      @Override
      public int getCardinality() {
        return results.size();
      }

      @Override
      public ResultSet<V> read() {
        return new ListResultSet(results);
      }

      @Override
      public ResultSet<FieldBundle> readRaw() {
        ImmutableList.Builder<FieldBundle> fieldBundles = ImmutableList.builder();
        for (V result : results) {
          ImmutableListMultimap.Builder<String, Object> fields = ImmutableListMultimap.builder();
          for (FieldDef<V, ?> field : getSchema().getFields().values()) {
            if (field.get(result) == null) {
              continue;
            }
            if (field.isRepeatable()) {
              fields.putAll(field.getName(), (Iterable<?>) field.get(result));
            } else {
              fields.put(field.getName(), field.get(result));
            }
          }
          fieldBundles.add(new FieldBundle(fields.build()));
        }
        return new ListResultSet(fieldBundles.build());
      }
    };
  }

  @Override
  public void markReady(boolean ready) {
    IndexUtils.setReady(sitePaths, indexName, schema.getVersion(), ready);
  }

  protected abstract K keyFor(V value);

  protected Object docFor(V value) {
    return value;
  }

  @SuppressWarnings("unchecked")
  protected V valueFor(Object value) {
    return (V) value;
  }

  protected abstract Comparator<V> sortingComparator();

  public static class FakeChangeIndex extends AbstractFakeIndex<Change.Id, ChangeData>
      implements ChangeIndex {
    private final ChangeData.Factory changeDataFactory;

    @Inject
    FakeChangeIndex(
        SitePaths sitePaths,
        ChangeData.Factory changeDataFactory,
        @Assisted Schema<ChangeData> schema) {
      super(schema, sitePaths, "changes");
      this.changeDataFactory = changeDataFactory;
    }

    @Override
    protected Change.Id keyFor(ChangeData value) {
      return value.getId();
    }

    @Override
    protected Comparator<ChangeData> sortingComparator() {
      Comparator<ChangeData> lastUpdated =
          Comparator.comparing(cd -> cd.change().getLastUpdatedOn());
      Comparator<ChangeData> merged =
          Comparator.comparing(cd -> cd.getMergedOn().orElse(new Timestamp(0)));
      Comparator<ChangeData> id = Comparator.comparing(cd -> cd.getId().get());
      return lastUpdated.thenComparing(merged).thenComparing(id).reversed();
    }

    @Override
    protected Object docFor(ChangeData value) {
      ImmutableMap.Builder<String, Object> doc = ImmutableMap.builder();
      for (FieldDef<ChangeData, ?> field : getSchema().getFields().values()) {
        Object docifiedValue = field.get(value);
        if (docifiedValue != null) {
          doc.put(field.getName(), field.get(value));
        }
      }
      return doc.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ChangeData valueFor(Object value) {
      Map<String, Object> doc = (Map<String, Object>) value;
      ChangeData cd =
          changeDataFactory.create(
              Project.nameKey((String) doc.get(ChangeField.PROJECT.getName())),
              Change.id(Integer.valueOf((String) doc.get(ChangeField.LEGACY_ID_STR.getName()))));
      for (FieldDef<ChangeData, ?> field : getSchema().getFields().values()) {
        Object val = doc.get(field.getName());
        field.setIfPossible(
            cd,
            new FieldDef.IndexedDocument() {
              @Override
              public String stringValue() {
                return (String) val;
              }

              @Override
              public Iterable<String> stringValues() {
                return (Iterable<String>) val;
              }

              @Override
              public Integer integerValue() {
                return (Integer) val;
              }

              @Override
              public Iterable<Integer> integerValues() {
                return (Iterable<Integer>) val;
              }

              @Override
              public Long longValue() {
                return (Long) val;
              }

              @Override
              public Timestamp timestampValue() {
                return (Timestamp) val;
              }

              @Override
              public Iterable<Long> longValues() {
                return (Iterable<Long>) val;
              }

              @Override
              public byte[] rawValue() {
                return (byte[]) val;
              }

              @Override
              public Iterable<byte[]> rawValues() {
                return (Iterable<byte[]>) val;
              }
            });
      }
      return cd;
    }
  }

  public static class FakeAccountIndex extends AbstractFakeIndex<Account.Id, AccountState>
      implements AccountIndex {

    @Inject
    FakeAccountIndex(SitePaths sitePaths, @Assisted Schema<AccountState> schema) {
      super(schema, sitePaths, "accounts");
    }

    @Override
    protected Account.Id keyFor(AccountState value) {
      return value.account().id();
    }

    @Override
    protected Comparator<AccountState> sortingComparator() {
      return Comparator.comparing(a -> a.account().id().get());
    }
  }

  public static class FakeGroupIndex extends AbstractFakeIndex<AccountGroup.UUID, InternalGroup>
      implements GroupIndex {
    @Inject
    FakeGroupIndex(SitePaths sitePaths, @Assisted Schema<InternalGroup> schema) {
      super(schema, sitePaths, "groups");
    }

    @Override
    protected AccountGroup.UUID keyFor(InternalGroup value) {
      return value.getGroupUUID();
    }

    @Override
    protected Comparator<InternalGroup> sortingComparator() {
      return Comparator.comparing(g -> g.getId().get());
    }
  }

  public static class FakeProjectIndex extends AbstractFakeIndex<Project.NameKey, ProjectData>
      implements ProjectIndex {
    @Inject
    FakeProjectIndex(SitePaths sitePaths, @Assisted Schema<ProjectData> schema) {
      super(schema, sitePaths, "projects");
    }

    @Override
    protected Project.NameKey keyFor(ProjectData value) {
      return value.getProject().getNameKey();
    }

    @Override
    protected Comparator<ProjectData> sortingComparator() {
      return Comparator.comparing(p -> p.getProject().getName());
    }
  }
}
