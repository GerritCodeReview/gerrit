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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper combining an {@link IndexPredicate} together with a
 * {@link ChangeDataSource} that returns matching results from the index.
 * <p>
 * Appropriate to return as the rootmost predicate that can be processed using
 * the secondary index; such predicates must also implement
 * {@link ChangeDataSource} to be chosen by the query processor.
 */
public class PredicateWrapper extends Predicate<ChangeData> implements
    ChangeDataSource {
  private final Predicate<ChangeData> pred;
  private final List<ChangeDataSource> sources;

  public PredicateWrapper(Predicate<ChangeData> pred, ChangeIndex index)
      throws QueryParseException {
    this(pred, ImmutableList.of(index));
  }

  public PredicateWrapper(Predicate<ChangeData> pred,
      Collection<ChangeIndex> indexes) throws QueryParseException {
    this.pred = pred;
    sources = Lists.newArrayListWithCapacity(indexes.size());
    for (ChangeIndex index : indexes) {
      sources.add(index.getSource(pred));
    }
  }

  @Override
  public int getCardinality() {
    int n = 0;
    for (ChangeDataSource source : sources) {
      n += source.getCardinality();
    }
    return n;
  }

  @Override
  public boolean hasChange() {
    for (ChangeDataSource source : sources) {
      if (!source.hasChange()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    final List<ResultSet<ChangeData>> results =
        Lists.newArrayListWithCapacity(sources.size());
    for (ChangeDataSource source : sources) {
      results.add(source.read());
    }
    return new ResultSet<ChangeData>() {
      @Override
      public Iterator<ChangeData> iterator() {
        // TODO(dborowitz): May return duplicates since moving a document
        // between indexes is not atomic.
        return Iterables.concat(results).iterator();
      }

      @Override
      public List<ChangeData> toList() {
        return Collections.unmodifiableList(Lists.newArrayList(iterator()));
      }

      @Override
      public void close() {
        for (ResultSet<ChangeData> rs : results) {
          rs.close();
        }
      }
    };
  }

  @Override
  public Predicate<ChangeData> copy(
      Collection<? extends Predicate<ChangeData>> children) {
    return this;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return pred.match(cd);
  }

  @Override
  public int getCost() {
    return pred.getCost();
  }

  @Override
  public int hashCode() {
    return pred.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && getClass() == other.getClass()
        && pred.equals(((PredicateWrapper) other).pred);
  }

  @Override
  public String toString() {
    return "index(" + pred + ")";
  }
}
