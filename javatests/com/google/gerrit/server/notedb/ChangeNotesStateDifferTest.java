// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Change;
import org.junit.Test;

public final class ChangeNotesStateDifferTest {

  private final ChangeNotesStateDiffer differ = new ChangeNotesStateDiffer();

  @Test
  public void getDiff_givenEmptyStates() {
    ChangeNotesState emptyState1 = ChangeNotesState.Builder.empty(Change.id(1)).build();
    ChangeNotesState emptyState2 = ChangeNotesState.Builder.empty(Change.id(2)).build();

    ChangeNotesStateDiff diff = differ.getDiff(emptyState1, emptyState2);

    assertThat(diff.oldTopic()).isNull();
    assertThat(diff.newTopic()).isNull();
    assertThat(diff.addedMessages()).isEmpty();
    assertThat(diff.addedPatchSetApprovals()).isEmpty();
    assertThat(diff.removedPatchSetApprovals()).isEmpty();
  }
}
