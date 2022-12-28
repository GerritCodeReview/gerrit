// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index.group;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.query.group.GroupPredicates;
import java.util.function.Function;

/**
 * Index for internal Gerrit groups. This class is mainly used for typing the generic parent class
 * that contains actual implementations.
 */
public interface GroupIndex extends Index<AccountGroup.UUID, InternalGroup> {
  interface Factory
      extends IndexDefinition.IndexFactory<AccountGroup.UUID, InternalGroup, GroupIndex> {}

  @Override
  default Predicate<InternalGroup> keyPredicate(AccountGroup.UUID uuid) {
    return GroupPredicates.uuid(uuid);
  }

  Function<InternalGroup, AccountGroup.UUID> ENTITY_TO_KEY = (g) -> g.getGroupUUID();
}
