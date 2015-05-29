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

import com.google.common.collect.Iterables;
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
    return schema.getField(FUZZY_TOPIC, LEGACY_TOPIC3, LEGACY_TOPIC2).get();
  }

  TopicPredicate(Schema<ChangeData> schema, String topic, ChangeIndex index) {
    super(topicField(schema), topic);
    this.index = index;
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    Change change = cd.change();
    if (change == null) {
      return false;
    }
    String t = change.getTopic();
    if (t == null) {
      return false;
    }
    if (getField() == FUZZY_TOPIC || getField() == LEGACY_TOPIC3) {
      try {
        Predicate<ChangeData> thisId = new LegacyChangeIdPredicate(cd.getId());
        Iterable<ChangeData> results = index.getSource(and(thisId, this), 0, 1).read();
        return !Iterables.isEmpty(results);
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
