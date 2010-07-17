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

import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;

import java.util.ArrayList;
import java.util.Collection;

class AndSource extends AndPredicate<ChangeData> implements ChangeDataSource {
  AndSource(final Collection<? extends Predicate<ChangeData>> that) {
    super(that);
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    // TODO(spearce) We should select the lowest cardinality operator.
    //
    ChangeDataSource source = null;
    for (Predicate<ChangeData> p : getChildren()) {
      if (p instanceof ChangeDataSource) {
        source = (ChangeDataSource) p;
        break;
      }
    }
    if (source == null) {
      throw new OrmException("Query expression " + this + " not valid");
    }

    // TODO(spearce) This probably should be more lazy.
    //
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    for (ChangeData data : source.read()) {
      if (match(data)) {
        r.add(data);
      }
    }
    return new ListResultSet<ChangeData>(r);
  }
}
