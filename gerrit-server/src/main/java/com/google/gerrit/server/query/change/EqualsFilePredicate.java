// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;

class EqualsFilePredicate extends ChangeIndexPredicate {
  static Predicate<ChangeData> create(Arguments args, String value) {
    Predicate<ChangeData> eqPath = new EqualsPathPredicate(ChangeQueryBuilder.FIELD_FILE, value);
    if (!args.getSchema().hasField(ChangeField.FILE_PART)) {
      return eqPath;
    }
    return Predicate.or(eqPath, new EqualsFilePredicate(value));
  }

  private final String value;

  private EqualsFilePredicate(String value) {
    super(ChangeField.FILE_PART, ChangeQueryBuilder.FIELD_FILE, value);
    this.value = value;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    return ChangeField.getFileParts(object).contains(value);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
