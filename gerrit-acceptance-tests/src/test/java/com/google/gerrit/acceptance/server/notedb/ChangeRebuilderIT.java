// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testutil.GerritServerTests.isNoteDbTestEnabled;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @Inject
  private PatchLineCommentsUtil plcUtil;

  @Inject
  private ChangeRebuilder rebuilder;

  @Before
  public void setUp() {
    assume().that(isNoteDbTestEnabled()).isFalse();
    notesMigration.setAllEnabled(false);
  }

  @Test
  public void changeFields() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    gApi.changes().id(id.get()).topic(name("a-topic"));
    rebuildAndCheck(id);
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());
    rebuildAndCheck(id);
  }

  private void rebuildAndCheck(Change.Id... changeIds) throws Exception {
    Map<Change.Id, ChangeBundle> expected =
        new TreeMap<>(ReviewDbUtil.intKeyOrdering());
    notesMigration.setWriteChanges(true);
    for (Change.Id id : changeIds) {
      expected.put(id, ChangeBundle.fromReviewDb(db, id));
      rebuilder.rebuild(db, id);
    }

    notesMigration.setReadChanges(true);
    for (Change.Id id : changeIds) {
      ChangeBundle actual =
          ChangeBundle.fromNotes(plcUtil, notesFactory.create(db, project, id));
      List<String> diff = expected.get(id).differencesFrom(actual);
      if (!diff.isEmpty()) {
        throw new AssertionError(
            "Differences between ReviewDb and NoteDb for change " + id + ":\n"
            + Joiner.on('\n').join(diff));
      }
    }
  }
}
