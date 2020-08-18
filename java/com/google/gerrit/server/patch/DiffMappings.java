// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.patch.GitPositionTransformer.FileMapping;
import com.google.gerrit.server.patch.GitPositionTransformer.Mapping;
import com.google.gerrit.server.patch.GitPositionTransformer.Range;
import com.google.gerrit.server.patch.GitPositionTransformer.RangeMapping;

/** Mappings derived from diffs. */
public class DiffMappings {

  private DiffMappings() {}

  public static Mapping toMapping(PatchListEntry patchListEntry) {
    FileMapping fileMapping = toFileMapping(patchListEntry);
    ImmutableSet<RangeMapping> rangeMappings = toRangeMappings(patchListEntry);
    return Mapping.create(fileMapping, rangeMappings);
  }

  private static FileMapping toFileMapping(PatchListEntry patchListEntry) {
    switch (patchListEntry.getChangeType()) {
      case ADDED:
        return FileMapping.forAddedFile(patchListEntry.getNewName());
      case MODIFIED:
      case REWRITE:
        return FileMapping.forModifiedFile(patchListEntry.getNewName());
      case DELETED:
        // Name of deleted file is mentioned as newName.
        return FileMapping.forDeletedFile(patchListEntry.getNewName());
      case RENAMED:
      case COPIED:
        return FileMapping.forRenamedFile(patchListEntry.getOldName(), patchListEntry.getNewName());
      default:
        throw new IllegalStateException("Unmapped diff type: " + patchListEntry.getChangeType());
    }
  }

  private static ImmutableSet<RangeMapping> toRangeMappings(PatchListEntry patchListEntry) {
    return patchListEntry.getEdits().stream()
        .map(
            edit ->
                RangeMapping.create(
                    Range.create(edit.getBeginA(), edit.getEndA()),
                    Range.create(edit.getBeginB(), edit.getEndB())))
        .collect(toImmutableSet());
  }
}
