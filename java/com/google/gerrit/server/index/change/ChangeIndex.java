// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import java.util.function.Function;

/**
 * Index for Gerrit changes. This class is mainly used for typing the generic parent class that
 * contains actual implementations.
 */
public interface ChangeIndex extends Index<Change.Id, ChangeData> {
  interface Factory extends IndexDefinition.IndexFactory<Change.Id, ChangeData, ChangeIndex> {}

  @Override
  default Predicate<ChangeData> keyPredicate(Change.Id id) {
    return ChangePredicates.idStr(id);
  }

  Function<ChangeData, Change.Id> ENTITY_TO_KEY = ChangeData::getId;
}
