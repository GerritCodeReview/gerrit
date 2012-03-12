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

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class AndSource extends AndPredicate<ChangeData, PatchSet>
    implements ChangeDataSource {
  private static final Comparator<Predicate<ChangeData, PatchSet>> CMP =
      new Comparator<Predicate<ChangeData, PatchSet>>() {
        @Override
        public int compare(Predicate<ChangeData, PatchSet> a,
                           Predicate<ChangeData, PatchSet> b) {
          int ai = a instanceof ChangeDataSource ? 0 : 1;
          int bi = b instanceof ChangeDataSource ? 0 : 1;
          int cmp = ai - bi;

          if (cmp == 0 //
              && a instanceof ChangeDataSource //
              && b instanceof ChangeDataSource) {
            ai = ((ChangeDataSource) a).hasChange() ? 0 : 1;
            bi = ((ChangeDataSource) b).hasChange() ? 0 : 1;
            cmp = ai - bi;
          }

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

          return cmp;
        }
      };

  private static List<Predicate<ChangeData, PatchSet>> sort(
      Collection<? extends Predicate<ChangeData, PatchSet>> that) {
    ArrayList<Predicate<ChangeData, PatchSet>> r =
        new ArrayList<Predicate<ChangeData, PatchSet>>(that);
    Collections.sort(r, CMP);
    return r;
  }

  private int cardinality = -1;

  AndSource(final Collection<? extends Predicate<ChangeData, PatchSet>> that) {
    super(sort(that));
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

    // TODO(spearce) This probably should be more lazy.
    //
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    ChangeData last = null;
    boolean skipped = false;
    for (ChangeData data : source.read()) {
      if (match(data)) {
        r.add(data);
      } else {
        skipped = true;
      }
      last = data;
    }

    if (skipped && last != null && source instanceof Paginated) {
      // If we our source is a paginated source and we skipped at
      // least one of its results, we may not have filled the full
      // limit the caller wants.  Restart the source and continue.
      //
      Paginated p = (Paginated) source;
      while (skipped && r.size() < p.limit()) {
        ChangeData lastBeforeRestart = last;
        skipped = false;
        last = null;
        for (ChangeData data : p.restart(lastBeforeRestart)) {
          if (match(data)) {
            r.add(data);
          } else {
            skipped = true;
          }
          last = data;
        }
      }
    }

    return new ListResultSet<ChangeData>(r);
  }

  private ChangeDataSource source() {
    for (Predicate<ChangeData, PatchSet> p : getChildren()) {
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
      for (Predicate<ChangeData, PatchSet> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          int c = ((ChangeDataSource) p).getCardinality();
          cardinality = Math.min(cardinality, c);
        }
      }
    }
    return cardinality;
  }
}
