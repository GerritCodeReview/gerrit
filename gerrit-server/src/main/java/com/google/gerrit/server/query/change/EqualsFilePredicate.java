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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListLoader;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class EqualsFilePredicate extends IndexPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final PatchListLoader loader;
  private final PatchListCache cache;
  private final GitRepositoryManager mgr;
  private final String value;

  EqualsFilePredicate(Provider<ReviewDb> db, PatchListLoader pll,
      PatchListCache plc, GitRepositoryManager mgr, String value) {
    super(ChangeField.FILE, value);
    this.db = db;
    this.loader = pll;
    this.cache = plc;
    this.mgr = mgr;
    this.value = value;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    List<String> files = getFiles(object);
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

  @Override
  public boolean isIndexOnly() {
    return true;
  }

  private List<String> getFiles(ChangeData cd) throws OrmException {
    try {
      return cd.currentFilePaths(db, cache, loader, mgr);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }
}
