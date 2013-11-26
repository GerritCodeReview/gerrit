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
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

public class ChangeSizePredicate extends IndexPredicate<ChangeData> {

  public static enum Value {
    XS, S, M, L, XL;

    public static Value from(ChangedLines changedLines, Config cfg) {
      int n = changedLines.deletions + changedLines.insertions;
      int largeChangeThreshold = cfg.getInt("change", "largeChange", 500);
      if (n < largeChangeThreshold / 4) {
        return XS;
      } else if (n < largeChangeThreshold / 2) {
        return S;
      } else if (n < largeChangeThreshold) {
        return M;
      } else if (n < largeChangeThreshold * 2) {
        return L;
      } else {
        return XL;
      }
    }
  }

  private final Provider<ReviewDb> dbProvider;
  private final PatchListCache patchListCache;
  private final Config cfg;

  public ChangeSizePredicate(Provider<ReviewDb> dbProvider,
      PatchListCache patchListCache, Config cfg, String value) {
    super(ChangeField.SIZE, value);
    this.dbProvider = dbProvider;
    this.patchListCache = patchListCache;
    this.cfg = cfg;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Value size = Value.valueOf(getValue());
    return size.equals(Value.from(
        object.changedLines(dbProvider, patchListCache), cfg));
  }

  @Override
  public int getCost() {
    return 1;
  }
}
