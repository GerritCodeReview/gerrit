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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class AgePredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private final Provider<ReviewDb> dbProvider;
  private final long cut;

  AgePredicate(Provider<ReviewDb> dbProvider, String value) {
    super(ChangeQueryBuilder.FIELD_AGE, value);
    this.dbProvider = dbProvider;

    long s = ConfigUtil.getTimeUnit(getValue(), 0, SECONDS);
    long ms = MILLISECONDS.convert(s, SECONDS);
    this.cut = (System.currentTimeMillis() - ms) + 1;
  }

  long getCut() {
    return cut;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change(dbProvider);
    return change != null && change.getLastUpdatedOn().getTime() < cut;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
