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

import static com.google.gerrit.server.index.ChangeField.FUZZY_TOPIC;
import static com.google.gerrit.server.index.ChangeField.LEGACY_TOPIC2;
import static com.google.gerrit.server.index.ChangeField.LEGACY_TOPIC3;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;

class TopicPredicate extends IndexPredicate<ChangeData> {
  private final ChangeIndex index;

  @SuppressWarnings("deprecation")
  static FieldDef<ChangeData, ?> topicField(Schema<ChangeData> schema) {
    if (schema == null) {
      return LEGACY_TOPIC2;
    }
    if (schema.hasField(FUZZY_TOPIC)) {
      return schema.getFields().get(FUZZY_TOPIC.getName());
    }
    if (schema.hasField(LEGACY_TOPIC3)) {
      return schema.getFields().get(LEGACY_TOPIC3.getName());
    }

    return schema.getFields().get(LEGACY_TOPIC2.getName());
  }

  TopicPredicate(Schema<ChangeData> schema, String topic, ChangeIndex index) {
    super(topicField(schema), topic);
    this.index = index;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change();
    if (change == null) {
      return false;
    }
    String t = change.getTopic();
    if (t == null) {
      return false;
    }
    if (getField() == FUZZY_TOPIC || getField() == LEGACY_TOPIC3) {
      try {
        for (ChangeData cData : index.getSource(this, 0, 1).read()) {
          if (cData.change().getTopic().equals(object.change().getTopic())) {
            return true;
          }
        }
      } catch (QueryParseException e) {
        throw new OrmException(e);
      }
    }
    if (getField() == LEGACY_TOPIC2) {
      return t.equals(getValue());
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
