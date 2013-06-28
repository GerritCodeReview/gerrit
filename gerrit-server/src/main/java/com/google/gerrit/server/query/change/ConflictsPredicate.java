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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.List;

class ConflictsPredicate extends OrPredicate<ChangeData> {
  private final String value;

  ConflictsPredicate(Provider<ReviewDb> db, PatchListCache plc, String value,
      List<Change> changes) throws OrmException {
    super(predicates(db, plc, changes));
    this.value = value;
  }

  private static List<Predicate<ChangeData>> predicates(Provider<ReviewDb> db,
      PatchListCache plc, List<Change> changes) throws OrmException {
    List<Predicate<ChangeData>> r = Lists.newArrayList();
    for (Change c : changes) {
      List<Predicate<ChangeData>> r2 = Lists.newArrayList();
      for (String file : (new ChangeData(c)).currentFilePaths(db, plc)) {
        r2.add(new EqualsFilePredicate(db, plc, file));
      }

      List<Predicate<ChangeData>> r3 = Lists.newArrayList();
      r3.add(not(new LegacyChangeIdPredicate(db, c.getId())));
      r3.add(or(r2));

      r.add(and(r3));
    }
    return r;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_CONFLICTS + ":" + value;
  }
}
