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

import java.io.File;
import org.junit.Test;

public class H2GitLockAccountPatchReviewStoreTest {
  @Test
  public void lockTargetFromUrl_plainPath() {
    assertThat(H2GitLockAccountPatchReviewStore.lockTargetFromUrl("jdbc:h2:file:/path/to/db"))
        .isEqualTo(new File("/path/to/db"));
  }

  @Test
  public void lockTargetFromUrl_stripsOptions() {
    assertThat(
            H2GitLockAccountPatchReviewStore.lockTargetFromUrl(
                "jdbc:h2:file:/path/to/db;FILE_LOCK=NO;DB_CLOSE_DELAY=0"))
        .isEqualTo(new File("/path/to/db"));
  }

  @Test
  public void lockTargetFromUrl_unescapesSemicolonInPath() {
    assertThat(
            H2GitLockAccountPatchReviewStore.lockTargetFromUrl(
                "jdbc:h2:file:/path/with\\;semi/db;FILE_LOCK=NO"))
        .isEqualTo(new File("/path/with;semi/db"));
  }
}
