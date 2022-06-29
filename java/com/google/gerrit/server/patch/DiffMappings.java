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
import com.google.gerrit.server.patch.filediff.Edit;
import com.google.gerrit.server.patch.filediff.FileEdits;
import java.util.List;

/** Mappings derived from diffs. */
public class DiffMappings {

  private DiffMappings() {}

  public static Mapping toMapping(FileEdits fileEdits) {
    FileMapping fileMapping = FileMapping.forFile(fileEdits.oldPath(), fileEdits.newPath());
    ImmutableSet<RangeMapping> rangeMappings = toRangeMappings(fileEdits.edits());
    return Mapping.create(fileMapping, rangeMappings);
  }

  private static ImmutableSet<RangeMapping> toRangeMappings(List<Edit> edits) {
    return edits.stream()
        .map(
            edit ->
                RangeMapping.create(
                    Range.create(edit.beginA(), edit.endA()),
                    Range.create(edit.beginB(), edit.endB())))
        .collect(toImmutableSet());
  }
}
