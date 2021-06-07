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

package com.google.gerrit.server.query.approval;

import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Predicate that matches when the new patch-set includes the same files as the old patch-set. */
@Singleton
public class ListOfFilesUnchangedPredicate extends ApprovalPredicate {
  private final PatchListCache patchListCache;

  @Inject
  public ListOfFilesUnchangedPredicate(PatchListCache patchListCache) {
    this.patchListCache = patchListCache;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    PatchSet currentPatchset = ctx.changeNotes().getCurrentPatchSet();
    Map.Entry<PatchSet.Id, PatchSet> priorPatchSet =
        ctx.changeNotes().getPatchSets().lowerEntry(currentPatchset.id());
    PatchListKey key =
        PatchListKey.againstCommit(
            priorPatchSet.getValue().commitId(),
            currentPatchset.commitId(),
            DiffPreferencesInfo.Whitespace.IGNORE_NONE);
    try {
      return match(patchListCache.get(key, ctx.changeNotes().getProjectName()));
    } catch (PatchListNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't copy"
              + " votes on labels even if list of files is the same and "
              + "copyAllIfListOfFilesDidNotChange",
          ex);
    }
  }

  public boolean match(PatchList patchList) {
    return patchList.getPatches().stream()
        .noneMatch(
            p ->
                p.getChangeType() == ChangeType.ADDED
                    || p.getChangeType() == ChangeType.DELETED
                    || p.getChangeType() == ChangeType.RENAMED);
  }

  @Override
  public Predicate<ApprovalContext> copy(
      Collection<? extends Predicate<ApprovalContext>> children) {
    return new ListOfFilesUnchangedPredicate(patchListCache);
  }

  @Override
  public int hashCode() {
    return Objects.hash(patchListCache);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ListOfFilesUnchangedPredicate)) {
      return false;
    }
    ListOfFilesUnchangedPredicate o = (ListOfFilesUnchangedPredicate) other;
    return Objects.equals(o.patchListCache, patchListCache);
  }
}
