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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.index.ChangeField.SORTKEY;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

public abstract class SortKeyPredicate extends IndexPredicate<ChangeData> {
  @SuppressWarnings("deprecation")
  private static long parseSortKey(Schema<ChangeData> schema, String value) {
    FieldDef<ChangeData, ?> field = schema.getFields().get(SORTKEY.getName());
    if (field == SORTKEY) {
      return ChangeUtil.parseSortKey(value);
    } else {
      return ChangeField.legacyParseSortKey(value);
    }
  }

  @SuppressWarnings("deprecation")
  private static FieldDef<ChangeData, ?> sortkeyField(Schema<ChangeData> schema) {
    if (schema == null) {
      return ChangeField.LEGACY_SORTKEY;
    }
    FieldDef<ChangeData, ?> f = schema.getFields().get(SORTKEY.getName());
    if (f != null) {
      return f;
    }
    return checkNotNull(
        schema.getFields().get(ChangeField.LEGACY_SORTKEY.getName()),
        "schema missing sortkey field, found: %s", schema.getFields().keySet());
  }

  protected final Schema<ChangeData> schema;
  protected final Provider<ReviewDb> dbProvider;

  SortKeyPredicate(Schema<ChangeData> schema, Provider<ReviewDb> dbProvider,
      String name, String value) {
    super(sortkeyField(schema), name, value);
    this.schema = schema;
    this.dbProvider = dbProvider;
  }

  @Override
  public int getCost() {
    return 1;
  }

  public abstract long getMinValue(Schema<ChangeData> schema);
  public abstract long getMaxValue(Schema<ChangeData> schema);
  public abstract SortKeyPredicate copy(String newValue);

  public static class Before extends SortKeyPredicate {
    Before(@Nullable Schema<ChangeData> schema, Provider<ReviewDb> dbProvider,
        String value) {
      super(schema, dbProvider, "sortkey_before", value);
    }

    @Override
    public long getMinValue(Schema<ChangeData> schema) {
      return 0;
    }

    @Override
    public long getMaxValue(Schema<ChangeData> schema) {
      return parseSortKey(schema, getValue());
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) < 0;
    }

    @Override
    public Before copy(String newValue) {
      return new Before(schema, dbProvider, newValue);
    }
  }

  public static class After extends SortKeyPredicate {
    After(@Nullable Schema<ChangeData> schema, Provider<ReviewDb> dbProvider,
        String value) {
      super(schema, dbProvider, "sortkey_after", value);
    }

    @Override
    public long getMinValue(Schema<ChangeData> schema) {
      return parseSortKey(schema, getValue());
    }

    @Override
    public long getMaxValue(Schema<ChangeData> schema) {
      return Long.MAX_VALUE;
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      Change change = cd.change(dbProvider);
      return change != null && change.getSortKey().compareTo(getValue()) > 0;
    }

    @Override
    public After copy(String newValue) {
      return new After(schema, dbProvider, newValue);
    }
  }
}
