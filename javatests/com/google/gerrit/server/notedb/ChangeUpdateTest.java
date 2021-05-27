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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.util.time.TimeUtil;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ChangeUpdateTest extends AbstractChangeNotesTest {

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenChangingAttentionSetOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenClosingChange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setStatus(Change.Status.ABANDONED);

    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenNotAbandoningChangeAndNotChangingAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenCommentsAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    // Add a comment
    RevCommit commit = tr.commit().message("PS2").create();
    update.putComment(
        HumanComment.Status.PUBLISHED,
        newComment(
            c.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.nowTs(),
            "Comment",
            (short) 1,
            commit,
            false));
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }
}
