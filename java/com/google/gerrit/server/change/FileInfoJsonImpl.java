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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of {@link FileInfoJson} using {@link DiffOperations}. */
public class FileInfoJsonImpl implements FileInfoJson {
  private final DiffOperations diffs;

  @Inject
  FileInfoJsonImpl(DiffOperations diffOperations) {
    this.diffs = diffOperations;
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    try {
      if (base == null) {
        // Setting parentNum=0 requests the default parent, which is the only parent for
        // single-parent commits, or the auto-merge otherwise
        return asFileInfo(
            diffs.listModifiedFilesAgainstParent(
                change.getProject(), objectId, /* parentNum= */ 0, DiffOptions.DEFAULTS));
      }
      return asFileInfo(
          diffs.listModifiedFiles(
              change.getProject(), base.commitId(), objectId, DiffOptions.DEFAULTS));
    } catch (DiffNotAvailableException e) {
      convertException(e);
      return null; // unreachable. handleAndThrow will throw an exception anyway
    }
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parent)
      throws ResourceConflictException, PatchListNotAvailableException {
    try {
      Map<String, FileDiffOutput> modifiedFiles =
          diffs.listModifiedFilesAgainstParent(project, objectId, parent, DiffOptions.DEFAULTS);
      return asFileInfo(modifiedFiles);
    } catch (DiffNotAvailableException e) {
      convertException(e);
      return null; // unreachable. handleAndThrow will throw an exception anyway
    }
  }

  private void convertException(DiffNotAvailableException e)
      throws ResourceConflictException, PatchListNotAvailableException {
    Throwable cause = e.getCause();
    if (cause != null && !(cause instanceof NoMergeBaseException)) {
      cause = cause.getCause();
    }
    if (cause instanceof NoMergeBaseException) {
      throw new ResourceConflictException(
          String.format("Cannot create auto merge commit: %s", e.getMessage()), e);
    }
    throw new PatchListNotAvailableException(e);
  }

  private Map<String, FileInfo> asFileInfo(Map<String, FileDiffOutput> fileDiffs) {
    Map<String, FileInfo> result = new HashMap<>();
    for (String path : fileDiffs.keySet()) {
      FileDiffOutput fileDiff = fileDiffs.get(path);
      FileInfo fileInfo = new FileInfo();
      fileInfo.status =
          fileDiff.changeType() != Patch.ChangeType.MODIFIED
              ? fileDiff.changeType().getCode()
              : null;
      fileInfo.oldPath = FilePathAdapter.getOldPath(fileDiff.oldPath(), fileDiff.changeType());
      fileInfo.sizeDelta = fileDiff.sizeDelta();
      fileInfo.size = fileDiff.size();
      if (fileDiff.patchType().get() == Patch.PatchType.BINARY) {
        fileInfo.binary = true;
      } else {
        fileInfo.linesInserted = fileDiff.insertions() > 0 ? fileDiff.insertions() : null;
        fileInfo.linesDeleted = fileDiff.deletions() > 0 ? fileDiff.deletions() : null;
      }
      result.put(path, fileInfo);
    }
    return result;
  }
}
