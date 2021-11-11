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
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.ObjectId;

public class DiffSummaryLoader implements Callable<DiffSummary> {
  public interface Factory {
    DiffSummaryLoader create(DiffSummaryKey key, Project.NameKey project);
  }

  private final DiffOperations diffOperations;
  private final DiffSummaryKey key;
  private final Project.NameKey project;

  @Inject
  DiffSummaryLoader(
      DiffOperations diffOps, @Assisted DiffSummaryKey k, @Assisted Project.NameKey p) {
    diffOperations = diffOps;
    key = k;
    project = p;
  }

  @Override
  public DiffSummary call() throws Exception {
    ObjectId oldId = key.toPatchListKey().getOldId();
    ObjectId newId = key.toPatchListKey().getNewId();
    Map<String, FileDiffOutput> diffList =
        oldId == null
            ? diffOperations.listModifiedFilesAgainstParent(
                project, newId, /* parentNum= */ 0, DiffOptions.DEFAULTS)
            : diffOperations.listModifiedFiles(project, oldId, newId, DiffOptions.DEFAULTS);
    return toDiffSummary(diffList);
  }

  private DiffSummary toDiffSummary(Map<String, FileDiffOutput> fileDiffs) {
    List<String> r = new ArrayList<>(fileDiffs.size());
    int linesInserted = 0;
    int linesDeleted = 0;
    for (String path : fileDiffs.keySet()) {
      if (Patch.isMagic(path)) {
        continue;
      }
      FileDiffOutput fileDiff = fileDiffs.get(path);
      linesInserted += fileDiff.insertions();
      linesDeleted += fileDiff.deletions();
      switch (fileDiff.changeType()) {
        case ADDED:
        case MODIFIED:
        case DELETED:
        case COPIED:
        case REWRITE:
          r.add(
              FilePathAdapter.getNewPath(
                  fileDiff.oldPath(), fileDiff.newPath(), fileDiff.changeType()));
          break;

        case RENAMED:
          r.add(FilePathAdapter.getOldPath(fileDiff.oldPath(), fileDiff.changeType()));
          r.add(
              FilePathAdapter.getNewPath(
                  fileDiff.oldPath(), fileDiff.newPath(), fileDiff.changeType()));
          break;
      }
    }
    return new DiffSummary(r.stream().sorted().toArray(String[]::new), linesInserted, linesDeleted);
  }
}
