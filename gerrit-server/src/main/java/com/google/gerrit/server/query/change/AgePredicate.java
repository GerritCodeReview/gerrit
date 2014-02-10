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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.index.ChangeField.UPDATED;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.TimestampRangePredicate;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;

import java.sql.Timestamp;

public class AgePredicate extends TimestampRangePredicate<ChangeData> {
  private final long cut;

  @SuppressWarnings({"deprecation", "unchecked"})
  private static FieldDef<ChangeData, Timestamp> updatedField(
      Schema<ChangeData> schema) {
    if (schema == null) {
      return ChangeField.LEGACY_UPDATED;
    }
    FieldDef<ChangeData, ?> f = schema.getFields().get(UPDATED.getName());
    if (f == null) {
      f = schema.getFields().get(ChangeField.LEGACY_UPDATED.getName());
      checkNotNull(f, "schema missing updated field, found: %s", schema);
    }
    checkArgument(f.getType() == FieldType.TIMESTAMP,
        "expected %s to be TIMESTAMP, found %s", f.getName(), f.getType());
    return (FieldDef<ChangeData, Timestamp>) f;
  }

  AgePredicate(Schema<ChangeData> schema, String value) {
    super(updatedField(schema), ChangeQueryBuilder.FIELD_AGE, value);

    long s = ConfigUtil.getTimeUnit(getValue(), 0, SECONDS);
    long ms = MILLISECONDS.convert(s, SECONDS);
    this.cut = TimeUtil.nowMs() - ms;
  }

  public Timestamp getMinTimestamp() {
    return new Timestamp(0);
  }

  public Timestamp getMaxTimestamp() {
    return new Timestamp(cut);
  }

  long getCut() {
    return cut + 1;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change();
    return change != null && change.getLastUpdatedOn().getTime() <= cut;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
