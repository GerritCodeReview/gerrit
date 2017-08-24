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

import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;

import com.google.gerrit.reviewdb.client.Change;

/** Predicate over change number (aka legacy ID or Change.Id). */
public class LegacyChangeIdPredicate extends ChangeIndexPredicate {
  protected final Change.Id id;

  public LegacyChangeIdPredicate(Change.Id id) {
    super(LEGACY_ID, ChangeQueryBuilder.FIELD_CHANGE, id.toString());
    this.id = id;
  }

  @Override
  public boolean match(ChangeData object) {
    return id.equals(object.getId());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
