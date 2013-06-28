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
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.List;

class AnyFilePredicate extends AndPredicate<ChangeData> {
  private final String value;

  AnyFilePredicate(Provider<ReviewDb> db, PatchListCache plc, Change change)
      throws OrmException {
    super(predicates(db, plc, change));
    this.value = change.getId().toString();
  }

  private static List<Predicate<ChangeData>> predicates(Provider<ReviewDb> db,
      PatchListCache plc, Change change) throws OrmException {
    List<Predicate<ChangeData>> r = Lists.newArrayList();
    for (String file : (new ChangeData(change)).currentFilePaths(db, plc)) {
      r.add(new EqualsFilePredicate(db, plc, file));
    }

    List<Predicate<ChangeData>> r2 = Lists.newArrayList();
    r2.add(not(new LegacyChangeIdPredicate(db, change.getId())));
    r2.add(or(r));
    return r2;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_FILE + ":any=" + value;
  }
}
