// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.Change;
import java.io.File;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@UseLocalDisk
public class ReflogIT extends AbstractDaemonTest {
  @Test
  public void guessRestApiInReflog() throws Exception {
    assume().that(notesMigration.disableChangeReviewDb()).isTrue();
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    try (Repository repo = repoManager.openRepository(r.getChange().project())) {
      File log = new File(repo.getDirectory(), "logs/" + changeMetaRef(id));
      if (!log.exists()) {
        log.getParentFile().mkdirs();
        assertThat(log.createNewFile()).isTrue();
      }

      gApi.changes().id(id.get()).topic("foo");
      ReflogEntry last = repo.getReflogReader(changeMetaRef(id)).getLastEntry();
      assertThat(last).named("last RefLogEntry").isNotNull();
      assertThat(last.getComment()).isEqualTo("restapi.change.PutTopic");
    }
  }
}
