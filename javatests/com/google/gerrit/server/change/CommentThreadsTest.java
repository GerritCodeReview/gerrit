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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import java.sql.Timestamp;
import org.junit.Test;

public class CommentThreadsTest {

  @Test
  public void threadsAreEmptyWhenNoCommentsAreProvided() {
    ImmutableList<HumanComment> comments = ImmutableList.of();
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads = ImmutableSet.of();
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsCanBeCreatedFromSingleRoot() {
    HumanComment root = createComment("root");

    ImmutableList<HumanComment> comments = ImmutableList.of(root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads = ImmutableSet.of(toThread(root));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsCanBeCreatedFromUnorderedComments() {
    HumanComment root = createComment("root");
    HumanComment child1 = asReply(createComment("child1"), "root");
    HumanComment child2 = asReply(createComment("child2"), "child1");
    HumanComment child3 = asReply(createComment("child3"), "child2");

    ImmutableList<HumanComment> comments = ImmutableList.of(child2, child1, root, child3);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, child1, child2, child3));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void childWithNotAvailableParentIsAssumedToBeRoot() {
    HumanComment child1 = asReply(createComment("child1"), "root");

    ImmutableList<HumanComment> comments = ImmutableList.of(child1);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads = ImmutableSet.of(toThread(child1));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsIgnoreDuplicateRoots() {
    HumanComment root = createComment("root");
    HumanComment child1 = asReply(createComment("child1"), "root");

    ImmutableList<HumanComment> comments = ImmutableList.of(root, root, child1);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, child1));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsIgnoreDuplicateChildren() {
    HumanComment root = createComment("root");
    HumanComment child1 = asReply(createComment("child1"), "root");

    ImmutableList<HumanComment> comments = ImmutableList.of(root, child1, child1);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, child1));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void commentsAreOrderedIntoCorrectThreads() {
    HumanComment thread1Root = createComment("thread1Root");
    HumanComment thread1Child1 = asReply(createComment("thread1Child1"), "thread1Root");
    HumanComment thread1Child2 = asReply(createComment("thread1Child2"), "thread1Child1");
    HumanComment thread2Root = createComment("thread2Root");
    HumanComment thread2Child1 = asReply(createComment("thread2Child1"), "thread2Root");

    ImmutableList<HumanComment> comments =
        ImmutableList.of(thread2Root, thread1Child2, thread1Child1, thread1Root, thread2Child1);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(
            toThread(thread1Root, thread1Child1, thread1Child2),
            toThread(thread2Root, thread2Child1));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void branchedThreadsAreFlattenedAccordingToDate() {
    HumanComment root = writtenOn(createComment("root"), new Timestamp(1));
    HumanComment sibling1 = writtenOn(asReply(createComment("sibling1"), "root"), new Timestamp(2));
    HumanComment sibling2 = writtenOn(asReply(createComment("sibling2"), "root"), new Timestamp(3));
    HumanComment sibling1Child =
        writtenOn(asReply(createComment("sibling1Child"), "sibling1"), new Timestamp(4));
    HumanComment sibling2Child =
        writtenOn(asReply(createComment("sibling2Child"), "sibling2"), new Timestamp(5));

    ImmutableList<HumanComment> comments =
        ImmutableList.of(sibling2, sibling2Child, sibling1, sibling1Child, root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, sibling1, sibling2, sibling1Child, sibling2Child));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsConsiderParentRelationshipStrongerThanDate() {
    HumanComment root = writtenOn(createComment("root"), new Timestamp(3));
    HumanComment child1 = writtenOn(asReply(createComment("child1"), "root"), new Timestamp(2));
    HumanComment child2 = writtenOn(asReply(createComment("child2"), "child1"), new Timestamp(1));

    ImmutableList<HumanComment> comments = ImmutableList.of(child2, child1, root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, child1, child2));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void threadsFallBackToUuidOrderIfParentAndDateAreTheSame() {
    HumanComment root = writtenOn(createComment("root"), new Timestamp(1));
    HumanComment sibling1 = writtenOn(asReply(createComment("sibling1"), "root"), new Timestamp(2));
    HumanComment sibling2 = writtenOn(asReply(createComment("sibling2"), "root"), new Timestamp(2));

    ImmutableList<HumanComment> comments = ImmutableList.of(sibling2, sibling1, root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, sibling1, sibling2));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void specificThreadsCanBeRequestedByTheirReply() {
    HumanComment thread1Root = createComment("thread1Root");
    HumanComment thread2Root = createComment("thread2Root");

    HumanComment thread1Reply = asReply(createComment("thread1Reply"), "thread1Root");

    ImmutableList<HumanComment> comments = ImmutableList.of(thread1Root, thread2Root, thread1Reply);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreadsForChildren(ImmutableList.of(thread1Reply));

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(thread1Root, thread1Reply));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void requestedThreadsDoNotNeedToContainReply() {
    HumanComment thread1Root = createComment("thread1Root");
    HumanComment thread2Root = createComment("thread2Root");

    HumanComment thread1Reply = asReply(createComment("thread1Reply"), "thread1Root");

    ImmutableList<HumanComment> comments = ImmutableList.of(thread1Root, thread2Root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreadsForChildren(ImmutableList.of(thread1Reply));

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(thread1Root));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void completeThreadCanBeRequestedByReplyToRootComment() {
    HumanComment root = createComment("root");
    HumanComment child = asReply(createComment("child"), "root");

    HumanComment reply = asReply(createComment("reply"), "root");

    ImmutableList<HumanComment> comments = ImmutableList.of(root, child);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreadsForChildren(ImmutableList.of(reply));

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, child));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void completeThreadWithBranchesCanBeRequestedByReplyToIntermediateComment() {
    HumanComment root = writtenOn(createComment("root"), new Timestamp(1));
    HumanComment sibling1 = writtenOn(asReply(createComment("sibling1"), "root"), new Timestamp(2));
    HumanComment sibling2 = writtenOn(asReply(createComment("sibling2"), "root"), new Timestamp(3));
    HumanComment sibling1Child =
        writtenOn(asReply(createComment("sibling1Child"), "sibling1"), new Timestamp(4));
    HumanComment sibling2Child =
        writtenOn(asReply(createComment("sibling2Child"), "sibling2"), new Timestamp(5));

    HumanComment reply = asReply(createComment("sibling1"), "root");

    ImmutableList<HumanComment> comments =
        ImmutableList.of(root, sibling1, sibling2, sibling1Child, sibling2Child);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreadsForChildren(ImmutableList.of(reply));

    ImmutableSet<CommentThread<HumanComment>> expectedThreads =
        ImmutableSet.of(toThread(root, sibling1, sibling2, sibling1Child, sibling2Child));
    assertThat(commentThreads).isEqualTo(expectedThreads);
  }

  @Test
  public void requestedThreadsAreEmptyIfReplyDoesNotReferToAThread() {
    HumanComment root = createComment("root");

    HumanComment reply = asReply(createComment("reply"), "invalid");

    ImmutableList<HumanComment> comments = ImmutableList.of(root);
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreadsForChildren(ImmutableList.of(reply));

    ImmutableSet<CommentThread<HumanComment>> expectedThreads = ImmutableSet.of();
    assertThat(commentThreads).isEqualTo(expectedThreads);
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

  private static HumanComment asReply(HumanComment comment, String parentUuid) {
    comment.parentUuid = parentUuid;
    return comment;
  }

  private static HumanComment writtenOn(HumanComment comment, Timestamp writtenOn) {
    comment.writtenOn = writtenOn;
    return comment;
  }

  private static CommentThread<HumanComment> toThread(HumanComment... comments) {
    return CommentThread.<HumanComment>builder().comments(ImmutableList.copyOf(comments)).build();
  }
}
