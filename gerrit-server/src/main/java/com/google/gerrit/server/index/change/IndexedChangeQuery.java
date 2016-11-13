// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.index.change.ChangeField.CHANGE;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.IndexedQuery;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Matchable;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper combining an {@link IndexPredicate} together with a {@link ChangeDataSource} that returns
 * matching results from the index.
 *
 * <p>Appropriate to return as the rootmost predicate that can be processed using the secondary
 * index; such predicates must also implement {@link ChangeDataSource} to be chosen by the query
 * processor.
 */
public class IndexedChangeQuery extends IndexedQuery<Change.Id, ChangeData>
    implements ChangeDataSource, Matchable<ChangeData> {
  public static QueryOptions oneResult() {
    return createOptions(IndexConfig.createDefault(), 0, 1, ImmutableSet.<String>of());
  }

  public static QueryOptions createOptions(
      IndexConfig config, int start, int limit, Set<String> fields) {
    // Always include project since it is needed to load the change from NoteDb.
    if (!fields.contains(CHANGE.getName()) && !fields.contains(PROJECT.getName())) {
      fields = new HashSet<>(fields);
      fields.add(PROJECT.getName());
    }
    return QueryOptions.create(config, start, limit, fields);
  }

  @VisibleForTesting
  static QueryOptions convertOptions(QueryOptions opts) {
    opts = opts.convertForBackend();
    return IndexedChangeQuery.createOptions(
        opts.config(), opts.start(), opts.limit(), opts.fields());
  }

  private final Map<ChangeData, DataSource<ChangeData>> fromSource;

  public IndexedChangeQuery(ChangeIndex index, Predicate<ChangeData> pred, QueryOptions opts)
      throws QueryParseException {
    super(index, pred, convertOptions(opts));
    this.fromSource = new HashMap<>();
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    final DataSource<ChangeData> currSource = source;
    final ResultSet<ChangeData> rs = currSource.read();

    return new ResultSet<ChangeData>() {
      @Override
      public Iterator<ChangeData> iterator() {
        return Iterables.transform(
                rs,
                cd -> {
                  fromSource.put(cd, currSource);
                  return cd;
                })
            .iterator();
      }

      @Override
      public List<ChangeData> toList() {
        List<ChangeData> r = rs.toList();
        for (ChangeData cd : r) {
          fromSource.put(cd, currSource);
        }
        return r;
      }

      @Override
      public void close() {
        rs.close();
      }
    };
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    if (source != null && fromSource.get(cd) == source) {
      return true;
    }

    Predicate<ChangeData> pred = getChild(0);
    checkState(
        pred.isMatchable(),
        "match invoked, but child predicate %s " + "doesn't implement %s",
        pred,
        Matchable.class.getName());
    return pred.asMatchable().match(cd);
  }

  @Override
  public int getCost() {
    // Index queries are assumed to be cheaper than any other type of query, so
    // so try to make sure they get picked. Note that pred's cost may be higher
    // because it doesn't know whether it's being used in an index query or not.
    return 1;
  }

  @Override
  public boolean hasChange() {
    return index.getSchema().hasField(ChangeField.CHANGE);
  }
}
