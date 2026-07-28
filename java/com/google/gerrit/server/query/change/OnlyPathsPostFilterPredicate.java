// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.index.query.PostFilterPredicate;

/**
 * Post-filter that passes only when the change's set of real (non-magic) files is exactly the
 * expected list (sorted, deduplicated).
 *
 * <p>Magic files ({@code /COMMIT_MSG}, {@code /MERGE_LIST}, {@code /PATCHSET_LEVEL}) are excluded
 * from matching so that callers never need to mention them in queries.
 */
class OnlyPathsPostFilterPredicate extends PostFilterPredicate<ChangeData> {
  private final ImmutableSet<String> expectedFiles;

  OnlyPathsPostFilterPredicate(ImmutableSet<String> files) {
    super(ChangeQueryBuilder.FIELD_ONLY_PATHS, String.join(",", files));
    this.expectedFiles = files;
  }

  @Override
  public boolean match(ChangeData cd) {
    ImmutableSet<String> realFiles =
        cd.currentFilePaths().stream().filter(p -> !Patch.isMagic(p)).collect(toImmutableSet());
    return realFiles.equals(expectedFiles);
  }

  @Override
  public int getCost() {
    return 3;
  }
}
