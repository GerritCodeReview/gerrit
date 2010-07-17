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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

class OrSource extends OrPredicate<ChangeData> implements ChangeDataSource {
  OrSource(final Collection<? extends Predicate<ChangeData>> that) {
    super(that);
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    // TODO(spearce) This probably should be more lazy.
    //
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    HashSet<Change.Id> have = new HashSet<Change.Id>();
    for (Predicate<ChangeData> p : getChildren()) {
      if (p instanceof ChangeDataSource) {
        for (ChangeData cd : ((ChangeDataSource) p).read()) {
          if (have.add(cd.getId())) {
            r.add(cd);
          }
        }
      } else {
        throw new OrmException("Query operator (" + p + ") not valid in OR");
      }
    }
    return new ListResultSet<ChangeData>(r);
  }
}
