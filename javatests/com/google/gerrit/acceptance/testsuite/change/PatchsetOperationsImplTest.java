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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThatList;

import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class PatchsetOperationsImplTest extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOperations;

  @Test
  public void commentCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid = changeOperations.change(changeId).currentPatchset().newComment().create();

    List<CommentInfo> comments = getCommentsFromServer(changeId);
    assertThatList(comments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void commentCanBeCreatedOnOlderPatchset() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    PatchSet.Id previousPatchsetId =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();
    changeOperations.change(changeId).newPatchset().create();

    String commentUuid =
        changeOperations.change(changeId).patchset(previousPatchsetId).newComment().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).patchSet().isEqualTo(previousPatchsetId.get());
  }

  @Test
  public void commentIsCreatedWithSpecifiedMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .message("Test comment message")
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).message().isEqualTo("Test comment message");
  }

  @Test
  public void commentCanBeCreatedWithEmptyMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().noMessage().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).message().isNull();
  }

  @Test
  public void patchsetLevelCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().onPatchsetLevel().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).path().isEqualTo(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void fileCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().file("file1").content("Line 1").create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onFileLevelOf("file1")
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).path().isEqualTo("file1");
    assertThat(comment).line().isNull();
    assertThat(comment).range().isNull();
  }

  @Test
  public void lineCommentCanBeCreated() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Line 1\nLine 2\nLine 3\nLine 4\n")
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onLine(3)
            .ofFile("file1")
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).line().isEqualTo(3);
    assertThat(comment).range().isNull();
  }

  @Test
  public void rangeCommentCanBeCreated() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .file("file1")
            .content("Line 1\nLine 2\nLine 3\nLine 4\n")
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .fromLine(2)
            .charOffset(4)
            .toLine(3)
            .charOffset(5)
            .ofFile("file1")
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).range().startLine().isEqualTo(2);
    assertThat(comment).range().startCharacter().isEqualTo(4);
    assertThat(comment).range().endLine().isEqualTo(3);
    assertThat(comment).range().endCharacter().isEqualTo(5);
    // Line is automatically filled from specified range. It's the end line.
    assertThat(comment).line().isEqualTo(3);
  }

  @Test
  public void commentCanBeCreatedOnPatchsetCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onPatchsetCommit()
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    // Null is often used instead of Side.REVISION as Side.REVISION is the default.
    assertThat(comment).side().isAnyOf(Side.REVISION, null);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void commentCanBeCreatedOnParentCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().onParentCommit().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(1);
  }

  @Test
  public void commentCanBeCreatedOnSecondParentCommit() throws Exception {
    // Second parents only exist for merge commits. The test API currently doesn't support the
    // creation of changes with merge commits yet, though. As there's no explicit validation keeping
    // us from adding comments on the non-existing second parent of a regular commit, just use the
    // latter. That's still better than not having this test at all.
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onSecondParentCommit()
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void commentCanBeCreatedOnAutoMergeCommit() throws Exception {
    // Second parents only exist for merge commits. The test API currently doesn't support the
    // creation of changes with merge commits yet, though. As there's no explicit validation keeping
    // us from adding comments on the non-existing second parent of a regular commit, just use the
    // latter. That's still better than not having this test at all.
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onAutoMergeCommit()
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void commentCanBeCreatedAsResolved() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().resolved().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).unresolved().isFalse();
  }

  @Test
  public void commentCanBeCreatedAsUnresolved() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().unresolved().create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).unresolved().isTrue();
  }

  @Test
  public void replyToCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .parentUuid(parentCommentUuid)
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).inReplyTo().isEqualTo(parentCommentUuid);
  }

  private List<CommentInfo> getCommentsFromServer(Change.Id changeId) throws RestApiException {
    return gApi.changes().id(changeId.get()).commentsAsList();
  }

  private CommentInfo getCommentFromServer(Change.Id changeId, String uuid)
      throws RestApiException {
    return gApi.changes().id(changeId.get()).commentsAsList().stream()
        .filter(comment -> comment.id.equals(uuid))
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("Comment %s not found on change %d", uuid, changeId.get())));
  }

  private Correspondence<CommentInfo, String> hasUuid() {
    return NullAwareCorrespondence.transforming(comment -> comment.id, "hasUuid");
  }
}
