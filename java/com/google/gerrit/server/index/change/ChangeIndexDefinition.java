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

package com.google.gerrit.server.index.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

/** Bundle of service classes that make up the change index. */
public class ChangeIndexDefinition extends IndexDefinition<Change.Id, ChangeData, ChangeIndex> {

  @Inject
  ChangeIndexDefinition(
      ChangeIndexCollection indexCollection,
      ChangeIndex.Factory indexFactory,
      @Nullable AllChangesIndexer allChangesIndexer) {
    super(ChangeSchemaDefinitions.INSTANCE, indexCollection, indexFactory, allChangesIndexer);
  }
}
