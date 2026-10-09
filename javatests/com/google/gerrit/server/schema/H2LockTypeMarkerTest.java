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
import static com.google.gerrit.server.schema.H2CustomLockAccountPatchReviewStore.checkLockTypeMarker;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class H2LockTypeMarkerTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void firstStartCreatesMarker() throws Exception {
    File db = new File(tmp.getRoot(), "account_patch_reviews");
    checkLockTypeMarker(db, "jgit");
    assertThat(Files.readString(new File(db.getPath() + ".locktype").toPath())).isEqualTo("jgit");
  }

  @Test
  public void sameLockTypeIsAccepted() {
    File db = new File(tmp.getRoot(), "account_patch_reviews");
    checkLockTypeMarker(db, "jgit");
    checkLockTypeMarker(db, "jgit");
  }

  @Test
  public void differentLockTypeIsRejected() {
    File db = new File(tmp.getRoot(), "account_patch_reviews");
    checkLockTypeMarker(db, "jgit");
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> checkLockTypeMarker(db, "other"));
    assertThat(e).hasMessageThat().contains("h2LockType=jgit");
    assertThat(e).hasMessageThat().contains("h2LockType=other");
  }

  @Test
  public void noTempFilesAreLeftBehind() {
    File db = new File(tmp.getRoot(), "account_patch_reviews");
    checkLockTypeMarker(db, "jgit");
    checkLockTypeMarker(db, "jgit");
    assertThat(tmp.getRoot().list()).asList().containsExactly("account_patch_reviews.locktype");
  }
}
