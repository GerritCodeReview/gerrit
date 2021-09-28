// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import java.sql.Timestamp;
import org.junit.Test;

public class CommentThreadTest {

  @Test
  public void threadMustContainAtLeastOneComment() {
    assertThrows(IllegalStateException.class, () -> CommentThread.builder().build());
  }

  @Test
  public void threadCanBeUnresolved() {
    HumanComment root = unresolved(createComment("root"));
    CommentThread<Comment> commentThread = CommentThread.builder().addComment(root).build();

    assertThat(commentThread.unresolved()).isTrue();
  }

  @Test
  public void threadCanBeResolved() {
    HumanComment root = resolved(createComment("root"));
    CommentThread<Comment> commentThread = CommentThread.builder().addComment(root).build();

    assertThat(commentThread.unresolved()).isFalse();
  }

  @Test
  public void lastCommentInThreadDeterminesUnresolvedStatus() {
    HumanComment root = resolved(createComment("root"));
    HumanComment child = unresolved(createComment("child"));
    CommentThread<Comment> commentThread =
        CommentThread.builder().addComment(root).addComment(child).build();

    assertThat(commentThread.unresolved()).isTrue();
  }

  private static HumanComment createComment(String commentUuid) {
    return new HumanComment(
        new Comment.Key(commentUuid, "myFile", 1),
        Account.id(100),
        new Timestamp(1234),
        (short) 1,
        "Comment text",
        "serverId",
        true);
  }

  private static HumanComment resolved(HumanComment comment) {
    comment.unresolved = false;
    return comment;
  }

  private static HumanComment unresolved(HumanComment comment) {
    comment.unresolved = true;
    return comment;
  }
}
