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
// limitations under the License

package com.google.gerrit.server.index.group;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;

/** Bundle of service classes that make up the group index. */
public class GroupIndexDefinition
    extends IndexDefinition<AccountGroup.UUID, InternalGroup, GroupIndex> {

  @Inject
  GroupIndexDefinition(
      GroupIndexCollection indexCollection,
      GroupIndex.Factory indexFactory,
      @Nullable AllGroupsIndexer allGroupsIndexer) {
    super(GroupSchemaDefinitions.INSTANCE, indexCollection, indexFactory, allGroupsIndexer);
  }
}
