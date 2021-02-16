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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.RefNames.changeMetaRef;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/** Test handling of the NoteDb commit hash in the GetChange endpoint */
public class ChangeMetaIT extends AbstractDaemonTest {
  @Test
  public void metaSha1_fromIndex() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();

    try (AutoCloseable ignored = disableNoteDb()) {
      ChangeInfo change =
          Iterables.getOnlyElement(gApi.changes().query().withQuery("change:" + changeId).get());

      try (Repository repo = repoManager.openRepository(project)) {
        assertThat(change.metaRevId)
            .isEqualTo(
                repo.exactRef(changeMetaRef(Change.id(change._number))).getObjectId().getName());
      }
    }
  }

  @Test
  public void metaSha1_fromNoteDb() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    ChangeInfo before = gApi.changes().id(changeId).get();
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(before.metaRevId)
          .isEqualTo(
              repo.exactRef(changeMetaRef(Change.id(before._number))).getObjectId().getName());
    }
  }
}
