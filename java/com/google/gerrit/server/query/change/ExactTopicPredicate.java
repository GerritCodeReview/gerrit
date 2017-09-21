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

import static com.google.gerrit.server.index.change.ChangeField.EXACT_TOPIC;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;

public class ExactTopicPredicate extends ChangeIndexPredicate {
  public ExactTopicPredicate(String topic) {
    super(EXACT_TOPIC, topic);
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change change = object.change();
    if (change == null) {
      return false;
    }
    return getValue().equals(change.getTopic());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
