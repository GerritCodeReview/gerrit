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

import static com.google.gerrit.server.index.change.ChangeField.FUZZY_TOPIC;

import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;

public class FuzzyTopicPredicate extends ChangeIndexPredicate {
  protected final ChangeIndex index;

  public FuzzyTopicPredicate(String topic, ChangeIndex index) {
    super(FUZZY_TOPIC, topic);
    this.index = index;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    Change change = cd.change();
    if (change == null) {
      return false;
    }
    String t = change.getTopic();
    if (t == null) {
      return false;
    }
    try {
      Predicate<ChangeData> thisId = new LegacyChangeIdPredicate(cd.getId());
      Iterable<ChangeData> results =
          index.getSource(and(thisId, this), IndexedChangeQuery.oneResult()).read();
      return !Iterables.isEmpty(results);
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
