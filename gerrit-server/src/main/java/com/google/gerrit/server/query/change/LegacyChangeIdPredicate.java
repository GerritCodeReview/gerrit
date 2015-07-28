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

import static com.google.gerrit.server.index.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.ChangeField.LEGACY_ID2;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;

/** Predicate over change number (aka legacy ID or Change.Id). */
public class LegacyChangeIdPredicate extends IndexPredicate<ChangeData> {
  private final Change.Id id;

  @SuppressWarnings("deprecation")
  public static FieldDef<ChangeData, ?> idField(Schema<ChangeData> schema) {
    if (schema == null) {
      return ChangeField.LEGACY_ID2;
    } else if (schema.hasField(LEGACY_ID2)) {
      return schema.getFields().get(LEGACY_ID2.getName());
    } else {
      return schema.getFields().get(LEGACY_ID.getName());
    }
  }

  LegacyChangeIdPredicate(Schema<ChangeData> schema, Change.Id id) {
    super(idField(schema), ChangeQueryBuilder.FIELD_CHANGE, id.toString());
    this.id = id;
  }

  @Override
  public boolean match(final ChangeData object) {
    return id.equals(object.getId());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
