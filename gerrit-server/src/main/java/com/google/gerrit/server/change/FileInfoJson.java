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

import com.google.gerrit.common.Nullable;
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

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

@Singleton
public class FileInfoJson {
  private final PatchListCache patchListCache;
  private final GitRepositoryManager repoManager;

  @Inject
  FileInfoJson(
      PatchListCache patchListCache,
      GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
    this.patchListCache = patchListCache;
  }

  Map<String, FileInfo> toFileInfoMap(Change change, PatchSet patchSet)
      throws PatchListNotAvailableException {
    return toFileInfoMap(change, patchSet.getRevision(), null);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, RevId revision, @Nullable PatchSet base)
      throws PatchListNotAvailableException {
    ObjectId a = (base == null)
        ? null
        : ObjectId.fromString(base.getRevision().get());
    ObjectId b = ObjectId.fromString(revision.get());
    return toFileInfoMap(change, a, b);
  }

  Map<String, FileInfo> toFileInfoMap(Change change, RevId revision, int parent)
      throws RepositoryNotFoundException, IOException,
          PatchListNotAvailableException {
    ObjectId b = ObjectId.fromString(revision.get());
    ObjectId a;
    try (Repository git = repoManager.openRepository(change.getProject())) {
      a = getParent(git, b, parent);
    }
    return toFileInfoMap(change, a, b);
  }

  private Map<String, FileInfo> toFileInfoMap(Change change,
      ObjectId a, ObjectId b) throws PatchListNotAvailableException {
    PatchList list = patchListCache.get(
        new PatchListKey(a, b, Whitespace.IGNORE_NONE), change.getProject());

    Map<String, FileInfo> files = new TreeMap<>();
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
}
