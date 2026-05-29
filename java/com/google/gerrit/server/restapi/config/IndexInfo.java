// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexDefinition;

@AutoValue
public abstract class IndexInfo {

  public static IndexInfo fromIndexCollection(
      String name, IndexCollection<?, ?, ?> indexCollection) {
    ImmutableSortedMap.Builder<Integer, IndexVersionInfo> versions =
        ImmutableSortedMap.naturalOrder();
    Index<?, ?> searchIndex = indexCollection.getSearchIndex();
    int searchIndexVersion = searchIndex.getSchema().getVersion();
    boolean searchIndexAdded = false;
    for (Index<?, ?> index : indexCollection.getWriteIndexes()) {
      boolean isSearchIndex = index.getSchema().getVersion() == searchIndexVersion;
      versions.put(
          index.getSchema().getVersion(),
          IndexVersionInfo.create(true, isSearchIndex, index.numDocs()));
      searchIndexAdded = searchIndexAdded || isSearchIndex;
    }
    if (!searchIndexAdded) {
      versions.put(searchIndexVersion, IndexVersionInfo.create(false, true, searchIndex.numDocs()));
    }

    return new AutoValue_IndexInfo(name, versions.build());
  }

  public static IndexInfo fromIndexDefinition(IndexDefinition<?, ?, ?> def) {
    return fromIndexCollection(def.getName(), def.getIndexCollection());
  }

  public abstract String getName();

  public abstract ImmutableMap<Integer, IndexVersionInfo> getVersions();

  @AutoValue
  public abstract static class IndexVersionInfo {
    static IndexVersionInfo create(boolean write, boolean search, int numDocs) {
      return new AutoValue_IndexInfo_IndexVersionInfo(write, search, numDocs);
    }

    abstract boolean isWrite();

    abstract boolean isSearch();

    abstract int numDocs();
  }
}
