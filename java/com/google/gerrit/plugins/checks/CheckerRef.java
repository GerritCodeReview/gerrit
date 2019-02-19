// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.shard;
import static com.google.gerrit.reviewdb.client.RefNames.shardUuid;

import com.google.gerrit.reviewdb.client.Change;

public class CheckerRef {
  /** Ref namespace for checkers. */
  public static final String REFS_CHECKERS = "refs/checkers/";

  /** Ref that stores the repository to checkers map. */
  public static final String REFS_META_CHECKERS = "refs/meta/checkers/";

  /** Suffix for check refs. */
  public static final String CHECKS_SUFFIX = "/checks";

  public static String refsCheckers(String checkerUuid) {
    return REFS_CHECKERS + shardUuid(checkerUuid);
  }

  public static String checksRef(Change.Id changeId) {
    return REFS_CHANGES + shard(changeId.get()) + CHECKS_SUFFIX;
  }

  /**
   * Whether the ref is a checker branch that stores NoteDb data of a checker. Returns {@code true}
   * for all refs that start with {@code refs/checkers/}.
   */
  public static boolean isRefsCheckers(String ref) {
    return ref.startsWith(REFS_CHECKERS);
  }
}
