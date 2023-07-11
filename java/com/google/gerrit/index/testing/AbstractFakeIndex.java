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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;

/**
 * Fake secondary index implementation for usage in tests. All values are kept in-memory.
 *
 * <p>This class is thread-safe.
 */
public abstract class AbstractFakeIndex<K, V, D> implements Index<K, V> {
  private final Schema<V> schema;
  /**
   * SitePaths (config files) are used to signal that an index is ready. This implementation is
   * consistent with other index backends.
   */
  private final SitePaths sitePaths;

  private final String indexName;
  private final Map<K, D> indexedDocuments;
  private int queryCount;
  private List<Integer> resultsSizes;

  AbstractFakeIndex(Schema<V> schema, SitePaths sitePaths, String indexName) {
    this.schema = schema;
    this.sitePaths = sitePaths;
    this.indexName = indexName;
    this.indexedDocuments = new HashMap<>();
    this.queryCount = 0;
    this.resultsSizes = new ArrayList<Integer>();
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

  public int getQueryCount() {
    return queryCount;
  }

  @VisibleForTesting
  public void resetQueryCount() {
    queryCount = 0;
  }

  @VisibleForTesting
  public List<Integer> getResultsSizes() {
    return resultsSizes;
  }

  @Override
  public DataSource<V> getSource(Predicate<V> p, QueryOptions opts) {
    List<V> results;
    synchronized (indexedDocuments) {
      Stream<V> valueStream =
          indexedDocuments.values().stream()
              .map(doc -> valueFor(doc))
              .filter(doc -> p.asMatchable().match(doc))
              .sorted(sortingComparator());
      if (opts.searchAfter() != null) {
        ImmutableList<V> valueList = valueStream.collect(toImmutableList());
        int fromIndex =
            IntStream.range(0, valueList.size())
                    .filter(i -> keyFor(valueList.get(i)).equals(opts.searchAfter()))
                    .findFirst()
                    .orElse(-1)
                + 1;
        int toIndex = Math.min(fromIndex + opts.pageSize(), valueList.size());
        results = valueList.subList(fromIndex, toIndex);
      } else {
        results = valueStream.skip(opts.start()).limit(opts.pageSize()).collect(toImmutableList());
      }
      queryCount++;
      resultsSizes.add(results.size());
    }
    return new DataSource<>() {
      @Override
      public int getCardinality() {
        return results.size();
      }

      @Override
      public ResultSet<V> read() {
        return new ListResultSet<>(results) {
          @Override
          public Object searchAfter() {
            @Nullable V last = Iterables.getLast(results, null);
            return last != null ? keyFor(last) : null;
          }
        };
      }

      @Override
      public ResultSet<FieldBundle> readRaw() {
        ImmutableList.Builder<FieldBundle> fieldBundles = ImmutableList.builder();
        K searchAfter = null;
        for (V result : results) {
          ImmutableListMultimap.Builder<String, Object> fields = ImmutableListMultimap.builder();
          for (SchemaField<V, ?> field : getSchema().getSchemaFields().values()) {
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
          searchAfter = keyFor(result);
        }
        ImmutableList<FieldBundle> resultSet = fieldBundles.build();
        K finalSearchAfter = searchAfter;
        return new ListResultSet<>(resultSet) {
          @Override
          public Object searchAfter() {
            return finalSearchAfter;
          }
        };
      }
    };
  }

  @Override
  public void markReady(boolean ready) {
    IndexUtils.setReady(sitePaths, indexName, schema.getVersion(), ready);
  }

  /** Method to get a key from a document. */
  protected abstract K keyFor(V doc);

  /** Method to get a document the index should hold on to from a Gerrit Java data type. */
  protected abstract D docFor(V value);

  /** Method to a Gerrit Java data type from a document that the index was holding on to. */
  protected abstract V valueFor(D doc);

  /** Comparator representing the default search order. */
  protected abstract Comparator<V> sortingComparator();

  /**
   * Fake implementation of {@link ChangeIndex} where all filtering happens in-memory.
   *
   * <p>This index is special in that ChangeData is a mutable object. Therefore we can't just hold
   * onto the object that the caller wanted us to index. We also can't just create a new ChangeData
   * from scratch because there are tests that assert that certain computations (e.g. diffs) are
   * only done once. So we do what the prod indices do: We read and write fields using FieldDef.
   */
  public static class FakeChangeIndex
      extends AbstractFakeIndex<Change.Id, ChangeData, Map<String, Object>> implements ChangeIndex {
    private final ChangeData.Factory changeDataFactory;
    private final boolean skipMergable;

    @Inject
    FakeChangeIndex(
        SitePaths sitePaths,
        ChangeData.Factory changeDataFactory,
        @Assisted Schema<ChangeData> schema,
        @GerritServerConfig Config cfg) {
      super(schema, sitePaths, "changes");
      this.changeDataFactory = changeDataFactory;
      this.skipMergable = !MergeabilityComputationBehavior.fromConfig(cfg).includeInIndex();
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
          Comparator.comparing(cd -> cd.getMergedOn().orElse(Instant.EPOCH));
      Comparator<ChangeData> id = Comparator.comparing(cd -> cd.getId().get());
      return lastUpdated.thenComparing(merged).thenComparing(id).reversed();
    }

    @Override
    protected Map<String, Object> docFor(ChangeData value) {
      ImmutableMap.Builder<String, Object> doc = ImmutableMap.builder();
      for (SchemaField<ChangeData, ?> field : getSchema().getSchemaFields().values()) {
        if (ChangeField.MERGEABLE.getName().equals(field.getName()) && skipMergable) {
          continue;
        }
        Object docifiedValue = field.get(value);
        if (docifiedValue != null) {
          doc.put(field.getName(), field.get(value));
        }
      }
      return doc.build();
    }

    @Override
    protected ChangeData valueFor(Map<String, Object> doc) {
      ChangeData cd =
          changeDataFactory.create(
              Project.nameKey((String) doc.get(ChangeField.PROJECT.getName())),
              Change.id(Integer.valueOf((String) doc.get(ChangeField.LEGACY_ID_STR.getName()))));
      for (SchemaField<ChangeData, ?> field : getSchema().getSchemaFields().values()) {
        field.setIfPossible(cd, new FakeStoredValue(doc.get(field.getName())));
      }
      return cd;
    }

    @Override
    public void insert(ChangeData obj) {}
  }

  /** Fake implementation of {@link AccountIndex} where all filtering happens in-memory. */
  public static class FakeAccountIndex
      extends AbstractFakeIndex<Account.Id, AccountState, AccountState> implements AccountIndex {
    @Inject
    FakeAccountIndex(SitePaths sitePaths, @Assisted Schema<AccountState> schema) {
      super(schema, sitePaths, "accounts");
    }

    @Override
    protected Account.Id keyFor(AccountState value) {
      return value.account().id();
    }

    @Override
    protected AccountState docFor(AccountState value) {
      return value;
    }

    @Override
    protected AccountState valueFor(AccountState doc) {
      return doc;
    }

    @Override
    protected Comparator<AccountState> sortingComparator() {
      return Comparator.comparing(a -> a.account().id().get());
    }

    @Override
    public void insert(AccountState obj) {}
  }

  /** Fake implementation of {@link GroupIndex} where all filtering happens in-memory. */
  public static class FakeGroupIndex
      extends AbstractFakeIndex<AccountGroup.UUID, InternalGroup, InternalGroup>
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
    protected InternalGroup docFor(InternalGroup value) {
      return value;
    }

    @Override
    protected InternalGroup valueFor(InternalGroup doc) {
      return doc;
    }

    @Override
    protected Comparator<InternalGroup> sortingComparator() {
      return Comparator.comparing(g -> g.getId().get());
    }

    @Override
    public void insert(InternalGroup obj) {}
  }

  /** Fake implementation of {@link ProjectIndex} where all filtering happens in-memory. */
  public static class FakeProjectIndex
      extends AbstractFakeIndex<Project.NameKey, ProjectData, ProjectData> implements ProjectIndex {
    @Inject
    FakeProjectIndex(SitePaths sitePaths, @Assisted Schema<ProjectData> schema) {
      super(schema, sitePaths, "projects");
    }

    @Override
    protected Project.NameKey keyFor(ProjectData value) {
      return value.getProject().getNameKey();
    }

    @Override
    protected ProjectData docFor(ProjectData value) {
      return value;
    }

    @Override
    protected ProjectData valueFor(ProjectData doc) {
      return doc;
    }

    @Override
    protected Comparator<ProjectData> sortingComparator() {
      return Comparator.comparing(p -> p.getProject().getName());
    }

    @Override
    public void insert(ProjectData obj) {}
  }
}
