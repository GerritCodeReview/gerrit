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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;

/** Predicate over change number (aka legacy ID or Change.Id). */
class LegacyChangeIdPredicate extends IndexPredicate<ChangeData> {
  private final Change.Id id;

  LegacyChangeIdPredicate(Change.Id id) {
    super(ChangeField.LEGACY_ID, ChangeQueryBuilder.FIELD_CHANGE, id.toString());
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
