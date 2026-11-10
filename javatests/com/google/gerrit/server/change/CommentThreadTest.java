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
import java.time.Instant;
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

  @Test
  public void rootAndMetadataAccessorsReturnRootProperties() {
    HumanComment root = createComment("root", "java/Foo.java", 2, Account.id(101));
    HumanComment reply = createComment("reply", "java/Foo.java", 3, Account.id(202));
    CommentThread<Comment> commentThread =
        CommentThread.builder().addComment(root).addComment(reply).build();

    assertThat(commentThread.root()).isEqualTo(root);
    assertThat(commentThread.size()).isEqualTo(2);
    assertThat(commentThread.filename()).isEqualTo("java/Foo.java");
    assertThat(commentThread.side()).isEqualTo((short) 1);
  }

  @Test
  public void participantsAndPatchSetIdsPreserveThreadAppearanceOrder() {
    HumanComment root = createComment("root", "myFile", 1, Account.id(100));
    HumanComment reply1 = createComment("reply1", "myFile", 2, Account.id(200));
    HumanComment reply2 = createComment("reply2", "myFile", 2, Account.id(100));
    HumanComment reply3 = createComment("reply3", "myFile", 4, Account.id(300));
    CommentThread<Comment> commentThread =
        CommentThread.builder()
            .addComment(root)
            .addComment(reply1)
            .addComment(reply2)
            .addComment(reply3)
            .build();

    assertThat(commentThread.participants())
        .containsExactly(Account.id(100), Account.id(200), Account.id(300))
        .inOrder();
    assertThat(commentThread.patchSetIds()).containsExactly(1, 2, 4).inOrder();
  }

  private static HumanComment createComment(String commentUuid) {
    return createComment(commentUuid, "myFile", 1, Account.id(100));
  }

  private static HumanComment createComment(
      String commentUuid, String filename, int patchSetId, Account.Id authorId) {
    return new HumanComment(
        new Comment.Key(commentUuid, filename, patchSetId),
        authorId,
        Instant.ofEpochMilli(1234),
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
