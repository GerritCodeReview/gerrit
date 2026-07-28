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

import com.google.gerrit.entities.Patch;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.ioutil.RegexListSearcher;
import java.util.List;

/**
 * Predicate matching changes where <em>every</em> real (non-magic) file matches the supplied regex
 * — and no real files fall outside it.
 *
 * <p>Magic files ({@code /COMMIT_MSG}, {@code /MERGE_LIST}, {@code /PATCHSET_LEVEL}) are excluded
 * from matching so that callers never need to mention them in queries.
 *
 * <p>Usage: {@code onlypaths:^src/.*\.java$}
 */
public class RegexOnlyPathsPredicate extends PostFilterPredicate<ChangeData> {
  private final RegexListSearcher<String> searcher;

  public RegexOnlyPathsPredicate(String re) {
    super(ChangeQueryBuilder.FIELD_ONLY_PATHS, re);
    this.searcher = RegexListSearcher.ofStrings(re);
  }

  @Override
  public boolean match(ChangeData cd) {
    List<String> realFiles =
        cd.currentFilePaths().stream()
            .filter(p -> !Patch.isMagic(p))
            .sorted()
            .toList();

    if (realFiles.isEmpty()) {
      return false;
    }

    // Every real file must match; a single mismatch disqualifies the change.
    // RegexListSearcher expects a sorted list — realFiles is already sorted.
    return realFiles.stream()
        .allMatch(path -> searcher.search(List.of(path)).findAny().isPresent());
  }

  @Override
  public int getCost() {
    return 3;
  }
}
