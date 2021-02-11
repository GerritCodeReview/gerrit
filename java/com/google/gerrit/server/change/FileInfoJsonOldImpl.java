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
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of {@link FileInfoJson} using the old diff cache {@link PatchListCache}. */
@Deprecated
@Singleton
class FileInfoJsonOldImpl implements FileInfoJson {
  private final PatchListCache patchListCache;

  @Inject
  FileInfoJsonOldImpl(PatchListCache patchListCache) {
    this.patchListCache = patchListCache;
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    ObjectId a = base != null ? base.commitId() : null;
    return toFileInfoMap(change, PatchListKey.againstCommit(a, objectId, Whitespace.IGNORE_NONE));
  }

  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    PatchListKey key =
        PatchListKey.againstParentNum(
            parentNum + 1, objectId, DiffPreferencesInfo.Whitespace.IGNORE_NONE);
    return toFileInfoMap(project, key);
  }

  private Map<String, FileInfo> toFileInfoMap(Change change, PatchListKey key)
      throws ResourceConflictException, PatchListNotAvailableException {
    return toFileInfoMap(change.getProject(), key);
  }

  Map<String, FileInfo> toFileInfoMap(Project.NameKey project, PatchListKey key)
      throws ResourceConflictException, PatchListNotAvailableException {
    PatchList list;
    try {
      list = patchListCache.get(key, project);
    } catch (PatchListNotAvailableException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ExecutionException) {
        cause = cause.getCause();
      }
      if (cause instanceof NoMergeBaseException) {
        throw new ResourceConflictException(
            String.format("Cannot create auto merge commit: %s", e.getMessage()), e);
      }
      throw e;
    }

    Map<String, FileInfo> files = new TreeMap<>();
    for (PatchListEntry e : list.getPatches()) {
      FileInfo fileInfo = new FileInfo();
      fileInfo.status =
          e.getChangeType() != Patch.ChangeType.MODIFIED ? e.getChangeType().getCode() : null;
      fileInfo.oldMode = e.getOldMode();
      fileInfo.newMode = e.getNewMode();
      fileInfo.oldPath = e.getOldName();
      fileInfo.sizeDelta = e.getSizeDelta();
      fileInfo.size = e.getSize();
      if (e.getPatchType() == Patch.PatchType.BINARY) {
        fileInfo.binary = true;
      } else {
        fileInfo.linesInserted = e.getInsertions() > 0 ? e.getInsertions() : null;
        fileInfo.linesDeleted = e.getDeletions() > 0 ? e.getDeletions() : null;
      }

      FileInfo o = files.put(e.getNewName(), fileInfo);
      if (o != null) {
        // This should only happen on a delete-add break created by JGit
        // when the file was rewritten and too little content survived. Write
        // a single record with data from both sides.
        fileInfo.status = Patch.ChangeType.REWRITE.getCode();
        fileInfo.sizeDelta = o.sizeDelta;
        fileInfo.size = o.size;
        if (o.binary != null && o.binary) {
          fileInfo.binary = true;
        }
        if (o.linesInserted != null) {
          fileInfo.linesInserted = o.linesInserted;
        }
        if (o.linesDeleted != null) {
          fileInfo.linesDeleted = o.linesDeleted;
        }
      }
    }
    return files;
  }
}
