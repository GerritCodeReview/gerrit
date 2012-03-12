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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

class OrSource extends OrPredicate<ChangeData, PatchSet>
    implements ChangeDataSource {
  private int cardinality = -1;

  OrSource(final Collection<? extends Predicate<ChangeData, PatchSet>> that) {
    super(that);
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    // TODO(spearce) This probably should be more lazy.
    //
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    HashSet<Change.Id> have = new HashSet<Change.Id>();
    for (Predicate<ChangeData, PatchSet> p : getChildren()) {
      if (p instanceof ChangeDataSource) {
        for (ChangeData cd : ((ChangeDataSource) p).read()) {
          if (have.add(cd.getId())) {
            r.add(cd);
          }
        }
      } else {
        throw new OrmException("No ChangeDataSource: " + p);
      }
    }
    return new ListResultSet<ChangeData>(r);
  }

  @Override
  public boolean hasChange() {
    for (Predicate<ChangeData, PatchSet> p : getChildren()) {
      if (!(p instanceof ChangeDataSource)
          || !((ChangeDataSource) p).hasChange()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = 0;
      for (Predicate<ChangeData, PatchSet> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          cardinality += ((ChangeDataSource) p).getCardinality();
        }
      }
    }
    return cardinality;
  }
}
