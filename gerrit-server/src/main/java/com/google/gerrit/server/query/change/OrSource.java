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
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrSource extends OrPredicate<ChangeData> implements ChangeDataSource {
  private int cardinality = -1;

  public OrSource(Collection<? extends Predicate<ChangeData>> that) {
    super(that);
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    // TODO(spearce) This probably should be more lazy.
    //
    List<ChangeData> r = new ArrayList<>();
    Set<Change.Id> have = new HashSet<>();
    for (Predicate<ChangeData> p : getChildren()) {
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
    return new ListResultSet<>(r);
  }

  @Override
  public boolean hasChange() {
    for (Predicate<ChangeData> p : getChildren()) {
      if (!(p instanceof ChangeDataSource) || !((ChangeDataSource) p).hasChange()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = 0;
      for (Predicate<ChangeData> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          cardinality += ((ChangeDataSource) p).getCardinality();
        }
      }
    }
    return cardinality;
  }
}
