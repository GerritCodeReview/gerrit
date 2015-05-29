// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.index.ChangeField.EXACT_TOPIC;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gwtorm.server.OrmException;

class ExactTopicPredicate extends IndexPredicate<ChangeData> {
  @SuppressWarnings("deprecation")
  static FieldDef<ChangeData, ?> topicField(Schema<ChangeData> schema) {
    if (schema == null) {
      return ChangeField.LEGACY_TOPIC2;
    }
    if (schema.hasField(EXACT_TOPIC)) {
      return schema.getFields().get(EXACT_TOPIC.getName());
    }
    if (schema.hasField(ChangeField.LEGACY_TOPIC2)) {
      return schema.getFields().get(ChangeField.LEGACY_TOPIC2.getName());
    }
    // Not exact, but we cannot do any better.
    return schema.getFields().get(ChangeField.LEGACY_TOPIC3.getName());
  }

  ExactTopicPredicate(Schema<ChangeData> schema, String topic) {
    super(topicField(schema), topic);
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change();
    if (change == null) {
      return false;
    }
    String t = change.getTopic();
    if (t == null && getField() == ChangeField.LEGACY_TOPIC2) {
      t = "";
    }
    return getValue().equals(t);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
