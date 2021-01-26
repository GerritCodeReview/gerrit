// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of the new diff cache using the {@link DiffOperations} interface. */
public class FileInfoJsonNewImpl {
  private final DiffOperations diffs;

  @Inject
  FileInfoJsonNewImpl(DiffOperations diffOperations) {
    this.diffs = diffOperations;
  }

  Map<String, FileInfo> toFileInfoMap(Change change, PatchSet patchSet)
      throws PatchListNotAvailableException {
    return toFileInfoMap(change, patchSet.commitId(), null);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, ObjectId objectId, @Nullable PatchSet base)
      throws PatchListNotAvailableException {
    try {
      if (base == null) {
        return asFileInfo(
            diffs.getModifiedFilesAgainstParentOrAutoMerge(change.getProject(), objectId, null));
      } else {
        return asFileInfo(
            diffs.getModifiedFilesBetweenPatchsets(change.getProject(), base.commitId(), objectId));
      }
    } catch (DiffNotAvailableException e) {
      throw new PatchListNotAvailableException(e.getMessage(), e);
    }
  }

  Map<String, FileInfo> toFileInfoMap(Change change, ObjectId objectId, int parent)
      throws PatchListNotAvailableException {
    return toFileInfoMap(change.getProject(), objectId, parent);
  }

  Map<String, FileInfo> toFileInfoMap(Project.NameKey project, ObjectId objectId, int parent)
      throws PatchListNotAvailableException {
    try {
      Map<String, FileDiffOutput> modifiedFiles =
          diffs.getModifiedFilesAgainstParentOrAutoMerge(project, objectId, parent + 1);
      return asFileInfo(modifiedFiles);
    } catch (DiffNotAvailableException e) {
      throw new PatchListNotAvailableException(e.getMessage(), e);
    }
  }

  private Map<String, FileInfo> asFileInfo(Map<String, FileDiffOutput> fileDiffs) {
    Map<String, FileInfo> result = new HashMap<>();
    for (String path : fileDiffs.keySet()) {
      FileDiffOutput fileDiff = fileDiffs.get(path);
      FileInfo d = new FileInfo();
      d.status =
          fileDiff.changeType().get() != Patch.ChangeType.MODIFIED
              ? fileDiff.changeType().get().getCode()
              : null;
      d.oldPath = fileDiff.oldPath().orElse("NONE");
      d.sizeDelta = fileDiff.sizeDelta();
      d.size = fileDiff.size();
      if (fileDiff.patchType().get() == Patch.PatchType.BINARY) {
        d.binary = true;
      } else {
        d.linesInserted = fileDiff.insertions() > 0 ? fileDiff.insertions() : null;
        d.linesDeleted = fileDiff.deletions() > 0 ? fileDiff.deletions() : null;
      }
      result.put(path, d);
    }
    return result;
  }
}
