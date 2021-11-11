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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.MapSubject.assertThatMap;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.change.TestCommentCreation;
import com.google.gerrit.acceptance.testsuite.change.TestPatchset;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.ObjectId;
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
    String comment1Uuid = newComment(patchset1Id).create();
    newComment(patchset2Id).create();
    newComment(patchset3Id).create();

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
    String comment1Uuid = newComment(patchset1Id).create();
    String comment3Uuid = newComment(patchset3Id).create();

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
    String comment1Uuid = newComment(patchset1Id).create();
    String comment2Uuid = newComment(patchset1Id).create();

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
    String rootCommentUuid = newComment(patchset1Id).create();
    String child1CommentUuid = newComment(patchset1Id).parentUuid(rootCommentUuid).create();
    String child2CommentUuid = newComment(patchset1Id).parentUuid(child1CommentUuid).create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(rootCommentUuid, child1CommentUuid, child2CommentUuid);
  }

  @Test
  public void onlyUnresolvedPublishedCommentsArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    newComment(patchset1Id).resolved().create();
    String comment2Uuid = newComment(patchset1Id).unresolved().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(comment2Uuid);
  }

  @Test
  public void commentsArePortedWhenAllEditsAreDueToRebase() throws Exception {
    String fileName = "f.txt";
    String baseContent =
        IntStream.rangeClosed(1, 50)
            .mapToObj(number -> String.format("Line %d\n", number))
            .collect(joining());
    ObjectId headCommit = testRepo.getRepository().resolve("HEAD");
    ObjectId baseCommit = addCommit(headCommit, fileName, baseContent);

    // Create a change on top of baseCommit, modify line 1, then add comment on line 10
    PushOneCommit.Result r = createEmptyChange();
    Change.Id changeId = r.getChange().getId();
    addModifiedPatchSet(
        changeId.toString(), fileName, baseContent.replace("Line 1\n", "Line one\n"));
    PatchSet.Id ps2Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    newComment(ps2Id).message("Line comment").onLine(10).ofFile(fileName).create();

    // Add a commit on top of baseCommit. Delete line 4. Rebase the change on top of this commit.
    ObjectId newBase = addCommit(baseCommit, fileName, baseContent.replace("Line 4\n", ""));
    rebaseChangeOn(changeId.toString(), newBase);
    PatchSet.Id ps3Id = changeOps.change(changeId).currentPatchset().get().patchsetId();

    List<CommentInfo> portedComments = flatten(getPortedComments(ps3Id));
    assertThat(portedComments).hasSize(1);
    int portedLine = portedComments.get(0).line;
    BinaryResult fileContent = gApi.changes().id(changeId.get()).current().file(fileName).content();
    String[] lines = fileContent.asString().split("\n");
    assertThat(lines[portedLine - 1]).isEqualTo("Line 10");
  }

  private Result createEmptyChange() throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Test change", ImmutableMap.of());
    return push.to("refs/for/master");
  }

  private void rebaseChangeOn(String changeId, ObjectId newParent) throws Exception {
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = newParent.getName();
    // rebaseInput.allowConflicts = true;
    gApi.changes().id(changeId).current().rebase(rebaseInput);
  }

  private void addModifiedPatchSet(String changeId, String filePath, String content)
      throws Exception {
    gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(content));
    gApi.changes().id(changeId).edit().publish();
  }

  private ObjectId addCommit(ObjectId parentCommit, String fileName, String fileContent)
      throws Exception {
    testRepo.reset(parentCommit);
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Adjust files of repo",
            ImmutableMap.of(fileName, fileContent));
    PushOneCommit.Result result = push.to("refs/for/master");
    return result.getCommit();
  }

  @Test
  public void resolvedAndUnresolvedDraftCommentsArePorted() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String comment1Uuid = newDraftComment(patchset1Id).author(accountId).resolved().create();
    String comment2Uuid = newDraftComment(patchset1Id).author(accountId).unresolved().create();

    List<CommentInfo> portedComments =
        flatten(getPortedDraftCommentsOfUser(patchset2Id, accountId));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(comment1Uuid, comment2Uuid);
  }

  @Test
  public void unresolvedStateOfLastCommentInThreadMatters() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootComment1Uuid = newComment(patchset1Id).resolved().create();
    String childComment1Uuid =
        newComment(patchset1Id).parentUuid(rootComment1Uuid).unresolved().create();
    String rootComment2Uuid = newComment(patchset1Id).unresolved().create();
    newComment(patchset1Id).parentUuid(rootComment2Uuid).resolved().create();

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
    // Add comments. Comments should be more than 1 second apart as NoteDb only supports second
    // precision.
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String rootCommentUuid = newComment(patchset1Id).resolved().createdOn(now).create();
    String childComment1Uuid =
        newComment(patchset1Id)
            .parentUuid(rootCommentUuid)
            .resolved()
            .createdOn(now.plusSeconds(5))
            .create();
    String childComment2Uuid =
        newComment(patchset1Id)
            .parentUuid(rootCommentUuid)
            .unresolved()
            .createdOn(now.plusSeconds(10))
            .create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(rootCommentUuid, childComment1Uuid, childComment2Uuid);
  }

  @Test
  public void unresolvedStateOfDraftCommentsIsIgnoredForPublishedComments() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String rootComment1Uuid = newComment(patchset1Id).resolved().create();
    newDraftComment(patchset1Id)
        .author(accountId)
        .parentUuid(rootComment1Uuid)
        .unresolved()
        .create();
    String rootComment2Uuid = newComment(patchset1Id).unresolved().create();
    newDraftComment(patchset1Id).author(accountId).parentUuid(rootComment2Uuid).resolved().create();

    // Draft comments are only visible to their author.
    requestScopeOps.setApiUser(accountId);
    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(rootComment2Uuid);
  }

  @Test
  public void draftCommentsAreNotPortedViaApiForPublishedComments() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add draft comment.
    newDraftComment(patchset1Id).author(accountId).create();

    // Draft comments are only visible to their author.
    requestScopeOps.setApiUser(accountId);
    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThatList(portedComments).isEmpty();
  }

  @Test
  public void publishedCommentsAreNotPortedViaApiForDraftComments() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    newComment(patchset1Id).author(accountId).create();

    List<CommentInfo> portedComments =
        flatten(getPortedDraftCommentsOfUser(patchset2Id, accountId));

    assertThatList(portedComments).isEmpty();
  }

  @Test
  public void draftCommentCanBePorted() throws Exception {
    Account.Id accountId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add draft comment.
    newComment(patchset1Id).author(accountId).create();

    List<CommentInfo> portedComments =
        flatten(getPortedDraftCommentsOfUser(patchset2Id, accountId));

    assertThatList(portedComments).isEmpty();
  }

  @Test
  public void portedDraftCommentOfOtherUserIsNotVisible() throws Exception {
    Account.Id userId = accountOps.newAccount().create();
    Account.Id otherUserId = accountOps.newAccount().create();
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add draft comment.
    newComment(patchset1Id).author(otherUserId).create();

    List<CommentInfo> portedComments = flatten(getPortedDraftCommentsOfUser(patchset2Id, userId));

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
        newComment(patchset1Id)
            .message("Range comment")
            .fromLine(1)
            .charOffset(2)
            .toLine(2)
            .charOffset(1)
            .ofFile("myFile")
            .create();
    String lineCommentUuid =
        newComment(patchset1Id).message("Line comment").onLine(1).ofFile("myFile").create();
    String fileCommentUuid =
        newComment(patchset1Id).message("File comment").onFileLevelOf("myFile").create();
    String patchsetLevelCommentUuid =
        newComment(patchset1Id).message("Patchset-level comment").onPatchsetLevel().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(
            rangeCommentUuid, lineCommentUuid, fileCommentUuid, patchsetLevelCommentUuid);
  }

  @Test
  public void commentOnParentCommitIsPorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String commentUuid = newComment(patchset1Id).onParentCommit().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void commentOnInvalidParentIsPorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String commentUuid = newComment(patchset1Id).onSecondParentCommit().create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid);
  }

  @Test
  public void commentsOnInvalidPositionArePorted() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    String commentUuid1 = newComment(patchset1Id).onFileLevelOf("not-existing file").create();
    String commentUuid2 = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

    List<CommentInfo> portedComments = flatten(getPortedComments(patchset2Id));

    assertThat(portedComments)
        .comparingElementsUsing(hasUuid())
        .containsExactly(commentUuid1, commentUuid2);
  }

  @Test
  public void commentsOnInvalidPositionKeepTheirInvalidPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comments.
    newComment(patchset1Id).onFileLevelOf("not-existing file").create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly("not-existing file");
  }

  @Test
  public void portedCommentHasOriginalUuid() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newComment(patchset1Id).create();

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
    String commentUuid = newComment(patchset1Id).create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).patchSet().isEqualTo(patchset1Id.get());
  }

  @Test
  public void portedDraftCommentHasPatchsetFilled() throws Exception {
    // Set up change and patchsets.
    Account.Id authorId = accountOps.newAccount().create();
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newDraftComment(patchset1Id).author(authorId).create();

    Map<String, List<CommentInfo>> portedComments =
        getPortedDraftCommentsOfUser(patchset2Id, authorId);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);

    // We explicitly need to request that the patchset field is filled, which we could have missed
    // for drafts. -> Test that aspect. Don't verify the actual patchset number as that's already
    // covered by the previous test.
    assertThat(portedComment).patchSet().isNotNull();
  }

  @Test
  public void portedCommentHasOriginalPatchsetCommitId() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newComment(patchset1.patchsetId()).create();

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
    String commentUuid = newComment(patchset1.patchsetId()).message("My comment text").create();

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
    String rootCommentUuid = newComment(patchset1.patchsetId()).create();
    String childCommentUuid =
        newComment(patchset1.patchsetId()).parentUuid(rootCommentUuid).create();

    CommentInfo portedComment = getPortedComment(patchset2Id, childCommentUuid);

    assertThat(portedComment).inReplyTo().isEqualTo(rootCommentUuid);
  }

  @Test
  public void portedPublishedCommentHasOriginalAuthor() throws Exception {
    // Set up change and patchsets.
    Account.Id authorId = accountOps.newAccount().create();
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newComment(patchset1Id).author(authorId).create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).author().id().isEqualTo(authorId.get());
  }

  @Test
  public void anonymousUsersGetAuthExceptionForPortedDrafts() throws Exception {
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchsetId = changeOps.change(changeId).currentPatchset().get().patchsetId();

    requestScopeOps.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(patchsetId.changeId().get())
                    .revision(patchsetId.get())
                    .portedDrafts());
    assertThat(thrown)
        .hasMessageThat()
        .contains("requires authentication; only authenticated users can have drafts");
  }

  @Test
  public void portedDraftCommentHasNoAuthor() throws Exception {
    // Set up change and patchsets.
    Account.Id authorId = accountOps.newAccount().create();
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newDraftComment(patchset1Id).author(authorId).create();

    Map<String, List<CommentInfo>> portedComments =
        getPortedDraftCommentsOfUser(patchset2Id, authorId);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);

    // Authors of draft comments are never set.
    assertThat(portedComment).author().isNull();
  }

  @Test
  public void portedCommentHasOriginalTag() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    TestPatchset patchset1 = changeOps.change(changeId).currentPatchset().get();
    PatchSet.Id patchset2Id = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newComment(patchset1.patchsetId()).tag("My comment tag").create();

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
    String commentUuid = newComment(patchset1Id).create();

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
    String commentUuid = newComment(patchset1Id).create();

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
    String commentUuid = newComment(patchset1Id).onFileLevelOf("myFile").create();

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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
            .fromLine(1)
            .charOffset(2)
            .toLine(3)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
        newComment(patchset1Id)
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
  public void overlappingRangeCommentsArePortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1\nLine 2\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 2\n")
            .create();
    // Add comment.
    String commentUuid1 =
        newComment(patchset1Id)
            .fromLine(2)
            .charOffset(0)
            .toLine(2)
            .charOffset(3)
            .ofFile("myFile")
            .create();
    String commentUuid2 =
        newComment(patchset1Id)
            .fromLine(2)
            .charOffset(1)
            .toLine(2)
            .charOffset(4)
            .ofFile("myFile")
            .create();

    CommentInfo portedComment1 = getPortedComment(patchset2Id, commentUuid1);
    assertThat(portedComment1).range().startLine().isEqualTo(3);
    CommentInfo portedComment2 = getPortedComment(patchset2Id, commentUuid2);
    assertThat(portedComment2).range().startLine().isEqualTo(3);
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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(2).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(2).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(4).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(4).ofFile("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onLine(3).ofFile("myFile").create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).range().isNull();
    assertThat(portedComment).line().isNull();
  }

  @Test
  public void overlappingLineCommentsArePortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1\nLine 2\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps
            .change(changeId)
            .newPatchset()
            .file("myFile")
            .content("Line 1\nLine 1.1\nLine 2\n")
            .create();
    // Add comment.
    String commentUuid1 = newComment(patchset1Id).onLine(2).ofFile("myFile").create();
    String commentUuid2 = newComment(patchset1Id).onLine(2).ofFile("myFile").create();

    CommentInfo portedComment1 = getPortedComment(patchset2Id, commentUuid1);
    assertThat(portedComment1).line().isEqualTo(3);
    CommentInfo portedComment2 = getPortedComment(patchset2Id, commentUuid2);
    assertThat(portedComment2).line().isEqualTo(3);
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
    String commentUuid = newComment(patchset1Id).onFileLevelOf("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onFileLevelOf("myFile").create();

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
    newComment(patchset1Id).onFileLevelOf("myFile").create();

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
    String commentUuid = newComment(patchset1Id).onFileLevelOf("myFile").create();

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
    newComment(patchset1Id).onPatchsetLevel().create();

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
    newComment(patchset1Id).onPatchsetLevel().create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(patchset2Id);

    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void commentOnCommitMessageIsPortedToNewPosition() throws Exception {
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
        newComment(patchset1Id)
            // The /COMMIT_MSG file has a header of 6 lines, so the summary line is in line 7.
            // Place comment on 'Text 2' which is line 10.
            .onLine(10)
            .ofFile(Patch.COMMIT_MSG)
            .create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(11);
  }

  @Test
  public void commentOnParentIsPortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id parentChangeId = changeOps.newChange().file("myFile").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .childOf()
            .change(parentChangeId)
            .file("myFile")
            .content("Line one\n")
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id parentPatchset2Id =
        changeOps
            .change(parentChangeId)
            .newPatchset()
            .file("myFile")
            .content("Line 0\nLine 1\n")
            .create();
    PatchSet.Id childPatchset2Id =
        changeOps.change(childChangeId).newPatchset().parent().patchset(parentPatchset2Id).create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onParentCommit().onLine(1).ofFile("myFile").create();

    CommentInfo portedComment = getPortedComment(childPatchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void commentOnFirstParentIsPortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file2").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .file("file1")
            .content("Line one\n")
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id parent1Patchset2Id =
        changeOps
            .change(parent1ChangeId)
            .newPatchset()
            .file("file1")
            .content("Line 0\nLine 1\n")
            .create();
    PatchSet.Id childPatchset2Id =
        changeOps
            .change(childChangeId)
            .newPatchset()
            .parents()
            .patchset(parent1Patchset2Id)
            .and()
            .change(parent2ChangeId)
            .create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onParentCommit().onLine(1).ofFile("file1").create();

    CommentInfo portedComment = getPortedComment(childPatchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void commentOnSecondParentIsPortedToNewPosition() throws Exception {
    // Set up change and patchsets.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file2").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .file("file2")
            .content("Line one\n")
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id parent2Patchset2Id =
        changeOps
            .change(parent1ChangeId)
            .newPatchset()
            .file("file2")
            .content("Line 0\nLine 1\n")
            .create();
    PatchSet.Id childPatchset2Id =
        changeOps
            .change(childChangeId)
            .newPatchset()
            .parents()
            .change(parent1ChangeId)
            .and()
            .patchset(parent2Patchset2Id)
            .create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onSecondParentCommit().onLine(1).ofFile("file2").create();

    CommentInfo portedComment = getPortedComment(childPatchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void commentOnAutoMergeCommitIsPortedToNewPosition() throws Exception {
    // Set up change and patchsets. Use the same file so that there's a meaningful auto-merge
    // commit/diff.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id parent1Patchset2Id =
        changeOps
            .change(parent1ChangeId)
            .newPatchset()
            .file("file1")
            .content("Line 0\nLine 1\n")
            .create();
    PatchSet.Id parent2Patchset2Id =
        changeOps
            .change(parent1ChangeId)
            .newPatchset()
            .file("file1")
            .content("Line zero\nLine 1\n")
            .create();
    PatchSet.Id childPatchset2Id =
        changeOps
            .change(childChangeId)
            .newPatchset()
            .parents()
            .patchset(parent1Patchset2Id)
            .and()
            .patchset(parent2Patchset2Id)
            .create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onAutoMergeCommit().onLine(1).ofFile("file1").create();

    CommentInfo portedComment = getPortedComment(childPatchset2Id, commentUuid);

    // Merging the parents creates a conflict in the file. -> Several lines are added due to
    // conflict markers in the auto-merge commit. We don't care about the exact number, just that
    // the comment moved down several lines (instead of just one in each parent) and that the
    // porting logic hence used the auto-merge commit for its computation.
    assertThat(portedComment).line().isGreaterThan(2);
  }

  @Test
  public void commentOnFirstParentIsPortedToSingleParentWhenPatchsetChangedToNonMergeCommit()
      throws Exception {
    // Set up change and patchsets.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file2").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id parent1PatchsetId2 =
        changeOps
            .change(parent1ChangeId)
            .newPatchset()
            .file("file1")
            .content("Line 0\nLine 1\n")
            .create();
    PatchSet.Id childPatchset2Id =
        changeOps
            .change(childChangeId)
            .newPatchset()
            .parent()
            .patchset(parent1PatchsetId2)
            .create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onParentCommit().onLine(1).ofFile("file1").create();

    CommentInfo portedComment = getPortedComment(childPatchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
    assertThat(portedComment).side().isEqualTo(Side.PARENT);
    assertThat(portedComment).parent().isEqualTo(1);
  }

  @Test
  public void commentOnSecondParentBecomesPatchsetLevelCommentWhenPatchsetChangedToNonMergeCommit()
      throws Exception {
    // Set up change and patchsets.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file2").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id childPatchset2Id =
        changeOps.change(childChangeId).newPatchset().parent().change(parent1ChangeId).create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onSecondParentCommit().onLine(1).ofFile("file2").create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(childPatchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isNull();
    assertThat(portedComment).side().isNull();
    assertThat(portedComment).parent().isNull();
  }

  @Test
  // TODO(ghareeb): Adjust implementation in CommentsUtil to use the new auto-merge code instead of
  // PatchListCache#getOldId which returns the wrong result if a change isn't a merge commit.
  @Ignore
  public void
      commentOnAutoMergeCommitBecomesPatchsetLevelCommentWhenPatchsetChangedToNonMergeCommit()
          throws Exception {
    // Set up change and patchsets.
    Change.Id parent1ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id parent2ChangeId = changeOps.newChange().file("file1").content("Line 1\n").create();
    Change.Id childChangeId =
        changeOps
            .newChange()
            .mergeOf()
            .change(parent1ChangeId)
            .and()
            .change(parent2ChangeId)
            .create();
    PatchSet.Id childPatchset1Id =
        changeOps.change(childChangeId).currentPatchset().get().patchsetId();
    PatchSet.Id childPatchset2Id =
        changeOps.change(childChangeId).newPatchset().parent().change(parent1ChangeId).create();
    // Add comment.
    String commentUuid =
        newComment(childPatchset1Id).onAutoMergeCommit().onLine(1).ofFile("file1").create();

    Map<String, List<CommentInfo>> portedComments = getPortedComments(childPatchset2Id);
    assertThatMap(portedComments).keys().containsExactly(Patch.PATCHSET_LEVEL);
    CommentInfo portedComment = extractSpecificComment(portedComments, commentUuid);
    assertThat(portedComment).line().isNull();
    assertThat(portedComment).side().isNull();
    assertThat(portedComment).parent().isNull();
  }

  @Test
  public void whitespaceOnlyModificationsAreAlsoConsideredWhenPorting() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().file("myFile").content("Line 1\n").create();
    PatchSet.Id patchset1Id = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchset2Id =
        changeOps.change(changeId).newPatchset().file("myFile").content("\nLine 1\n").create();
    // Add comment.
    String commentUuid = newComment(patchset1Id).onLine(1).ofFile("myFile").create();

    CommentInfo portedComment = getPortedComment(patchset2Id, commentUuid);

    assertThat(portedComment).line().isEqualTo(2);
  }

  @Test
  public void deletedCommentContentIsNotCachedInPortedComments() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchsetId1 = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchsetId2 = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid = newComment(patchsetId1).message("Confidential content").create();

    getPortedComment(patchsetId2, commentUuid);
    gApi.changes()
        .id(changeId.get())
        .revision(patchsetId1.get())
        .comment(commentUuid)
        .delete(new DeleteCommentInput());
    CommentInfo portedComment = getPortedComment(patchsetId2, commentUuid);

    assertThat(portedComment).message().doesNotContain("Confidential content");
  }

  @Test
  public void setOfPortedCommentsCanChangeOnRepeatedCalls() throws Exception {
    // Set up change and patchsets.
    Change.Id changeId = changeOps.newChange().create();
    PatchSet.Id patchsetId1 = changeOps.change(changeId).currentPatchset().get().patchsetId();
    PatchSet.Id patchsetId2 = changeOps.change(changeId).newPatchset().create();
    // Add comment.
    String commentUuid1 = newComment(patchsetId1).unresolved().create();

    ImmutableList<CommentInfo> pastPortedComments = flatten(getPortedComments(patchsetId2));
    // Set the existing comment thread to resolved, so it won't be ported anymore.
    newComment(patchsetId1).parentUuid(commentUuid1).resolved().create();
    // Create a new comment which should show up as ported comment.
    String commentUuid2 = newComment(patchsetId1).create();
    ImmutableList<CommentInfo> portedComments = flatten(getPortedComments(patchsetId2));

    // Ensure that results are not cached between calls. This should not be necessary as the diffs
    // are already cached. If we need to also cache the ported comments in the future, we'll need to
    // identify ALL situations when the set of ported comments changes.
    assertThat(portedComments).isNotEqualTo(pastPortedComments);
    assertThat(portedComments).comparingElementsUsing(hasUuid()).containsExactly(commentUuid2);
  }

  private TestCommentCreation.Builder newComment(PatchSet.Id patchsetId) {
    // Create unresolved comments by default as only those are ported. Tests get override the
    // unresolved state by explicitly setting it.
    return changeOps.change(patchsetId.changeId()).patchset(patchsetId).newComment().unresolved();
  }

  private TestCommentCreation.Builder newDraftComment(PatchSet.Id patchsetId) {
    // Create unresolved comments by default as only those are ported. Tests get override the
    // unresolved state by explicitly setting it.
    return changeOps
        .change(patchsetId.changeId())
        .patchset(patchsetId)
        .newDraftComment()
        .unresolved();
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

  private Map<String, List<CommentInfo>> getPortedDraftCommentsOfUser(
      PatchSet.Id patchsetId, Account.Id accountId) throws RestApiException {
    // Draft comments are only visible to their author.
    requestScopeOps.setApiUser(accountId);
    return gApi.changes().id(patchsetId.changeId().get()).revision(patchsetId.get()).portedDrafts();
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
