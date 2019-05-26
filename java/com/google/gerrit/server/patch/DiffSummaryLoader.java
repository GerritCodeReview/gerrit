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

package com.google.gerrit.server.patch;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class DiffSummaryLoader implements Callable<DiffSummary> {
  public interface Factory {
    DiffSummaryLoader create(DiffSummaryKey key, Project.NameKey project);
  }

  private final PatchListCache patchListCache;
  private final DiffSummaryKey key;
  private final Project.NameKey project;

  @Inject
  DiffSummaryLoader(PatchListCache plc, @Assisted DiffSummaryKey k, @Assisted Project.NameKey p) {
    patchListCache = plc;
    key = k;
    project = p;
  }

  @Override
  public DiffSummary call() throws Exception {
    PatchList patchList = patchListCache.get(key.toPatchListKey(), project);
    return toDiffSummary(patchList);
  }

  private DiffSummary toDiffSummary(PatchList patchList) {
    List<String> r = new ArrayList<>(patchList.getPatches().size());
    for (PatchListEntry e : patchList.getPatches()) {
      if (Patch.isMagic(e.getNewName())) {
        continue;
      }
      switch (e.getChangeType()) {
        case ADDED:
        case MODIFIED:
        case DELETED:
        case COPIED:
        case REWRITE:
          r.add(e.getNewName());
          break;

        case RENAMED:
          r.add(e.getOldName());
          r.add(e.getNewName());
          break;
      }
    }
    return new DiffSummary(
        r.stream().sorted().toArray(String[]::new),
        patchList.getInsertions(),
        patchList.getDeletions());
  }
}
