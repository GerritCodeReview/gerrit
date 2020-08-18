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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.CommentInfoSubject.assertThatList;
import static com.google.gerrit.truth.MapSubject.assertThatMap;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestPatchset;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;

public class PortedCommentsIT extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOps;
  @Inject private AccountOperations accountOps;
  @Inject private RequestScopeOperations requestScopeOps;

  @Test
  public void onlyCommentsBeforeTargetPatchsetArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    PatchSet.Id patchset3Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String comment1Uuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();
    changeOps.change(changeId).patchset(patchset2Id).newComment().create();
    changeOps.change(changeId).patchset(patchset3Id).newComment().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThatList(portedComments).comparingElementsUsing(hasUuid()).containsExactly(comment1Uuid);
  }

  @Test
  public void commentsOnAnyPatchsetBeforeTargetPatchsetArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    changeOps.change(changeId).newPatchset().create();
    PatchSet.Id patchset3Id = changeOps.change(changeId).newPatchset().create();
    PatchSet.Id patchset4Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String comment1Uuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();
    String comment3Uuid = changeOps.change(changeId).patchset(patchset3Id).newComment().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset4Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(comment1Uuid, comment3Uuid);
  }

  @Test
  public void severalCommentsFromEarlierPatchsetArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String comment1Uuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();
    String comment2Uuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(comment1Uuid, comment2Uuid);
  }

  @Test
  public void completeCommentThreadIsPorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootCommentUuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();
    String child1CommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .parentUuid(rootCommentUuid)
            .create();
    String child2CommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .parentUuid(child1CommentUuid)
            .create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(rootCommentUuid, child1CommentUuid, child2CommentUuid);
  }

  @Test
  // TODO(aliceks): Filter out unresolved comment threads.
  @Ignore
  public void onlyUnresolvedCommentsArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    changeOps.change(changeId).patchset(patchset1Id).newComment().resolved().create();
    String comment2Uuid =
        changeOps.change(changeId).patchset(patchset1Id).newComment().unresolved().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(comment2Uuid);
  }

  @Test
  // TODO(aliceks): Filter out unresolved comment threads.
  @Ignore
  public void unresolvedStateOfLastCommentInThreadMatters() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootComment1Uuid =
        changeOps.change(changeId).patchset(patchset1Id).newComment().resolved().create();
    String childComment1Uuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .parentUuid(rootComment1Uuid)
            .unresolved()
            .create();
    String rootComment2Uuid =
        changeOps.change(changeId).patchset(patchset1Id).newComment().unresolved().create();
    changeOps
        .change(changeId)
        .patchset(patchset1Id)
        .newComment()
        .parentUuid(rootComment2Uuid)
        .resolved()
        .create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(rootComment1Uuid, childComment1Uuid);
  }

  @Test
  public void unresolvedStateOfLastCommentByDateMattersForBranchedThreads() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootCommentUuid =
        changeOps.change(changeId).patchset(patchset1Id).newComment().resolved().create();
    String childComment1Uuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .parentUuid(rootCommentUuid)
            .resolved()
            .create();
    String childComment2Uuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .parentUuid(rootCommentUuid)
            .unresolved()
            .create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(rootCommentUuid, childComment1Uuid, childComment2Uuid);
  }

  @Test
  public void draftCommentsAreNotPortedViaApiForPublishedComments() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add draft comment.
    changeOps.change(changeId).patchset(patchset1Id).newDraftComment().author(accountId).create();

    // Draft comments are only visible to their author.
    requestScopeOps.setApiUser(accountId);
    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThatList(portedComments).isEmpty();
  }

  @Test
  public void publishedCommentsOfAllTypesArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1\nLine 2\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rangeCommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .message("Range comment")
            .fromLine(1)
            .charOffset(2)
            .toLine(2)
            .charOffset(1)
            .ofFile("myFile")
            .create();
    String lineCommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .message("Line comment")
            .onLine(1)
            .ofFile("myFile")
            .create();
    String fileCommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .message("File comment")
            .onFileLevelOf("myFile")
            .create();
    String patchsetLevelCommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .message("Patchset-level comment")
            .onPatchsetLevel()
            .create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(
            rangeCommentUuid, lineCommentUuid, fileCommentUuid, patchsetLevelCommentUuid);
  }

  // This is not the desired behavior but at least a current state which doesn't throw exceptions
  // or has wrong behavior.
  @Test
  public void commentOnParentCommitIsIgnored() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    changeOps.change(changeId).patchset(patchset1Id).newComment().onParentCommit().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).isEmpty();
  }

  @Test
  public void portedCommentHasOriginalUuid() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThatList(portedComments).onlyElement().uuid().isEqualTo(commentUuid);
  }

  @Test
  public void portedCommentHasOriginalPatchset() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).patchSet().isEqualTo(patchset1Id.get());
  }

  @Test
  public void portedCommentHasOriginalPatchsetCommitId() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid =
        changeOps.change(changeId).patchset(patchset1.patchsetId()).newComment().create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).commitId().isEqualTo(patchset1.commitId().name());
  }

  @Test
  public void portedCommentHasOriginalMessage() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1.patchsetId())
            .newComment()
            .message("My comment text")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).message().isEqualTo("My comment text");
  }

  @Test
  public void portedReplyStillRefersToParentComment() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootCommentUuid =
        changeOps.change(changeId).patchset(patchset1.patchsetId()).newComment().create();
    String childCommentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1.patchsetId())
            .newComment()
            .parentUuid(rootCommentUuid)
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, childCommentUuid);

    assertThat(portedComment).inReplyTo().isEqualTo(rootCommentUuid);
  }

  @Test
  public void portedCommentHasOriginalAuthor() throws Exception {
    // Set up change and patchsets.
    Account.Id authorId = accountOps.newAccount().create();
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid =
        changeOps.change(changeId).patchset(patchset1Id).newComment().author(authorId).create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).author().id().isEqualTo(authorId.get());
  }

  @Test
  public void portedCommentHasOriginalTag() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1.patchsetId())
            .newComment()
            .tag("My comment tag")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).tag().isEqualTo("My comment tag");
  }

  @Test
  public void portedCommentHasUpdatedTimestamp() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).updated().isNotNull();
  }

  @Test
  public void portedCommentDoesNotHaveChangeMessageId() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = changeOps.change(changeId).patchset(patchset1Id).newComment().create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    // There's currently no use case for linking ported comments to specific change messages. Hence,
    // there's no reason to fill this field, which requires additional computations.
    // Besides, we also don't fill this field for the comments requested for a specific patchset.
    assertThat(portedComment).changeMessageId().isNull();
  }

  @Test
  public void pathOfPortedCommentIsOnlyIndicatedInMap() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onFileLevelOf("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("myFile");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).path().isNull();
  }

  @Test
  public void portedRangeCommentCanHandleAddedLines() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().startLine().isEqualTo(5);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(6);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void portedRangeCommentCanHandleDeletedLines() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().startLine().isEqualTo(2);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(3);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void portedRangeCommentCanHandlePureRename() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").renameTo("newFileName").create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("newFileName");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().startLine().isEqualTo(3);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(4);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void portedRangeCommentCanHandleRenameWithLineShift() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .delete()
            .file("newFileName")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("newFileName");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().startLine().isEqualTo(5);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(6);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void portedRangeCommentAdditionallyAppearsOnCopyAtIndependentPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    // Gerrit currently only identifies a copy if a rename also happens at the same time. Modify the
    // renamed file slightly different than the copied file so that the end location of the comment
    // is different. Modify the renamed file less so that Gerrit/Git picks it as the renamed one.
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .delete()
            .file("renamedFiled")
            .content("Line 1\nLine 1.1\nLine 2\nLine 3\nLine 4\n")
            .file("copiedFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("renamedFiled", "copiedFile");
    CommentInfo portedCommentOnRename = getOnlyElement(portedComments.get("renamedFiled"));
    assertThat(portedCommentOnRename).uuid().isEqualTo(commentUuid);
    assertThat(portedCommentOnRename).range().startLine().isEqualTo(4);
    assertThat(portedCommentOnRename).range().startCharacter().isEqualTo(2);
    assertThat(portedCommentOnRename).range().endLine().isEqualTo(5);
    assertThat(portedCommentOnRename).range().endCharacter().isEqualTo(5);
    CommentInfo portedCommentOnCopy = getOnlyElement(portedComments.get("copiedFile"));
    assertThat(portedCommentOnCopy).uuid().isEqualTo(commentUuid);
    assertThat(portedCommentOnCopy).range().startLine().isEqualTo(5);
    assertThat(portedCommentOnCopy).range().startCharacter().isEqualTo(2);
    assertThat(portedCommentOnCopy).range().endLine().isEqualTo(6);
    assertThat(portedCommentOnCopy).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void lineOfPortedRangeCommentFollowsContract() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    // Line is equal to the end line, which is at 6 when ported.
    assertThat(portedComment).line().isEqualTo(6);
  }

  @Test
  public void portedRangeCommentBecomesFileCommentOnConflict() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine two\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(2)
            .charOffset(2)
            .toLine(3)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("myFile");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedRangeCommentEndingOnLineJustBeforeModificationCanBePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(1)
            .charOffset(2)
            .toLine(2)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().startLine().isEqualTo(1);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(2);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  @Test
  public void portedRangeCommentEndingAtStartOfModifiedLineCanBePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(1)
            .charOffset(2)
            .toLine(3)
            .charOffset(0)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().startLine().isEqualTo(1);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(3);
    assertThat(portedComment).range().endCharacter().isEqualTo(0);
  }

  @Test
  public void portedRangeCommentEndingWithinModifiedLineBecomesFileComment() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(1)
            .charOffset(2)
            .toLine(4)
            .charOffset(4)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedRangeCommentWithinModifiedLineBecomesFileComment() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(3)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedRangeCommentStartingWithinLastModifiedLineBecomesFileComment()
      throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line one\nLine two\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(2)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedRangeCommentStartingOnLineJustAfterModificationCanBePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine two\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().startLine().isEqualTo(3);
    assertThat(portedComment).range().startCharacter().isEqualTo(2);
    assertThat(portedComment).range().endLine().isEqualTo(4);
    assertThat(portedComment).range().endCharacter().isEqualTo(5);
  }

  // We could actually do better in such a situation but that involves some careful improvements
  // which would need to be covered with even more tests (e.g. several modifications could be within
  // the comment range; several comments could surround it; other modifications could have occurred
  // in the file so that start is shifted too but different than end). That's why we go for the
  // simple solution now (-> just map to file comment).
  @Test
  public void portedRangeCommentStartingBeforeButEndingAfterModifiedLineBecomesFileComment()
      throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(2)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedRangeCommentBecomesPatchsetLevelCommentOnFileDeletion() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").delete().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .fromLine(3)
            .charOffset(2)
            .toLine(4)
            .charOffset(5)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedLineCommentCanHandleAddedLines() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(5);
  }

  @Test
  public void portedLineCommentCanHandleDeletedLines() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void portedLineCommentCanHandlePureRename() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").renameTo("newFileName").create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("newFileName");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isEqualTo(3);
  }

  @Test
  public void portedLineCommentCanHandleRenameWithLineShift() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    changeOps
        .change(changeId)
        .newPatchset()
        .file("myFile")
        .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
        .create();
    PatchSet.Id patchset3Id =
        changeOps.change(changeId).newPatchset().file("myFile").renameTo("newFileName").create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset3Id);

    assertThatMap(portedComments).keys().containsExactly("newFileName");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isEqualTo(5);
  }

  @Test
  public void portedLineCommentAdditionallyAppearsOnCopyAtIndependentPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    // Gerrit currently only identifies a copy if a rename also happens at the same time. Modify the
    // renamed file slightly different than the copied file so that the end location of the comment
    // is different. Modify the renamed file less so that Gerrit/Git picks it as the renamed one.
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .delete()
            .file("renamedFiled")
            .content("Line 1\nLine 1.1\nLine 2\nLine 3\nLine 4\n")
            .file("copiedFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("renamedFiled", "copiedFile");
    CommentInfo portedCommentOnRename = getOnlyElement(portedComments.get("renamedFiled"));
    assertThat(portedCommentOnRename).uuid().isEqualTo(commentUuid);
    assertThat(portedCommentOnRename).line().isEqualTo(4);
    CommentInfo portedCommentOnCopy = getOnlyElement(portedComments.get("copiedFile"));
    assertThat(portedCommentOnCopy).uuid().isEqualTo(commentUuid);
    assertThat(portedCommentOnCopy).line().isEqualTo(5);
  }

  @Test
  public void portedLineCommentBecomesFileCommentOnConflict() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine two\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(2)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("myFile");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedLineCommentOnLineJustBeforeModificationCanBePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(2)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void portedLineCommentOnStartLineOfModificationBecomesFileComment() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nSome completely\ndifferent\ncontent\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedLineCommentOnLastLineOfModificationBecomesFileComment() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nSome completely\ndifferent\ncontent\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(4)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedLineCommentOnLineJustAfterModificationCanBePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 2\nLine three\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(4)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(4);
  }

  @Test
  public void portedLineCommentBecomesPatchsetLevelCommentOnFileDeletion() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").delete().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onLine(3)
            .ofFile("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedFileCommentIsObliviousToAdjustedFileContent() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onFileLevelOf("myFile")
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedFileCommentCanHandleRename() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").renameTo("newFileName").create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onFileLevelOf("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("newFileName");
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedFileCommentAdditionallyAppearsOnCopy() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .renameTo("renamedFiled")
            .file("copiedFile")
            .content("Line 1\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    changeOps.change(changeId).patchset(patchset1Id).newComment().onFileLevelOf("myFile").create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("renamedFiled", "copiedFile");
    CommentInfo portedCommentOnCopy = getOnlyElement(portedComments.get("copiedFile"));
    assertThat(portedCommentOnCopy).line().isNull();
  }

  @Test
  public void portedFileCommentBecomesPatchsetLevelCommentOnFileDeletion() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").delete().create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            .onFileLevelOf("myFile")
            .create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void portedPatchsetLevelCommentIsObliviousToAdjustedFileContent() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 1.2\nLine 2\nLine 3\nLine 4\n")
            .create();
    // Add comment.
    changeOps.change(changeId).patchset(patchset1Id).newComment().onPatchsetLevel().create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void portedPatchsetLevelCommentIsObliviousToRename() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().file("myFile").content("Line 1\nLine 2\nLine 3\nLine 4\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").renameTo("newFileName").create();
    // Add comment.
    changeOps.change(changeId).patchset(patchset1Id).newComment().onPatchsetLevel().create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void lineCommentOnCommitMessageIsPortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId =
        changeOps.newChange().commitMessage("Summary line\n\nText 1\nText 2").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .commitMessage("Summary line\n\nText 1\nText 1.1\nText 2")
            .create();
    // Add comment.
    String commentUuid =
        changeOps
            .change(changeId)
            .patchset(patchset1Id)
            .newComment()
            // The /COMMIT_MSG file has a header of 6 lines, so the summary line is in line 7.
            // Place comment on 'Text 2' which is line 10.
            .onLine(10)
            .ofFile(Patch.COMMIT_MSG)
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(11);
  }

  private CommentInfo getPortedComment(PatchSet.Id patchsetId, String commentUuid)
      throws RestApiException {
    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchsetId);
    return extractSpecificComment(portedComments, commentUuid);
  }

  private Map<String, List<CommentInfo>> getPortedComments(PatchSet.Id patchsetId)
      throws RestApiException {
    return gApi.changes()
        .id(patchsetId.changeId().get())
        .revision(patchsetId.get())
        .portedComments();
  }

  private static CommentInfo extractSpecificComment(
      Map<String, List<CommentInfo>> portedComments, String commentUuid) {
    return portedComments.values().stream()
        .flatMap(Collection::stream)
        .filter(comment -> comment.id.equals(commentUuid))
        .collect(onlyElement());
  }

  /**
   * Returns all comments in one list. The map keys (= file paths) are simply ignored. The returned
   * comments won't have the file path attribute set for them as they came from a map with that
   * attribute as key (= established Gerrit behavior).
   */
  private static ImmutableList<CommentInfo> flatten(
      Map<String, List<CommentInfo>> commentsPerFile) {
    return commentsPerFile.values().stream()
        .flatMap(Collection::stream)
        .collect((toImmutableList()));
  }

  // Unfortunately, we don't get an absolutely helpful error message when using this correspondence
  // as CommentInfo doesn't have a toString() implementation. Even if we added it, the string
  // representation would be quite unwieldy due to the huge number of comment attributes.
  // Interestingly, using Correspondence#formattingDiffsUsing didn't improve anything.
  private static Correspondence<CommentInfo, String> hasUuid() {
    return NullAwareCorrespondence.transforming(comment -> comment.id, "hasUuid");
  }
}
