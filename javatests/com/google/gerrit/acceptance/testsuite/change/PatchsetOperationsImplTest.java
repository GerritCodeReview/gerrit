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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThatList;
import static com.google.gerrit.extensions.common.testing.RobotCommentInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.RobotCommentInfoSubject.assertThatList;

import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Test;

public class PatchsetOperationsImplTest extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

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
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

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
  public void commentCanBeCreatedOnNonExistingSecondParentCommit() throws Exception {
    Change.Id parentChangeId = changeOperations.newChange().create();
    Change.Id changeId = changeOperations.newChange().childOf().change(parentChangeId).create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .onSecondParentCommit()
            .create();

    // We want to be able to create such invalid comments for testing purposes (e.g. testing error
    // handling or resilience of an endpoint) and hence we need to allow such invalid comments in
    // the test API.
    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void commentCanBeCreatedOnAutoMergeCommit() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

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

  @Test
  public void tagCanBeAttachedToAComment() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .tag("my special tag")
            .create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).tag().isEqualTo("my special tag");
  }

  @Test
  public void commentIsCreatedWithSpecifiedAuthor() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    Account.Id accountId = accountOperations.newAccount().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().author(accountId).create();

    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).author().id().isEqualTo(accountId.get());
  }

  @Test
  public void commentIsCreatedWithSpecifiedCreationTime() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    // Don't use nanos. NoteDb supports only second precision.
    Instant creationTime =
        LocalDateTime.of(2020, Month.SEPTEMBER, 15, 12, 10, 43).atZone(ZoneOffset.UTC).toInstant();
    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .createdOn(creationTime)
            .create();

    Timestamp creationTimestamp = Timestamp.from(creationTime);
    CommentInfo comment = getCommentFromServer(changeId, commentUuid);
    assertThat(comment).updated().isEqualTo(creationTimestamp);
  }

  @Test
  public void zoneOfCreationDateCanBeOmitted() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    // As we don't care about the exact time zone internally used as a default, do a relative test
    // so that we don't need to assert on exact instants in time. For a relative test, we need two
    // comments whose creation date should be exactly the specified amount apart.
    // Don't use nanos or millis. NoteDb supports only second precision.
    LocalDateTime creationTime1 = LocalDateTime.of(2020, Month.SEPTEMBER, 15, 12, 10, 43);
    LocalDateTime creationTime2 = creationTime1.plusMinutes(10);
    String commentUuid1 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .createdOn(creationTime1)
            .create();
    String commentUuid2 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .createdOn(creationTime2)
            .create();

    CommentInfo comment1 = getCommentFromServer(changeId, commentUuid1);
    Instant comment1Creation = comment1.updated.toInstant();
    CommentInfo comment2 = getCommentFromServer(changeId, commentUuid2);
    Instant comment2Creation = comment2.updated.toInstant();
    Duration commentCreationDifference = Duration.between(comment1Creation, comment2Creation);
    assertThat(commentCreationDifference).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  public void draftCommentCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().create();

    List<CommentInfo> comments = getDraftCommentsFromServer(changeId);
    assertThatList(comments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void draftCommentCanBeCreatedOnOlderPatchset() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    PatchSet.Id previousPatchsetId =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();
    changeOperations.change(changeId).newPatchset().create();

    String commentUuid =
        changeOperations.change(changeId).patchset(previousPatchsetId).newDraftComment().create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).patchSet().isEqualTo(previousPatchsetId.get());
  }

  @Test
  public void draftCommentIsCreatedWithSpecifiedMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .message("Test comment message")
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).message().isEqualTo("Test comment message");
  }

  @Test
  public void draftCommentCanBeCreatedWithEmptyMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().noMessage().create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).message().isNull();
  }

  @Test
  public void draftPatchsetLevelCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onPatchsetLevel()
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).path().isEqualTo(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void draftFileCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().file("file1").content("Line 1").create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onFileLevelOf("file1")
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).path().isEqualTo("file1");
    assertThat(comment).line().isNull();
    assertThat(comment).range().isNull();
  }

  @Test
  public void draftLineCommentCanBeCreated() throws Exception {
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
            .newDraftComment()
            .onLine(3)
            .ofFile("file1")
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).line().isEqualTo(3);
    assertThat(comment).range().isNull();
  }

  @Test
  public void draftRangeCommentCanBeCreated() throws Exception {
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
            .newDraftComment()
            .fromLine(2)
            .charOffset(4)
            .toLine(3)
            .charOffset(5)
            .ofFile("file1")
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).range().startLine().isEqualTo(2);
    assertThat(comment).range().startCharacter().isEqualTo(4);
    assertThat(comment).range().endLine().isEqualTo(3);
    assertThat(comment).range().endCharacter().isEqualTo(5);
    // Line is automatically filled from specified range. It's the end line.
    assertThat(comment).line().isEqualTo(3);
  }

  @Test
  public void draftCommentCanBeCreatedOnPatchsetCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onPatchsetCommit()
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    // Null is often used instead of Side.REVISION as Side.REVISION is the default.
    assertThat(comment).side().isAnyOf(Side.REVISION, null);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void draftCommentCanBeCreatedOnParentCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onParentCommit()
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(1);
  }

  @Test
  public void draftCommentCanBeCreatedOnSecondParentCommit() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onSecondParentCommit()
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void draftCommentCanBeCreatedOnNonExistingSecondParentCommit() throws Exception {
    Change.Id parentChangeId = changeOperations.newChange().create();
    Change.Id changeId = changeOperations.newChange().childOf().change(parentChangeId).create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onSecondParentCommit()
            .create();

    // We want to be able to create such invalid comments for testing purposes (e.g. testing error
    // handling or resilience of an endpoint) and hence we need to allow such invalid comments in
    // the test API.
    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void draftCommentCanBeCreatedOnAutoMergeCommit() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .onAutoMergeCommit()
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void draftCommentCanBeCreatedAsResolved() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().resolved().create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).unresolved().isFalse();
  }

  @Test
  public void draftCommentCanBeCreatedAsUnresolved() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().unresolved().create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).unresolved().isTrue();
  }

  @Test
  public void draftReplyToDraftCommentCanBeCreated() {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newDraftComment().create();

    // Gerrit's other APIs shouldn't support the creation of a draft reply to a draft comment but
    // there's currently no reason to not support such a comment via the test API if a test really
    // wants to create such a comment.
    changeOperations
        .change(changeId)
        .currentPatchset()
        .newDraftComment()
        .parentUuid(parentCommentUuid)
        .create();
  }

  @Test
  public void draftReplyToPublishedCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newComment().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .parentUuid(parentCommentUuid)
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).inReplyTo().isEqualTo(parentCommentUuid);
  }

  @Test
  public void tagCanBeAttachedToADraftComment() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .tag("my special tag")
            .create();

    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).tag().isEqualTo("my special tag");
  }

  @Test
  public void draftCommentIsCreatedWithSpecifiedAuthor() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    Account.Id accountId = accountOperations.newAccount().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .author(accountId)
            .create();

    // A user can only retrieve their own draft comments.
    requestScopeOperations.setApiUser(accountId);
    List<CommentInfo> comments = getDraftCommentsFromServer(changeId);
    // Draft comments never have the author field set. As a user can only retrieve their own draft
    // comments, we implicitly know that the author was correctly set when we find the created
    // comment in the draft comments of that user.
    assertThatList(comments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void draftCommentIsCreatedWithSpecifiedCreationTime() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    // Don't use nanos. NoteDb supports only second precision.
    Instant creationTime =
        LocalDateTime.of(2020, Month.SEPTEMBER, 15, 12, 10, 43).atZone(ZoneOffset.UTC).toInstant();
    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .createdOn(creationTime)
            .create();

    Timestamp creationTimestamp = Timestamp.from(creationTime);
    CommentInfo comment = getDraftCommentFromServer(changeId, commentUuid);
    assertThat(comment).updated().isEqualTo(creationTimestamp);
  }

  @Test
  public void zoneOfCreationDateOfDraftCommentCanBeOmitted() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    // As we don't care about the exact time zone internally used as a default, do a relative test
    // so that we don't need to assert on exact instants in time. For a relative test, we need two
    // comments whose creation date should be exactly the specified amount apart.
    // Don't use nanos or millis. NoteDb supports only second precision.
    LocalDateTime creationTime1 = LocalDateTime.of(2020, Month.SEPTEMBER, 15, 12, 10, 43);
    LocalDateTime creationTime2 = creationTime1.plusMinutes(10);
    String commentUuid1 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .createdOn(creationTime1)
            .create();
    String commentUuid2 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newDraftComment()
            .createdOn(creationTime2)
            .create();

    CommentInfo comment1 = getDraftCommentFromServer(changeId, commentUuid1);
    Instant comment1Creation = comment1.updated.toInstant();
    CommentInfo comment2 = getDraftCommentFromServer(changeId, commentUuid2);
    Instant comment2Creation = comment2.updated.toInstant();
    Duration commentCreationDifference = Duration.between(comment1Creation, comment2Creation);
    assertThat(commentCreationDifference).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  public void noDraftCommentsAreCreatedOnCreationOfPublishedComment() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    changeOperations.change(changeId).currentPatchset().newComment().create();

    List<CommentInfo> comments = getDraftCommentsFromServer(changeId);
    assertThatList(comments).isEmpty();
  }

  @Test
  public void noPublishedCommentsAreCreatedOnCreationOfDraftComment() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    changeOperations.change(changeId).currentPatchset().newDraftComment().create();

    List<CommentInfo> comments = getCommentsFromServer(changeId);
    assertThatList(comments).isEmpty();
  }

  @Test
  public void robotCommentCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().create();
    List<RobotCommentInfo> robotComments = getRobotCommentsFromServerFromCurrentPatchset(changeId);
    assertThatList(robotComments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void robotCommentCanBeCreatedOnOlderPatchset() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    PatchSet.Id previousPatchsetId =
        changeOperations.change(changeId).currentPatchset().get().patchsetId();
    changeOperations.change(changeId).newPatchset().create();

    String commentUuid =
        changeOperations.change(changeId).patchset(previousPatchsetId).newRobotComment().create();

    CommentInfo comment = getRobotCommentFromServer(previousPatchsetId, commentUuid);
    assertThat(comment).uuid().isEqualTo(commentUuid);
  }

  @Test
  public void robotCommentIsCreatedWithSpecifiedMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .message("Test comment message")
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).message().isEqualTo("Test comment message");
  }

  @Test
  public void robotCommentCanBeCreatedWithEmptyMessage() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().noMessage().create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).message().isNull();
  }

  @Test
  public void patchsetLevelRobotCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onPatchsetLevel()
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).path().isEqualTo(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void fileRobotCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().file("file1").content("Line 1").create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onFileLevelOf("file1")
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).path().isEqualTo("file1");
    assertThat(comment).line().isNull();
    assertThat(comment).range().isNull();
  }

  @Test
  public void lineRobotCommentCanBeCreated() throws Exception {
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
            .newRobotComment()
            .onLine(3)
            .ofFile("file1")
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).line().isEqualTo(3);
    assertThat(comment).range().isNull();
  }

  @Test
  public void rangeRobotCommentCanBeCreated() throws Exception {
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
            .newRobotComment()
            .fromLine(2)
            .charOffset(4)
            .toLine(3)
            .charOffset(5)
            .ofFile("file1")
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).range().startLine().isEqualTo(2);
    assertThat(comment).range().startCharacter().isEqualTo(4);
    assertThat(comment).range().endLine().isEqualTo(3);
    assertThat(comment).range().endCharacter().isEqualTo(5);
    // Line is automatically filled from specified range. It's the end line.
    assertThat(comment).line().isEqualTo(3);
  }

  @Test
  public void robotCommentCanBeCreatedOnPatchsetCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onPatchsetCommit()
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    // Null is often used instead of Side.REVISION as Side.REVISION is the default.
    assertThat(comment).side().isAnyOf(Side.REVISION, null);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void robotCommentCanBeCreatedOnParentCommit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onParentCommit()
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(1);
  }

  @Test
  public void robotCommentCanBeCreatedOnSecondParentCommit() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onSecondParentCommit()
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void robotCommentCanBeCreatedOnNonExistingSecondParentCommit() throws Exception {
    Change.Id parentChangeId = changeOperations.newChange().create();
    Change.Id changeId = changeOperations.newChange().childOf().change(parentChangeId).create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onSecondParentCommit()
            .create();

    // We want to be able to create such invalid robot comments for testing purposes (e.g. testing
    // error handling or resilience of an endpoint) and hence we need to allow such invalid robot
    // comments in the test API.
    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isEqualTo(2);
  }

  @Test
  public void robotCommentCanBeCreatedOnAutoMergeCommit() throws Exception {
    Change.Id parent1ChangeId = changeOperations.newChange().create();
    Change.Id parent2ChangeId = changeOperations.newChange().create();
    Change.Id changeId =
        changeOperations
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .onAutoMergeCommit()
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).side().isEqualTo(Side.PARENT);
    assertThat(comment).parent().isNull();
  }

  @Test
  public void replyToRobotCommentCanBeCreated() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    String parentCommentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .parentUuid(parentCommentUuid)
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).inReplyTo().isEqualTo(parentCommentUuid);
  }

  @Test
  public void tagCanBeAttachedToARobotComment() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .tag("my special tag")
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).tag().isEqualTo("my special tag");
  }

  @Test
  public void robotCommentIsCreatedWithSpecifiedAuthor() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    Account.Id accountId = accountOperations.newAccount().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .author(accountId)
            .create();

    CommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).author().id().isEqualTo(accountId.get());
  }

  @Test
  public void robotCommentIsCreatedWithRobotId() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .robotId("robot-id")
            .create();

    RobotCommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).robotId().isEqualTo("robot-id");
  }

  @Test
  public void robotCommentIsCreatedWithRobotRunId() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .robotId("robot-run-id")
            .create();

    RobotCommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).robotId().isEqualTo("robot-run-id");
  }

  @Test
  public void robotCommentIsCreatedWithUrl() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations.change(changeId).currentPatchset().newRobotComment().url("url").create();

    RobotCommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).url().isEqualTo("url");
  }

  @Test
  public void robotCommentIsCreatedWithProperty() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();

    String commentUuid =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newRobotComment()
            .addProperty("key", "value")
            .create();

    RobotCommentInfo comment = getRobotCommentFromServerInCurrentPatchset(changeId, commentUuid);
    assertThat(comment).properties().containsExactly("key", "value");
  }

  private List<CommentInfo> getCommentsFromServer(Change.Id changeId) throws RestApiException {
    return gApi.changes().id(changeId.get()).commentsRequest().getAsList();
  }

  private List<RobotCommentInfo> getRobotCommentsFromServerFromCurrentPatchset(Change.Id changeId)
      throws RestApiException {
    return gApi.changes().id(changeId.get()).current().robotCommentsAsList();
  }

  private List<CommentInfo> getDraftCommentsFromServer(Change.Id changeId) throws RestApiException {
    return gApi.changes().id(changeId.get()).draftsAsList();
  }

  private CommentInfo getCommentFromServer(Change.Id changeId, String uuid)
      throws RestApiException {
    return gApi.changes().id(changeId.get()).commentsRequest().getAsList().stream()
        .filter(comment -> comment.id.equals(uuid))
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format("Comment %s not found on change %d", uuid, changeId.get())));
  }

  private RobotCommentInfo getRobotCommentFromServerInCurrentPatchset(
      Change.Id changeId, String uuid) throws RestApiException {
    return gApi.changes().id(changeId.get()).current().robotCommentsAsList().stream()
        .filter(comment -> comment.id.equals(uuid))
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Robot Comment %s not found on change %d on the latest patchset",
                        uuid, changeId.get())));
  }

  private RobotCommentInfo getRobotCommentFromServer(PatchSet.Id patchsetId, String uuid)
      throws RestApiException {
    return gApi.changes().id(patchsetId.changeId().toString())
        .revision(patchsetId.getId().toString()).robotCommentsAsList().stream()
        .filter(comment -> comment.id.equals(uuid))
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Robot Comment %s not found on change %d on patchset %d",
                        uuid, patchsetId.changeId().get(), patchsetId.get())));
  }

  private CommentInfo getDraftCommentFromServer(Change.Id changeId, String uuid)
      throws RestApiException {
    return gApi.changes().id(changeId.get()).draftsAsList().stream()
        .filter(comment -> comment.id.equals(uuid))
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Draft comment %s not found on change %d", uuid, changeId.get())));
  }

  private Correspondence<CommentInfo, String> hasUuid() {
    return NullAwareCorrespondence.transforming(comment -> comment.id, "hasUuid");
  }
}
