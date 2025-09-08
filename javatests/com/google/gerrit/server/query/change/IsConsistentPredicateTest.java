// Copyright (C) 2025 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IsConsistentPredicateTest {
  @Mock private ChangeNotes changeNotesMock;
  @Mock private ChangeData changeData;

  @Test
  public void matchConsistent() {
    IsConsistentPredicate isConsistentPredicate = new IsConsistentPredicate();
    Change.Id changeId = Change.id(1);
    int currentPS = 1;

    ChangeData cd =
        ChangeData.createForTest(
            Project.nameKey("project"),
            changeId,
            currentPS,
            ObjectId.zeroId(),
            (sid, legacyChangeNum) -> changeId,
            changeNotesMock);

    assertTrue(isConsistentPredicate.match(cd));
  }

  @Test
  public void matchInconsistent() {
    IsConsistentPredicate isConsistentPredicate = new IsConsistentPredicate();
    Change.Id changeId = Change.id(1);

    when(changeData.notes()).thenThrow(new NoSuchChangeException(changeId));

    assertFalse(isConsistentPredicate.match(changeData));
  }
}
