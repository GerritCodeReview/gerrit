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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.schema.H2JGitLockAccountPatchReviewStore.lockTargetFromUrl;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.io.File;
import org.junit.Test;

public class H2JGitLockAccountPatchReviewStoreTest {
  @Test
  public void lockTargetFromUrl_plainPath() {
    assertThat(lockTargetFromUrl("jdbc:h2:file:/path/to/db")).isEqualTo(new File("/path/to/db"));
  }

  @Test
  public void lockTargetFromUrl_stripsOptions() {
    assertThat(lockTargetFromUrl("jdbc:h2:file:/path/to/db;FILE_LOCK=NO;DB_CLOSE_DELAY=0"))
        .isEqualTo(new File("/path/to/db"));
  }

  @Test
  public void lockTargetFromUrl_unescapesSemicolonInPath() {
    assertThat(lockTargetFromUrl("jdbc:h2:file:/path/with\\;semi/db;FILE_LOCK=NO"))
        .isEqualTo(new File("/path/with;semi/db"));
  }

  @Test
  public void lockTargetFromUrl_throwsOnInvalidUrls() {
    assertThrows(
        IllegalArgumentException.class, () -> lockTargetFromUrl("jdbc:h2:mem:/path/to/db"));
  }
}
