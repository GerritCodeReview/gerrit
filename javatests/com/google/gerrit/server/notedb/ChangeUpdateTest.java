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

public class ChangeUpdateTest extends AbstractChangeNotesTest {

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenNotAbandoningChangeAndChangingAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setAttentionSetOnly(true);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenAbandoningChangeAndNotChangingAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setStatus(Change.Status.ABANDONED);
    update.setAttentionSetOnly(false);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenNotAbandoningChangeAndNotChangingAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setAttentionSetOnly(false);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenAbandoningAndChangesToAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setStatus(Change.Status.ABANDONED);
    update.setAttentionSetOnly(true);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }
}
