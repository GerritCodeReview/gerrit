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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @Inject
  private NoteDbChecker checker;

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
    checker.checkChanges(id);
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());
    checker.checkChanges(id);
  }
}
