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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.util.GitUtil.getParent;

import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Map;

@Singleton
public class FileInfoJson {
  private final PatchListCache patchListCache;
  private final GitRepositoryManager repoManager;

  @Inject
  FileInfoJson(
      PatchListCache patchListCache,
      GitRepositoryManager repoManager) {
    this.patchListCache = patchListCache;
    this.repoManager = repoManager;
  }

  Map<String, FileInfo> toFileInfoMap(Change change, PatchSet patchSet)
      throws PatchListNotAvailableException {
    return toFileInfoMap(change, patchSet.getRevision(), null, null);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, PatchSet patchSet,
      DiffType difftype) throws PatchListNotAvailableException {
    return toFileInfoMap(change, patchSet.getRevision(), null, difftype);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, RevId revision,
      @Nullable PatchSet base)
          throws PatchListNotAvailableException {
    return toFileInfoMap(change, revision, base, DiffType.BASE);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, RevId revision,
      @Nullable PatchSet base, @Nullable DiffType diffType)
          throws PatchListNotAvailableException {
    ObjectId b = ObjectId.fromString(revision.get());
    ObjectId a = getObjectIdA(base, b, change, diffType);
    PatchList list = patchListCache.get(
        new PatchListKey(a, b, Whitespace.IGNORE_NONE), change.getProject());

    Map<String, FileInfo> files = Maps.newTreeMap();
    for (PatchListEntry e : list.getPatches()) {
      FileInfo d = new FileInfo();
      d.status = e.getChangeType() != Patch.ChangeType.MODIFIED
          ? e.getChangeType().getCode() : null;
      d.oldPath = e.getOldName();
      d.sizeDelta = e.getSizeDelta();
      d.size = e.getSize();
      if (e.getPatchType() == Patch.PatchType.BINARY) {
        d.binary = true;
      } else {
        d.linesInserted = e.getInsertions() > 0 ? e.getInsertions() : null;
        d.linesDeleted = e.getDeletions() > 0 ? e.getDeletions() : null;
      }

      FileInfo o = files.put(e.getNewName(), d);
      if (o != null) {
        // This should only happen on a delete-add break created by JGit
        // when the file was rewritten and too little content survived. Write
        // a single record with data from both sides.
        d.status = Patch.ChangeType.REWRITE.getCode();
        d.sizeDelta = o.sizeDelta;
        d.size = o.size;
        if (o.binary != null && o.binary) {
          d.binary = true;
        }
        if (o.linesInserted != null) {
          d.linesInserted = o.linesInserted;
        }
        if (o.linesDeleted != null) {
          d.linesDeleted = o.linesDeleted;
        }
      }
    }
    return files;
  }

  private ObjectId getObjectIdA(PatchSet base, ObjectId b, Change change,
      DiffType diffType) throws PatchListNotAvailableException {
    if (base != null) {
      return ObjectId.fromString(base.getRevision().get());
    }
    if (diffType == null || diffType == DiffType.BASE) {
      try (Repository git = repoManager.openRepository(change.getProject())) {
        return getParent(git, b, 0);
      } catch (IOException e) {
        throw new PatchListNotAvailableException(
            String.format("Cannot parse commit: ", b));
      }
    }
    return null;
  }
}
