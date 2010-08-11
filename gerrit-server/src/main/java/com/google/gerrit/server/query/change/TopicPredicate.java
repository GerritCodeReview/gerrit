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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.change.ChangeData.NeededData;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.EnumSet;

class TopicPredicate extends OperatorPredicate<ChangeData> implements
    Prefetchable {
  private final Provider<ReviewDb> dbProvider;

  TopicPredicate(Provider<ReviewDb> dbProvider, String topic) {
    super(ChangeQueryBuilder.FIELD_TOPIC, topic);
    this.dbProvider = dbProvider;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change(dbProvider);
    if (change == null) {
      return false;
    }
    return getValue().equals(change.getTopic());
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public EnumSet<NeededData> getNeededData() {
    return EnumSet.of(NeededData.CHANGE);
  }
}
