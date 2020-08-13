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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.patch.GitPositionTransformer.FileMapping;
import com.google.gerrit.server.patch.GitPositionTransformer.Mapping;
import com.google.gerrit.server.patch.GitPositionTransformer.Range;
import com.google.gerrit.server.patch.GitPositionTransformer.RangeMapping;

/** Mappings derived from diffs. */
public class DiffMappings {

  private DiffMappings() {}

  public static Mapping toMapping(PatchListEntry patchListEntry) {
    // This is just a direct translation of the former logic in EditTransformer. It doesn't
    // work for file deletions, though. As file deletions aren't relevant for 'edits due to rebase'
    // situations, we didn't notice this in the past.
    // TODO(aliceks): Fix for file deletions in another change.
    FileMapping fileMapping =
        FileMapping.create(getOldFilePath(patchListEntry), patchListEntry.getNewName());
    ImmutableSet<RangeMapping> rangeMappings =
        patchListEntry.getEdits().stream()
            .map(
                edit ->
                    RangeMapping.create(
                        Range.create(edit.getBeginA(), edit.getEndA()),
                        Range.create(edit.getBeginB(), edit.getEndB())))
            .collect(toImmutableSet());
    return Mapping.create(fileMapping, rangeMappings);
  }

  private static String getOldFilePath(PatchListEntry patchListEntry) {
    return MoreObjects.firstNonNull(patchListEntry.getOldName(), patchListEntry.getNewName());
  }
}
