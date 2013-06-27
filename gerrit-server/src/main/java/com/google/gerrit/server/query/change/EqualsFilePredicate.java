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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;

class EqualsFilePredicate extends IndexPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final PatchListCache cache;
  private final String value;

  EqualsFilePredicate(Provider<ReviewDb> db, PatchListCache plc, String value) {
    super(ChangeField.FILE, value);
    this.db = db;
    this.cache = plc;
    this.value = value;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    List<String> files = object.currentFilePaths(db, cache);
    if (files != null) {
      return Collections.binarySearch(files, value) >= 0;
    } else {
      // The ChangeData can't do expensive lookups right now. Bypass
      // them and include the result anyway. We might be able to do
      // a narrow later on to a smaller set.
      //
      return true;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
