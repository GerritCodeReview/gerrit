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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.group.GroupPredicates;

public interface GroupIndex extends Index<AccountGroup.UUID, AccountGroup> {
  public interface Factory
      extends IndexDefinition.IndexFactory<AccountGroup.UUID, AccountGroup, GroupIndex> {}

  @Override
  default Predicate<AccountGroup> keyPredicate(AccountGroup.UUID uuid) {
    return GroupPredicates.uuid(uuid);
  }
}
