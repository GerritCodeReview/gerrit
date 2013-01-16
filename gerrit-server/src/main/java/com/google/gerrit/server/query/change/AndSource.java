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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class AndSource extends AndPredicate<ChangeData> implements ChangeDataSource {
  private static final Comparator<Predicate<ChangeData>> CMP =
      new Comparator<Predicate<ChangeData>>() {
        @Override
        public int compare(Predicate<ChangeData> a, Predicate<ChangeData> b) {
          int ai = a instanceof ChangeDataSource ? 0 : 1;
          int bi = b instanceof ChangeDataSource ? 0 : 1;
          int cmp = ai - bi;

          if (cmp == 0) {
            cmp = a.getCost() - b.getCost();
          }

          if (cmp == 0 //
              && a instanceof ChangeDataSource //
              && b instanceof ChangeDataSource) {
            ChangeDataSource as = (ChangeDataSource) a;
            ChangeDataSource bs = (ChangeDataSource) b;
            cmp = as.getCardinality() - bs.getCardinality();
          }

          if (cmp == 0 //
              && a instanceof ChangeDataSource //
              && b instanceof ChangeDataSource) {
            ai = ((ChangeDataSource) a).hasChange() ? 0 : 1;
            bi = ((ChangeDataSource) b).hasChange() ? 0 : 1;
            cmp = ai - bi;
          }

          return cmp;
        }
      };

  private static List<Predicate<ChangeData>> sort(
      Collection<? extends Predicate<ChangeData>> that) {
    ArrayList<Predicate<ChangeData>> r =
        new ArrayList<Predicate<ChangeData>>(that);
    Collections.sort(r, CMP);
    return r;
  }

  private final Provider<ReviewDb> db;
  private int cardinality = -1;

  AndSource(Provider<ReviewDb> db, Collection<? extends Predicate<ChangeData>> that) {
    super(sort(that));
    this.db = db;
  }

  @Override
  public boolean hasChange() {
    ChangeDataSource source = source();
    return source != null && source.hasChange();
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    ChangeDataSource source = source();
    if (source == null) {
      throw new OrmException("No ChangeDataSource: " + this);
    }

    ResultSet<ChangeData> scanner = source.read();
    List<ChangeData> r = Lists.newArrayList();
    ChangeData last = null;
    boolean skipped = false;
    for (;;) {
      List<ChangeData> buffer = buffer(scanner);
      if (buffer.isEmpty()) {
        break;
      }
      if (!source.hasChange()) {
        ChangeData.ensureChangeLoaded(db, buffer);
      }
      for (ChangeData data : buffer) {
        if (match(data)) {
          r.add(data);
        } else {
          skipped = true;
        }
        last = data;
      }
    }

    if (skipped && last != null && source instanceof Paginated) {
      // If our source is a paginated source and we skipped at
      // least one of its results, we may not have filled the full
      // limit the caller wants.  Restart the source and continue.
      //
      Paginated p = (Paginated) source;
      while (skipped && r.size() < p.limit()) {
        ChangeData lastBeforeRestart = last;
        skipped = false;
        last = null;
        scanner = p.restart(lastBeforeRestart);
        for (;;) {
          List<ChangeData> buffer = buffer(scanner);
          if (buffer.isEmpty()) {
            break;
          }
          if (!source.hasChange()) {
            ChangeData.ensureChangeLoaded(db, buffer);
          }
          for (ChangeData data : buffer) {
            if (match(data)) {
              r.add(data);
            } else {
              skipped = true;
            }
            last = data;
          }
        }
      }
    }

    return new ListResultSet<ChangeData>(r);
  }

  private static final int BUFFER_SIZE = 50;

  private static List<ChangeData> buffer(ResultSet<ChangeData> scanner) {
    List<ChangeData> buffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);
    for (ChangeData data : scanner) {
      buffer.add(data);
      if (buffer.size() == BUFFER_SIZE) {
        break;
      }
    }
    return buffer;
  }

  private ChangeDataSource source() {
    for (Predicate<ChangeData> p : getChildren()) {
      if (p instanceof ChangeDataSource) {
        return (ChangeDataSource) p;
      }
    }
    return null;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = Integer.MAX_VALUE;
      for (Predicate<ChangeData> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          int c = ((ChangeDataSource) p).getCardinality();
          cardinality = Math.min(cardinality, c);
        }
      }
    }
    return cardinality;
  }
}
