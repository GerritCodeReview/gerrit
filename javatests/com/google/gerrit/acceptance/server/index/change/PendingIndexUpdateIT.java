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

package com.google.gerrit.acceptance.server.index.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.WaitUtil.waitUntil;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.index.change.PendingIndexUpdate;
import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;

public class PendingIndexUpdateIT extends AbstractDaemonTest {
  private static final long DEAD_THREAD_ID = Long.MAX_VALUE;
  @Inject private PendingIndexUpdate pendingIndexUpdate;

  @Test
  @GerritConfig(name = "index.staleChangeRecovery", value = "true")
  @GerritConfig(name = "index.staleChangeRecoveryInterval", value = "1s")
  @GerritConfig(name = "index.changes.commitWithin", value = "0")
  public void scannerRecoversMissedIndexWrite() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id changeId = r.getChange().getId();

    // Simulate a crash: the change is in NoteDb but the index write was missed.
    indexer.delete(project, changeId);
    pendingIndexUpdate.write(DEAD_THREAD_ID, project, changeId, /* delete= */ false);
    assertThat(gApi.changes().query("change:" + changeId).get()).isEmpty();

    waitUntil(
        () -> {
          try {
            return gApi.changes().query("change:" + changeId).get().size() == 1;
          } catch (Exception e) {
            return false;
          }
        },
        Duration.ofSeconds(5));
    assertThat(gApi.changes().query("change:" + changeId).get()).hasSize(1);
  }

  @Test
  @GerritConfig(name = "index.staleChangeRecovery", value = "true")
  @GerritConfig(name = "index.staleChangeRecoveryInterval", value = "1s")
  @GerritConfig(name = "index.changes.commitWithin", value = "0")
  public void scannerRecoversMissedIndexDelete() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id changeId = r.getChange().getId();
    pendingIndexUpdate.write(DEAD_THREAD_ID, project, changeId, /* delete= */ true);

    assertThat(gApi.changes().query("change:" + changeId).get()).hasSize(1);
    waitUntil(
        () -> {
          try {
            return gApi.changes().query("change:" + changeId).get().isEmpty();
          } catch (Exception e) {
            return false;
          }
        },
        Duration.ofSeconds(5));
    assertThat(gApi.changes().query("change:" + changeId).get()).isEmpty();
  }
}
