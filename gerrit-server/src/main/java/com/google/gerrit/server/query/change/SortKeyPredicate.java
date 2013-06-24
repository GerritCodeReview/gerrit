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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.index.TimestampRangePredicate;
import com.google.gerrit.server.index.UpdatedField;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.sql.Timestamp;

public abstract class SortKeyPredicate extends
    TimestampRangePredicate<ChangeData> {
  protected final Provider<ReviewDb> dbProvider;

  SortKeyPredicate(Provider<ReviewDb> dbProvider, String name, String value) {
    super(UpdatedField.INSTANCE, name, value);
    this.dbProvider = dbProvider;
  }

  @Override
  public int getCost() {
    return 1;
  }

  public static class Before extends SortKeyPredicate {
    Before(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_before", value);
    }

    @Override
    public Timestamp getMinTimestamp() {
      return new Timestamp(0);
    }

    @Override
    public Timestamp getMaxTimestamp() {
      return ChangeUtil.timeFromSortKey(getValue());
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) < 0;
    }
  }

  public static class After extends SortKeyPredicate {
    After(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_after", value);
    }

    @Override
    public Timestamp getMinTimestamp() {
      return ChangeUtil.timeFromSortKey(getValue());
    }

    @Override
    public Timestamp getMaxTimestamp() {
      return new Timestamp(Long.MAX_VALUE);
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) > 0;
    }
  }
}
