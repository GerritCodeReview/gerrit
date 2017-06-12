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

import static com.google.gerrit.server.index.change.ChangeField.COMMITTER;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_COMMITTER;

import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

public class CommitterPredicate extends ChangeIndexPredicate {
  CommitterPredicate(String value) {
    super(COMMITTER, FIELD_COMMITTER, value.toLowerCase());
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    return ChangeField.getCommitterParts(object).contains(getValue().toLowerCase());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
