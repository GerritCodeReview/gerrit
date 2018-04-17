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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.Collection;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class LegacyCommentsIT extends AbstractDaemonTest {
  @Inject private ChangeNoteUtil noteUtil;

  @ConfigSuite.Default
  public static Config writeJsonFalseConfig() {
    Config c = new Config();
    c.setBoolean("noteDb", null, "writeJson", false);
    return c;
  }

  @Before
  public void setUp() throws Exception {
    setApiUser(user);
  }

  @Test
  public void legacyCommentHasLegacyFormatTrue() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    assertThat(noteUtil.getWriteJson()).isFalse();

    PushOneCommit.Result result = createChange();
    Change.Id changeId = result.getChange().getId();

    CommentInput cin = new CommentInput();
    cin.message = "comment";
    cin.path = PushOneCommit.FILE_NAME;

    ReviewInput rin = new ReviewInput();
    rin.comments = ImmutableMap.of(cin.path, ImmutableList.of(cin));
    gApi.changes().id(changeId.get()).current().review(rin);

    Collection<Comment> comments =
        notesFactory.createChecked(db, project, changeId).getComments().values();
    assertThat(comments).hasSize(1);
    Comment comment = comments.iterator().next();
    assertThat(comment.message).isEqualTo("comment");
    assertThat(comment.legacyFormat).isTrue();
  }
}
