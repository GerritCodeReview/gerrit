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

import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.server.restapi.config.IndexCollection.IndexType;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class IndexInfo {
  private String name;
  private Map<Integer, IndexVersionInfo> versions = new TreeMap<>();

  public static IndexInfo fromIndexCollection(
      IndexType indexType, IndexCollection<?, ?, ?> indexCollection) {
    IndexInfo indexInfo = new IndexInfo();
    indexInfo.name = indexType.toString().toLowerCase(Locale.US);
    Index<?, ?> searchIndex = indexCollection.getSearchIndex();
    for (Index<?, ?> index : indexCollection.getWriteIndexes()) {
      indexInfo.versions.put(
          index.getSchema().getVersion(), new IndexVersionInfo(true, index.equals(searchIndex)));
    }
    int searchIndexVersion = searchIndex.getSchema().getVersion();
    if (!indexInfo.versions.containsKey(searchIndexVersion)) {
      indexInfo.versions.put(searchIndexVersion, new IndexVersionInfo(false, true));
    }
    return indexInfo;
  }

  public String getName() {
    return name;
  }

  public Map<Integer, IndexVersionInfo> getVersions() {
    return versions;
  }

  public static class IndexVersionInfo {
    private boolean write;
    private boolean search;

    public IndexVersionInfo(boolean write, boolean search) {
      this.write = write;
      this.search = search;
    }

    public boolean isWrite() {
      return write;
    }

    public boolean isSearch() {
      return search;
    }
  }
}
