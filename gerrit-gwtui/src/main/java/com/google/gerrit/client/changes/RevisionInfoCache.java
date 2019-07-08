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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cache of PatchSet.Id to revision SHA-1 strings. */
public class RevisionInfoCache {
  private static final int LIMIT = 10;
  private static final RevisionInfoCache IMPL = new RevisionInfoCache();

  public static void add(Change.Id change, RevisionInfo info) {
    IMPL.psToCommit.put(new PatchSet.Id(change, info._number()), info.name());
  }

  static String get(PatchSet.Id id) {
    return IMPL.psToCommit.get(id);
  }

  private final LinkedHashMap<PatchSet.Id, String> psToCommit;

  @SuppressWarnings("serial")
  private RevisionInfoCache() {
    psToCommit =
        new LinkedHashMap<PatchSet.Id, String>(LIMIT) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<PatchSet.Id, String> e) {
            return size() > LIMIT;
          }
        };
  }
}
