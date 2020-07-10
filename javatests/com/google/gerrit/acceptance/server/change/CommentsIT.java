// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.truth.MapSubject.assertThatMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.DeleteCommentRewriter;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CommentsIT extends AbstractDaemonTest {
  @Inject private ChangeNoteUtil noteUtil;
  @Inject private FakeEmailSender email;
  @Inject private ProjectOperations projectOperations;
  @Inject private Provider<ChangesCollection> changes;
  @Inject private Provider<PostReview> postReview;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private CommentsUtil commentsUtil;

  private final Integer[] lines = {0, 1};

  @Before
  public void setUp() {
    requestScopeOperations.setApiUser(user.id());
  }

  @Test
  public void getNonExistingComment() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThrows(
        ResourceNotFoundException.class,
        () -> getPublishedComment(changeId, revId, "non-existing"));
  }

  @Test
  public void createDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      assertThat(result).hasSize(1);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));

      List<CommentInfo> list = getDraftCommentsAsList(changeId);
      assertThat(list).hasSize(1);
      actual = list.get(0);
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
    }
  }

  @Test
  public void createDraftOnMergeCommitChange() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput c1 = newDraft(path, Side.REVISION, line, "ps-1");
      DraftInput c2 = newDraft(path, Side.PARENT, line, "auto-merge of ps-1");
      DraftInput c3 = newDraftOnParent(path, 1, line, "parent-1 of ps-1");
      DraftInput c4 = newDraftOnParent(path, 2, line, "parent-2 of ps-1");
      addDraft(changeId, revId, c1);
      addDraft(changeId, revId, c2);
      addDraft(changeId, revId, c3);
      addDraft(changeId, revId, c4);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      assertThat(result).hasSize(1);
      assertThat(result.get(path).stream().map(infoToDraft(path))).containsExactly(c1, c2, c3, c4);

      List<CommentInfo> list = getDraftCommentsAsList(changeId);
      assertThat(list).hasSize(4);
      assertThat(list.stream().map(infoToDraft(path))).containsExactly(c1, c2, c3, c4);
    }
  }

  @Test
  public void postComment() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(admin.newIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void patchsetLevelCommentCanBeAddedAndRetrieved() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addComments(changeId, ps1, comment);

    Map<String, List<CommentInfo>> results = getPublishedComments(changeId, ps1);
    assertThatMap(results).keys().containsExactly(PATCHSET_LEVEL);
  }

  @Test
  public void deletePatchsetLevelComment() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    String commentMessage = "to be deleted";
    CommentInput comment = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, commentMessage);
    addComments(changeId, revId, comment);

    Map<String, List<CommentInfo>> results = getPublishedComments(changeId, revId);
    CommentInfo oldComment = Iterables.getOnlyElement(results.get(PATCHSET_LEVEL));

    DeleteCommentInput input = new DeleteCommentInput("reason");
    gApi.changes().id(changeId).revision(revId).comment(oldComment.id).delete(input);
    CommentInfo updatedComment =
        Iterables.getOnlyElement(getPublishedComments(changeId, revId).get(PATCHSET_LEVEL));

    assertThat(updatedComment.message).doesNotContain(commentMessage);
  }

  @Test
  public void patchsetLevelCommentEmailNotification() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput comment = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addComments(changeId, ps1, comment);

    String emailBody = Iterables.getOnlyElement(email.getMessages()).body();
    assertThat(emailBody).contains("Patchset");
    assertThat(emailBody).doesNotContain("/PATCHSET_LEVEL");
  }

  @Test
  public void patchsetLevelCommentCantHaveLine() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput input = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    input.line = 1;
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addComments(changeId, ps1, input));
    assertThat(ex.getMessage()).contains("line");
  }

  @Test
  public void patchsetLevelCommentCantHaveRange() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput input = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    input.range = createLineRange(1, 3);
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addComments(changeId, ps1, input));
    assertThat(ex.getMessage()).contains("range");
  }

  @Test
  public void patchsetLevelCommentCantHaveSide() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput input = newCommentWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    input.side = Side.REVISION;
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addComments(changeId, ps1, input));
    assertThat(ex.getMessage()).contains("side");
  }

  @Test
  public void patchsetLevelDraftCommentCanBeAddedAndRetrieved() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> results = getDraftComments(changeId, revId);
    assertThatMap(results).keys().containsExactly(PATCHSET_LEVEL);
  }

  @Test
  public void deletePatchsetLevelDraft() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput draft = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment 1");
    CommentInfo returned = addDraft(changeId, revId, draft);
    deleteDraft(changeId, revId, returned.id);
    Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
    assertThat(drafts).isEmpty();
  }

  @Test
  public void patchsetLevelDraftCommentCantHaveLine() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    comment.line = 1;
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addDraft(changeId, revId, comment));
    assertThat(ex.getMessage()).contains("line");
  }

  @Test
  public void patchsetLevelDraftCommentCantHaveRange() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    comment.range = createLineRange(1, 3);
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addDraft(changeId, revId, comment));
    assertThat(ex.getMessage()).contains("range");
  }

  @Test
  public void patchsetLevelDraftCommentCantHaveSide() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    comment.side = Side.REVISION;
    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> addDraft(changeId, revId, comment));
    assertThat(ex.getMessage()).contains("range");
  }

  @Test
  public void patchsetLevelDraftCommentCantBeUpdatedToHaveLine() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> results = getDraftComments(changeId, revId);
    DraftInput update = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    update.id = Iterables.getOnlyElement(results.get(PATCHSET_LEVEL)).id;
    update.line = 1;
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> updateDraft(changeId, revId, update, update.id));
    assertThat(ex.getMessage()).contains("line");
  }

  @Test
  public void patchsetLevelDraftCommentCantBeUpdatedToHaveRange() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> results = getDraftComments(changeId, revId);
    DraftInput update = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    update.id = Iterables.getOnlyElement(results.get(PATCHSET_LEVEL)).id;
    update.range = createLineRange(1, 3);
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> updateDraft(changeId, revId, update, update.id));
    assertThat(ex.getMessage()).contains("range");
  }

  @Test
  public void patchsetLevelDraftCommentCantBeUpdatedToHaveSide() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    addDraft(changeId, revId, comment);
    Map<String, List<CommentInfo>> results = getDraftComments(changeId, revId);
    DraftInput update = newDraftWithOnlyMandatoryFields(PATCHSET_LEVEL, "comment");
    update.id = Iterables.getOnlyElement(results.get(PATCHSET_LEVEL)).id;
    update.side = Side.REVISION;
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> updateDraft(changeId, revId, update, update.id));
    assertThat(ex.getMessage()).contains("side");
  }

  @Test
  public void postCommentWithReply() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(admin.newIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));

      input = new ReviewInput();
      comment = newComment(file, Side.REVISION, line, "comment 1 reply", false);
      comment.inReplyTo = actual.id;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      result = getPublishedComments(changeId, revId);
      actual = result.get(comment.path).get(1);
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void postCommentWithUnresolved() throws Exception {
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(admin.newIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", true);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToInput(file).apply(actual));
      assertThat(comment)
          .isEqualTo(infoToInput(file).apply(getPublishedComment(changeId, revId, actual.id)));
    }
  }

  @Test
  public void postCommentOnMergeCommitChange() throws Exception {
    for (Integer line : lines) {
      String file = "foo";
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master", file);
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput c1 = newComment(file, Side.REVISION, line, "ps-1", false);
      CommentInput c2 = newComment(file, Side.PARENT, line, "auto-merge of ps-1", false);
      CommentInput c3 = newCommentOnParent(file, 1, line, "parent-1 of ps-1");
      CommentInput c4 = newCommentOnParent(file, 2, line, "parent-2 of ps-1");
      input.comments = new HashMap<>();
      input.comments.put(file, ImmutableList.of(c1, c2, c3, c4));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      assertThat(result.get(file).stream().map(infoToInput(file))).containsExactly(c1, c2, c3, c4);

      List<CommentInfo> list = getPublishedCommentsAsList(changeId);
      assertThat(list.stream().map(infoToInput(file))).containsExactly(c1, c2, c3, c4);
    }

    // for the commit message comments on the auto-merge are not possible
    for (Integer line : lines) {
      String file = Patch.COMMIT_MSG;
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      ReviewInput input = new ReviewInput();
      CommentInput c1 = newComment(file, Side.REVISION, line, "ps-1", false);
      CommentInput c2 = newCommentOnParent(file, 1, line, "parent-1 of ps-1");
      CommentInput c3 = newCommentOnParent(file, 2, line, "parent-2 of ps-1");
      input.comments = new HashMap<>();
      input.comments.put(file, ImmutableList.of(c1, c2, c3));
      revision(r).review(input);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      assertThat(result.get(file).stream().map(infoToInput(file))).containsExactly(c1, c2, c3);

      List<CommentInfo> list = getPublishedCommentsAsList(changeId);
      assertThat(list.stream().map(infoToInput(file))).containsExactly(c1, c2, c3);
    }
  }

  @Test
  public void postCommentOnCommitMessageOnAutoMerge() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master");
    ReviewInput input = new ReviewInput();
    CommentInput c = newComment(Patch.COMMIT_MSG, Side.PARENT, 0, "comment on auto-merge", false);
    input.comments = new HashMap<>();
    input.comments.put(Patch.COMMIT_MSG, ImmutableList.of(c));
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> revision(r).review(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("cannot comment on " + Patch.COMMIT_MSG + " on auto-merge");
  }

  @Test
  public void postCommentsReplacingDrafts() throws Exception {
    String file = "file";
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "first subject", file, "contents");
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();

    DraftInput draft = newDraft(file, Side.REVISION, 0, "comment");
    addDraft(changeId, revId, draft);
    Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
    CommentInfo draftInfo = Iterables.getOnlyElement(drafts.get(draft.path));

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.KEEP;
    reviewInput.message = "foo";
    CommentInput comment = newComment(file, Side.REVISION, 0, "comment", false);
    // Replace the existing draft.
    comment.id = draftInfo.id;
    reviewInput.comments = new HashMap<>();
    reviewInput.comments.put(comment.path, ImmutableList.of(comment));
    revision(r).review(reviewInput);

    // DraftHandling.KEEP is ignored on publishing a comment.
    drafts = getDraftComments(changeId, revId);
    assertThat(drafts).isEmpty();
  }

  @Test
  public void postCommentsUnreachableData() throws Exception {
    String file = "file";
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "first subject", file, "l1\nl2\n");

    String dest = "refs/for/master";
    PushOneCommit.Result r1 = push.to(dest);
    r1.assertOkStatus();
    String changeId = r1.getChangeId();
    String revId = r1.getCommit().getName();

    PushOneCommit.Result r2 = amendChange(r1.getChangeId());
    r2.assertOkStatus();

    String draftRefName = RefNames.refsDraftComments(r1.getChange().getId(), admin.id());

    DraftInput draft = newDraft(file, Side.REVISION, 1, "comment");
    addDraft(changeId, "1", draft);
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH;
    reviewInput.message = "foo";
    gApi.changes().id(r1.getChangeId()).revision(1).review(reviewInput);

    addDraft(changeId, "2", newDraft(file, Side.REVISION, 2, "comment2"));
    reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "bar";
    gApi.changes().id(r1.getChangeId()).revision(2).review(reviewInput);

    Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
    assertThat(drafts.isEmpty()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(draftRefName);
      assertThat(ref).isNull();
    }
  }

  @Test
  public void listComments() throws Exception {
    String file = "file";
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "first subject", file, "contents");
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getPublishedComments(changeId, revId)).isEmpty();
    assertThat(getPublishedCommentsAsList(changeId)).isEmpty();

    List<CommentInput> expectedComments = new ArrayList<>();
    for (Integer line : lines) {
      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment " + line, false);
      expectedComments.add(comment);
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      revision(r).review(input);
    }

    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(actualComments.stream().map(infoToInput(file)))
        .containsExactlyElementsIn(expectedComments);

    List<CommentInfo> list = getPublishedCommentsAsList(changeId);
    assertThat(list.stream().map(infoToInput(file))).containsExactlyElementsIn(expectedComments);
  }

  /**
   * This test makes sure that the commits in the refs/draft-comments ref in NoteDb have no parent
   * commits. This is important so that each new draft update (add, modify, delete) does not keep
   * track of previous history.
   */
  @Test
  public void commitsInDraftCommentsRefHaveNoParent() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    String draftRefName = RefNames.refsDraftComments(r.getChange().getId(), user.id());

    DraftInput comment1 = newDraft("file_1", Side.REVISION, 1, "comment 1");
    CommentInfo commentInfo1 = addDraft(changeId, revId, comment1);
    assertThat(getHeadOfDraftCommentsRef(draftRefName).getParentCount()).isEqualTo(0);

    DraftInput comment2 = newDraft("file_2", Side.REVISION, 2, "comment 2");
    CommentInfo commentInfo2 = addDraft(changeId, revId, comment2);
    assertThat(getHeadOfDraftCommentsRef(draftRefName).getParentCount()).isEqualTo(0);

    deleteDraft(changeId, revId, commentInfo1.id);
    assertThat(getHeadOfDraftCommentsRef(draftRefName).getParentCount()).isEqualTo(0);
    assertThat(
            getDraftComments(changeId, revId).values().stream()
                .flatMap(List::stream)
                .map(commentInfo -> commentInfo.message))
        .containsExactly("comment 2");

    deleteDraft(changeId, revId, commentInfo2.id);
    assertThat(projectOperations.project(allUsers).hasHead(draftRefName)).isFalse();
    assertThat(getDraftComments(changeId, revId).values().stream().flatMap(List::stream)).isEmpty();
  }

  @Test
  public void putDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
      String uuid = actual.id;
      comment.message = "updated comment 1";
      updateDraft(changeId, revId, comment, uuid);
      result = getDraftComments(changeId, revId);
      actual = Iterables.getOnlyElement(result.get(comment.path));
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));

      // Posting a draft comment doesn't cause lastUpdatedOn to change.
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void putDraft_idMismatch() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraft(file, Side.REVISION, 0, "foo");
    CommentInfo commentInfo = addDraft(changeId, revId, comment);
    DraftInput draftInput = newDraft(file, Side.REVISION, 0, "bar");
    draftInput.id = "anything_but_" + commentInfo.id;
    BadRequestException e =
        assertThrows(
            BadRequestException.class,
            () -> updateDraft(changeId, revId, draftInput, commentInfo.id));
    assertThat(e).hasMessageThat().contains("id must match URL");
  }

  @Test
  public void putDraft_negativeLine() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraft(file, Side.REVISION, -666, "foo");
    BadRequestException e =
        assertThrows(BadRequestException.class, () -> addDraft(changeId, revId, comment));
    assertThat(e).hasMessageThat().contains("line must be >= 0");
  }

  @Test
  public void putDraft_invalidRange() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput draftInput = newDraft(file, Side.REVISION, createLineRange(2, 3), "bar");
    draftInput.line = 666;
    BadRequestException e =
        assertThrows(BadRequestException.class, () -> addDraft(changeId, revId, draftInput));
    assertThat(e)
        .hasMessageThat()
        .contains("range endLine must be on the same line as the comment");
  }

  @Test
  public void putDraft_updatePath() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput comment = newDraft("file_foo", Side.REVISION, 0, "foo");
    CommentInfo commentInfo = addDraft(changeId, revId, comment);
    assertThat(getDraftComments(changeId, revId).keySet()).containsExactly("file_foo");
    DraftInput draftInput = newDraft("file_bar", Side.REVISION, 0, "bar");
    updateDraft(changeId, revId, draftInput, commentInfo.id);
    assertThat(getDraftComments(changeId, revId).keySet()).containsExactly("file_bar");
  }

  @Test
  public void putDraft_updateInReplyToAndTag() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    DraftInput draftInput1 = newDraft(FILE_NAME, Side.REVISION, 0, "foo");
    CommentInfo commentInfo = addDraft(changeId, revId, draftInput1);
    DraftInput draftInput2 = newDraft(FILE_NAME, Side.REVISION, 0, "bar");
    String inReplyTo = "in_reply_to";
    String tag = "täg";
    draftInput2.inReplyTo = inReplyTo;
    draftInput2.tag = tag;
    updateDraft(changeId, revId, draftInput2, commentInfo.id);
    com.google.gerrit.entities.Comment comment =
        Iterables.getOnlyElement(commentsUtil.draftByChange(r.getChange().notes()));
    assertThat(comment.parentUuid).isEqualTo(inReplyTo);
    assertThat(comment.tag).isEqualTo(tag);
  }

  @Test
  public void listDrafts() throws Exception {
    String file = "file";
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String revId = r.getCommit().getName();
    assertThat(getDraftComments(changeId, revId)).isEmpty();

    List<DraftInput> expectedDrafts = new ArrayList<>();
    for (Integer line : lines) {
      DraftInput comment = newDraft(file, Side.REVISION, line, "comment " + line);
      expectedDrafts.add(comment);
      addDraft(changeId, revId, comment);
    }

    Map<String, List<CommentInfo>> result = getDraftComments(changeId, revId);
    assertThat(result).isNotEmpty();
    List<CommentInfo> actualComments = result.get(file);
    assertThat(actualComments.stream().map(infoToDraft(file)))
        .containsExactlyElementsIn(expectedDrafts);
  }

  @Test
  public void getDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      String path = "file1";
      DraftInput comment = newDraft(path, Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, comment);
      CommentInfo actual = getDraftComment(changeId, revId, returned.id);
      assertThat(comment).isEqualTo(infoToDraft(path).apply(actual));
    }
  }

  @Test
  public void deleteDraft() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput draft = newDraft("file1", Side.REVISION, line, "comment 1");
      CommentInfo returned = addDraft(changeId, revId, draft);
      deleteDraft(changeId, revId, returned.id);
      Map<String, List<CommentInfo>> drafts = getDraftComments(changeId, revId);
      assertThat(drafts).isEmpty();

      // Deleting a draft comment doesn't cause lastUpdatedOn to change.
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void insertCommentsWithHistoricTimestamp() throws Exception {
    Timestamp timestamp = new Timestamp(0);
    for (Integer line : lines) {
      String file = "file";
      String contents = "contents " + line;
      PushOneCommit push =
          pushFactory.create(admin.newIdent(), testRepo, "first subject", file, contents);
      PushOneCommit.Result r = push.to("refs/for/master");
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      Timestamp origLastUpdated = r.getChange().change().getLastUpdatedOn();

      ReviewInput input = new ReviewInput();
      CommentInput comment = newComment(file, Side.REVISION, line, "comment 1", false);
      comment.updated = timestamp;
      input.comments = new HashMap<>();
      input.comments.put(comment.path, Lists.newArrayList(comment));
      ChangeResource changeRsrc =
          changes.get().parse(TopLevelResource.INSTANCE, IdString.fromDecoded(changeId));
      RevisionResource revRsrc = revisions.parse(changeRsrc, IdString.fromDecoded(revId));
      postReview.get().apply(revRsrc, input, timestamp);
      Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
      assertThat(result).isNotEmpty();
      CommentInfo actual = Iterables.getOnlyElement(result.get(comment.path));
      CommentInput ci = infoToInput(file).apply(actual);
      ci.updated = comment.updated;
      assertThat(comment).isEqualTo(ci);
      assertThat(actual.updated).isEqualTo(gApi.changes().id(r.getChangeId()).info().created);

      // Updating historic comments doesn't cause lastUpdatedOn to regress.
      assertThat(r.getChange().change().getLastUpdatedOn()).isEqualTo(origLastUpdated);
    }
  }

  @Test
  public void addDuplicateComments() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    String revId = r1.getCommit().getName();
    addComment(r1, "nit: trailing whitespace");
    addComment(r1, "nit: trailing whitespace");
    Map<String, List<CommentInfo>> result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(2);
    addComment(r1, "nit: trailing whitespace", true, false, null);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(2);

    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "content")
            .to("refs/for/master");
    changeId = r2.getChangeId();
    revId = r2.getCommit().getName();
    addComment(r2, "nit: trailing whitespace", true, false, null);
    result = getPublishedComments(changeId, revId);
    assertThat(result.get(FILE_NAME)).hasSize(1);
  }

  @Test
  public void listChangeDrafts() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "new content", r1.getChangeId())
            .to("refs/for/master");

    requestScopeOperations.setApiUser(admin.id());
    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "nit: trailing whitespace"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "typo: content"));

    requestScopeOperations.setApiUser(user.id());
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "+1, please fix"));

    requestScopeOperations.setApiUser(admin.id());
    Map<String, List<CommentInfo>> actual = gApi.changes().id(r1.getChangeId()).drafts();
    assertThat(actual.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> comments = actual.get(FILE_NAME);
    assertThat(comments).hasSize(2);

    CommentInfo c1 = comments.get(0);
    assertThat(c1.author).isNull();
    assertThat(c1.patchSet).isEqualTo(1);
    assertThat(c1.message).isEqualTo("nit: trailing whitespace");
    assertThat(c1.side).isNull();
    assertThat(c1.line).isEqualTo(1);

    CommentInfo c2 = comments.get(1);
    assertThat(c2.author).isNull();
    assertThat(c2.patchSet).isEqualTo(2);
    assertThat(c2.message).isEqualTo("typo: content");
    assertThat(c2.side).isNull();
    assertThat(c2.line).isEqualTo(1);
  }

  @Test
  public void listChangeDraftsAnonymousThrowsAuthException() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    requestScopeOperations.setApiUserAnonymous();
    assertThrows(AuthException.class, () -> gApi.changes().id(changeId).draftsAsList());
  }

  @Test
  public void listChangeComments() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "new cntent", r1.getChangeId())
            .to("refs/for/master");

    addComment(r1, "nit: trailing whitespace");
    addComment(r2, "typo: content");

    Map<String, List<CommentInfo>> actual = gApi.changes().id(r2.getChangeId()).comments(false);
    assertThat(actual.keySet()).containsExactly(FILE_NAME);

    List<CommentInfo> comments = actual.get(FILE_NAME);
    assertThat(comments).hasSize(2);

    // Comment context is disabled by default
    assertThat(comments.stream().filter(c -> c.contextLines != null)).isEmpty();

    CommentInfo c1 = comments.get(0);
    assertThat(c1.author._accountId).isEqualTo(user.id().get());
    assertThat(c1.patchSet).isEqualTo(1);
    assertThat(c1.message).isEqualTo("nit: trailing whitespace");
    assertThat(c1.side).isNull();
    assertThat(c1.line).isEqualTo(1);

    CommentInfo c2 = comments.get(1);
    assertThat(c2.author._accountId).isEqualTo(user.id().get());
    assertThat(c2.patchSet).isEqualTo(2);
    assertThat(c2.message).isEqualTo("typo: content");
    assertThat(c2.side).isNull();
    assertThat(c2.line).isEqualTo(1);
  }

  @Test
  public void listChangeCommentsWithContextEnabled() throws Exception {
    PushOneCommit.Result r1 = createChange();

    ImmutableList.Builder<String> content = ImmutableList.builder();
    for (int i = 1; i <= 10; i++) {
      content.add("line_" + Integer.toString(i));
    }

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                SUBJECT,
                FILE_NAME,
                content.build().stream().collect(Collectors.joining("\n")),
                r1.getChangeId())
            .to("refs/for/master");

    addCommentOnLine(r2, "nit: please fix", 1);
    addCommentOnRange(r2, "looks good", commentRangeInLines(2, 5));

    List<CommentInfo> comments = gApi.changes().id(r2.getChangeId()).commentsAsList(true);

    assertThat(comments).hasSize(2);

    assertThat(comments.get(0).message).isEqualTo("nit: please fix");
    assertThat(comments.get(0).contextLines).containsExactlyElementsIn(contextLines("1", "line_1"));

    assertThat(comments.get(1).message).isEqualTo("looks good");
    assertThat(comments.get(1).contextLines)
        .containsExactlyElementsIn(
            contextLines("2", "line_2", "3", "line_3", "4", "line_4", "5", "line_5"));
  }

  private List<ContextLineInfo> contextLines(String... args) {
    List<ContextLineInfo> result = new ArrayList<>();
    for (int i = 0; i < args.length; i += 2) {
      int lineNbr = Integer.parseInt(args[i]);
      String contextLine = args[i + 1];
      ContextLineInfo info = new ContextLineInfo(lineNbr, contextLine);
      result.add(info);
    }
    return result;
  }

  @Test
  public void listChangeCommentsAnonymousDoesNotRequireAuth() throws Exception {
    PushOneCommit.Result r1 = createChange();

    PushOneCommit.Result r2 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "new cntent", r1.getChangeId())
            .to("refs/for/master");

    addComment(r1, "nit: trailing whitespace");
    addComment(r2, "typo: content");

    List<CommentInfo> comments = gApi.changes().id(r1.getChangeId()).commentsAsList(false);
    assertThat(comments.stream().map(c -> c.message).collect(toList()))
        .containsExactly("nit: trailing whitespace", "typo: content");

    requestScopeOperations.setApiUserAnonymous();
    comments = gApi.changes().id(r1.getChangeId()).commentsAsList(false);
    assertThat(comments.stream().map(c -> c.message).collect(toList()))
        .containsExactly("nit: trailing whitespace", "typo: content");
  }

  @Test
  public void listChangeWithDrafts() throws Exception {
    for (Integer line : lines) {
      PushOneCommit.Result r = createChange();
      String changeId = r.getChangeId();
      String revId = r.getCommit().getName();
      DraftInput comment = newDraft("file1", Side.REVISION, line, "comment 1");
      addDraft(changeId, revId, comment);
      assertThat(gApi.changes().query("change:" + changeId + " has:draft").get()).hasSize(1);
    }
  }

  @Test
  public void publishCommentsAllRevisions() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();

    pushFactory
        .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "initial content\n", changeId)
        .to("refs/heads/master");

    PushOneCommit.Result r1 =
        pushFactory
            .create(admin.newIdent(), testRepo, SUBJECT, FILE_NAME, "old boring content\n")
            .to("refs/for/master");

    PushOneCommit.Result r2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                SUBJECT,
                FILE_NAME,
                "new interesting\ncntent\n",
                r1.getChangeId())
            .to("refs/for/master");

    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, createLineRange(4, 10), "Is it that bad?"));
    addDraft(
        r1.getChangeId(),
        r1.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, createLineRange(0, 7), "what happened to this?"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, createLineRange(4, 15), "better now"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 2, "typo: content"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 1, "comment 1 on base"));
    addDraft(
        r2.getChangeId(),
        r2.getCommit().getName(),
        newDraft(FILE_NAME, Side.PARENT, 2, "comment 2 on base"));

    PushOneCommit.Result other = createChange();
    // Drafts on other changes aren't returned.
    addDraft(
        other.getChangeId(),
        other.getCommit().getName(),
        newDraft(FILE_NAME, Side.REVISION, 1, "unrelated comment"));

    requestScopeOperations.setApiUser(admin.id());
    // Drafts by other users aren't returned.
    addDraft(
        r2.getChangeId(), r2.getCommit().getName(), newDraft(FILE_NAME, Side.REVISION, 2, "oops"));
    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    reviewInput.message = "comments";
    gApi.changes().id(r2.getChangeId()).current().review(reviewInput);

    assertThat(gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps1Map =
        gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).comments();
    assertThat(ps1Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps1List = ps1Map.get(FILE_NAME);
    assertThat(ps1List).hasSize(2);
    assertThat(ps1List.get(0).message).isEqualTo("what happened to this?");
    assertThat(ps1List.get(0).side).isEqualTo(Side.PARENT);
    assertThat(ps1List.get(1).message).isEqualTo("Is it that bad?");
    assertThat(ps1List.get(1).side).isNull();

    assertThat(gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).drafts())
        .isEmpty();
    Map<String, List<CommentInfo>> ps2Map =
        gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).comments();
    assertThat(ps2Map.keySet()).containsExactly(FILE_NAME);
    List<CommentInfo> ps2List = ps2Map.get(FILE_NAME);
    assertThat(ps2List).hasSize(4);
    assertThat(ps2List.get(0).message).isEqualTo("comment 1 on base");
    assertThat(ps2List.get(1).message).isEqualTo("comment 2 on base");
    assertThat(ps2List.get(2).message).isEqualTo("better now");
    assertThat(ps2List.get(3).message).isEqualTo("typo: content");

    List<Message> messages = email.getMessages(r2.getChangeId(), "comment");
    assertThat(messages).hasSize(1);
    String url = canonicalWebUrl.get();
    int c = r1.getChange().getId().get();
    assertThat(extractComments(messages.get(0).body()))
        .isEqualTo(
            "Patch Set 2:\n"
                + "\n"
                + "(6 comments)\n"
                + "\n"
                + "comments\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/1/a.txt \n"
                + "File a.txt:\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/1/a.txt@a1 \n"
                + "PS1, Line 1: initial\n"
                + "what happened to this?\n"
                + "\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/1/a.txt@1 \n"
                + "PS1, Line 1: boring\n"
                + "Is it that bad?\n"
                + "\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/2/a.txt \n"
                + "File a.txt:\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/2/a.txt@a1 \n"
                + "PS2, Line 1: initial content\n"
                + "comment 1 on base\n"
                + "\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/2/a.txt@a2 \n"
                + "PS2, Line 2: \n"
                + "comment 2 on base\n"
                + "\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/2/a.txt@1 \n"
                + "PS2, Line 1: interesting\n"
                + "better now\n"
                + "\n"
                + "\n"
                + url
                + "c/"
                + project.get()
                + "/+/"
                + c
                + "/2/a.txt@2 \n"
                + "PS2, Line 2: cntent\n"
                + "typo: content\n"
                + "\n"
                + "\n");
  }

  @Test
  public void commentTags() throws Exception {
    PushOneCommit.Result r = createChange();

    CommentInput pub = new CommentInput();
    pub.line = 1;
    pub.message = "published comment";
    pub.path = FILE_NAME;
    ReviewInput rin = newInput(pub);
    rin.tag = "tag1";
    gApi.changes().id(r.getChangeId()).current().review(rin);

    List<CommentInfo> comments = gApi.changes().id(r.getChangeId()).current().commentsAsList();
    assertThat(comments).hasSize(1);
    assertThat(comments.get(0).tag).isEqualTo("tag1");

    DraftInput draft = new DraftInput();
    draft.line = 2;
    draft.message = "draft comment";
    draft.path = FILE_NAME;
    draft.tag = "tag2";
    addDraft(r.getChangeId(), r.getCommit().name(), draft);

    List<CommentInfo> drafts = gApi.changes().id(r.getChangeId()).current().draftsAsList();
    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).tag).isEqualTo("tag2");
  }

  @Test
  public void queryChangesWithCommentCount() throws Exception {
    // PS1 has three comments in three different threads, PS2 has one comment in one thread.
    PushOneCommit.Result result = createChange("change 1", FILE_NAME, "content 1");
    String changeId1 = result.getChangeId();
    addComment(result, "comment 1", false, true, null);
    addComment(result, "comment 2", false, null, null);
    addComment(result, "comment 3", false, false, null);
    PushOneCommit.Result result2 = amendChange(changeId1);
    addComment(result2, "comment4", false, true, null);

    // Change2 has two comments in one thread, the first is unresolved and the second is resolved.
    result = createChange("change 2", FILE_NAME, "content 2");
    String changeId2 = result.getChangeId();
    addComment(result, "comment 1", false, true, null);
    Map<String, List<CommentInfo>> comments =
        getPublishedComments(changeId2, result.getCommit().name());
    assertThat(comments).hasSize(1);
    assertThat(comments.get(FILE_NAME)).hasSize(1);
    addComment(result, "comment 2", false, false, comments.get(FILE_NAME).get(0).id);

    // Change3 has two comments in one thread, the first is resolved, the second is unresolved.
    result = createChange("change 3", FILE_NAME, "content 3");
    String changeId3 = result.getChangeId();
    addComment(result, "comment 1", false, false, null);
    comments = getPublishedComments(result.getChangeId(), result.getCommit().name());
    assertThat(comments).hasSize(1);
    assertThat(comments.get(FILE_NAME)).hasSize(1);
    addComment(result, "comment 2", false, true, comments.get(FILE_NAME).get(0).id);

    try (AutoCloseable ignored = disableNoteDb()) {
      ChangeInfo changeInfo1 = Iterables.getOnlyElement(query(changeId1));
      ChangeInfo changeInfo2 = Iterables.getOnlyElement(query(changeId2));
      ChangeInfo changeInfo3 = Iterables.getOnlyElement(query(changeId3));
      assertThat(changeInfo1.unresolvedCommentCount).isEqualTo(2);
      assertThat(changeInfo1.totalCommentCount).isEqualTo(4);
      assertThat(changeInfo2.unresolvedCommentCount).isEqualTo(0);
      assertThat(changeInfo2.totalCommentCount).isEqualTo(2);
      assertThat(changeInfo3.unresolvedCommentCount).isEqualTo(1);
      assertThat(changeInfo3.totalCommentCount).isEqualTo(2);
    }
  }

  @Test
  public void deleteCommentCannotBeAppliedByUser() throws Exception {
    PushOneCommit.Result result = createChange();
    CommentInput targetComment = addComment(result.getChangeId());

    Map<String, List<CommentInfo>> commentsMap =
        getPublishedComments(result.getChangeId(), result.getCommit().name());

    assertThat(commentsMap).hasSize(1);
    assertThat(commentsMap.get(FILE_NAME)).hasSize(1);

    String uuid = commentsMap.get(targetComment.path).get(0).id;
    DeleteCommentInput input = new DeleteCommentInput("contains confidential information");

    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        AuthException.class,
        () -> gApi.changes().id(result.getChangeId()).current().comment(uuid).delete(input));
  }

  @Test
  public void deleteCommentByRewritingCommitHistory() throws Exception {
    // Creates the following commit history on the meta branch of the test change. Then tries to
    // delete the comments one by one, which will rewrite most of the commits on the 'meta' branch.
    // Commits will be rewritten N times for N added comments. After each deletion, the meta branch
    // should keep its previous state except that the target comment's message should be updated.

    // 1st commit: Create PS1.
    PushOneCommit.Result result1 = createChange(SUBJECT, "a.txt", "a");
    Change.Id id = result1.getChange().getId();
    String changeId = result1.getChangeId();
    String ps1 = result1.getCommit().name();

    // 2nd commit: Add (c1) to PS1.
    CommentInput c1 = newComment("a.txt", "comment 1");
    addComments(changeId, ps1, c1);

    // 3rd commit: Add (c2, c3) to PS1.
    CommentInput c2 = newComment("a.txt", "comment 2");
    CommentInput c3 = newComment("a.txt", "comment 3");
    addComments(changeId, ps1, c2, c3);

    // 4th commit: Add (c4) to PS1.
    CommentInput c4 = newComment("a.txt", "comment 4");
    addComments(changeId, ps1, c4);

    // 5th commit: Create PS2.
    PushOneCommit.Result result2 = amendChange(changeId, "refs/for/master", "b.txt", "b");
    String ps2 = result2.getCommit().name();

    // 6th commit: Add (c5) to PS1.
    CommentInput c5 = newComment("a.txt", "comment 5");
    addComments(changeId, ps1, c5);

    // 7th commit: Add (c6) to PS2.
    CommentInput c6 = newComment("b.txt", "comment 6");
    addComments(changeId, ps2, c6);

    // 8th commit: Create PS3.
    PushOneCommit.Result result3 = amendChange(changeId);
    String ps3 = result3.getCommit().name();

    // 9th commit: Create PS4.
    PushOneCommit.Result result4 = amendChange(changeId, "refs/for/master", "c.txt", "c");
    String ps4 = result4.getCommit().name();

    // 10th commit: Add (c7, c8) to PS4.
    CommentInput c7 = newComment("c.txt", "comment 7");
    CommentInput c8 = newComment("b.txt", "comment 8");
    addComments(changeId, ps4, c7, c8);

    // 11th commit: Add (c9) to PS2.
    CommentInput c9 = newCommentWithOnlyMandatoryFields("b.txt", "comment 9");
    addComments(changeId, ps2, c9);

    List<CommentInfo> commentsBeforeDelete = getChangeSortedComments(id.get());
    assertThat(commentsBeforeDelete).hasSize(9);
    // PS1 has comments [c1, c2, c3, c4, c5].
    assertThat(getRevisionComments(changeId, ps1)).hasSize(5);
    // PS2 has comments [c6, c9].
    assertThat(getRevisionComments(changeId, ps2)).hasSize(2);
    // PS3 has no comment.
    assertThat(getRevisionComments(changeId, ps3)).isEmpty();
    // PS4 has comments [c7, c8].
    assertThat(getRevisionComments(changeId, ps4)).hasSize(2);

    requestScopeOperations.setApiUser(admin.id());
    for (int i = 0; i < commentsBeforeDelete.size(); i++) {
      List<RevCommit> commitsBeforeDelete = getChangeMetaCommitsInReverseOrder(id);

      CommentInfo comment = commentsBeforeDelete.get(i);
      String uuid = comment.id;
      int patchSet = comment.patchSet;
      // 'oldComment' has some fields unset compared with 'comment'.
      CommentInfo oldComment = gApi.changes().id(changeId).revision(patchSet).comment(uuid).get();

      DeleteCommentInput input = new DeleteCommentInput("delete comment " + uuid);
      CommentInfo updatedComment =
          gApi.changes().id(changeId).revision(patchSet).comment(uuid).delete(input);

      String expectedMsg =
          String.format("Comment removed by: %s; Reason: %s", admin.fullName(), input.reason);
      assertThat(updatedComment.message).isEqualTo(expectedMsg);
      oldComment.message = expectedMsg;
      assertThat(updatedComment).isEqualTo(oldComment);

      // Check the NoteDb state after the deletion.
      assertMetaBranchCommitsAfterRewriting(commitsBeforeDelete, id, uuid, expectedMsg);

      comment.message = expectedMsg;
      commentsBeforeDelete.set(i, comment);
      List<CommentInfo> commentsAfterDelete = getChangeSortedComments(id.get());
      assertThat(commentsAfterDelete).isEqualTo(commentsBeforeDelete);
    }

    // Make sure that comments can still be added correctly.
    CommentInput c10 = newComment("a.txt", "comment 10");
    CommentInput c11 = newComment("b.txt", "comment 11");
    CommentInput c12 = newComment("a.txt", "comment 12");
    CommentInput c13 = newComment("c.txt", "comment 13");
    addComments(changeId, ps1, c10);
    addComments(changeId, ps2, c11);
    addComments(changeId, ps3, c12);
    addComments(changeId, ps4, c13);

    assertThat(getChangeSortedComments(id.get())).hasSize(13);
    assertThat(getRevisionComments(changeId, ps1)).hasSize(6);
    assertThat(getRevisionComments(changeId, ps2)).hasSize(3);
    assertThat(getRevisionComments(changeId, ps3)).hasSize(1);
    assertThat(getRevisionComments(changeId, ps4)).hasSize(3);
  }

  @Test
  public void deleteOneCommentMultipleTimes() throws Exception {
    PushOneCommit.Result result = createChange();
    Change.Id id = result.getChange().getId();
    String changeId = result.getChangeId();
    String ps1 = result.getCommit().name();

    CommentInput c1 = newComment(FILE_NAME, "comment 1");
    CommentInput c2 = newComment(FILE_NAME, "comment 2");
    CommentInput c3 = newComment(FILE_NAME, "comment 3");
    addComments(changeId, ps1, c1);
    addComments(changeId, ps1, c2);
    addComments(changeId, ps1, c3);

    List<CommentInfo> commentsBeforeDelete = getChangeSortedComments(id.get());
    assertThat(commentsBeforeDelete).hasSize(3);
    Optional<CommentInfo> targetComment =
        commentsBeforeDelete.stream().filter(c -> c.message.equals("comment 2")).findFirst();
    assertThat(targetComment).isPresent();
    String uuid = targetComment.get().id;
    CommentInfo oldComment = gApi.changes().id(changeId).revision(ps1).comment(uuid).get();

    List<RevCommit> commitsBeforeDelete = getChangeMetaCommitsInReverseOrder(id);

    requestScopeOperations.setApiUser(admin.id());
    for (int i = 0; i < 3; i++) {
      DeleteCommentInput input = new DeleteCommentInput("delete comment 2, iteration: " + i);
      gApi.changes().id(changeId).revision(ps1).comment(uuid).delete(input);
    }

    CommentInfo updatedComment = gApi.changes().id(changeId).revision(ps1).comment(uuid).get();
    String expectedMsg =
        String.format(
            "Comment removed by: %s; Reason: %s",
            admin.fullName(), "delete comment 2, iteration: 2");
    assertThat(updatedComment.message).isEqualTo(expectedMsg);
    oldComment.message = expectedMsg;
    assertThat(updatedComment).isEqualTo(oldComment);

    assertMetaBranchCommitsAfterRewriting(commitsBeforeDelete, id, uuid, expectedMsg);
    assertThat(getChangeSortedComments(id.get())).hasSize(3);
  }

  private List<CommentInfo> getRevisionComments(String changeId, String revId) throws Exception {
    return getPublishedComments(changeId, revId).values().stream()
        .flatMap(List::stream)
        .collect(toList());
  }

  private CommentInput addComment(String changeId) throws Exception {
    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(FILE_NAME, Side.REVISION, 0, "a message", false);
    input.comments = ImmutableMap.of(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(input);
    return comment;
  }

  private void addComments(String changeId, String revision, CommentInput... commentInputs)
      throws Exception {
    ReviewInput input = new ReviewInput();
    input.comments = Arrays.stream(commentInputs).collect(groupingBy(c -> c.path));
    gApi.changes().id(changeId).revision(revision).review(input);
  }

  /**
   * All the commits, which contain the target comment before, should still contain the comment with
   * the updated message. All the other metas of the commits should be exactly the same.
   */
  private void assertMetaBranchCommitsAfterRewriting(
      List<RevCommit> beforeDelete,
      Change.Id changeId,
      String targetCommentUuid,
      String expectedMessage)
      throws Exception {
    List<RevCommit> afterDelete = getChangeMetaCommitsInReverseOrder(changeId);
    assertThat(afterDelete).hasSize(beforeDelete.size());

    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader()) {
      for (int i = 0; i < beforeDelete.size(); i++) {
        RevCommit commitBefore = beforeDelete.get(i);
        RevCommit commitAfter = afterDelete.get(i);

        Map<String, HumanComment> commentMapBefore =
            DeleteCommentRewriter.getPublishedComments(
                noteUtil, reader, NoteMap.read(reader, commitBefore));
        Map<String, HumanComment> commentMapAfter =
            DeleteCommentRewriter.getPublishedComments(
                noteUtil, reader, NoteMap.read(reader, commitAfter));

        if (commentMapBefore.containsKey(targetCommentUuid)) {
          assertThat(commentMapAfter).containsKey(targetCommentUuid);
          HumanComment comment = commentMapAfter.get(targetCommentUuid);
          assertThat(comment.message).isEqualTo(expectedMessage);
          comment.message = commentMapBefore.get(targetCommentUuid).message;
          commentMapAfter.put(targetCommentUuid, comment);
          assertThat(commentMapAfter).isEqualTo(commentMapBefore);
        } else {
          assertThat(commentMapAfter).doesNotContainKey(targetCommentUuid);
        }

        // Other metas should be exactly the same.
        assertThat(commitAfter.getFullMessage()).isEqualTo(commitBefore.getFullMessage());
        assertThat(commitAfter.getCommitterIdent()).isEqualTo(commitBefore.getCommitterIdent());
        assertThat(commitAfter.getAuthorIdent()).isEqualTo(commitBefore.getAuthorIdent());
        assertThat(commitAfter.getEncoding()).isEqualTo(commitBefore.getEncoding());
        assertThat(commitAfter.getEncodingName()).isEqualTo(commitBefore.getEncodingName());
      }
    }
  }

  private RevCommit getHeadOfDraftCommentsRef(String refName) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return getHead(repo, refName);
    }
  }

  private static String extractComments(String msg) {
    // Extract lines between start "....." and end "-- ".
    Pattern p = Pattern.compile(".*[.]{5}\n+(.*)\\n+-- \n.*", Pattern.DOTALL);
    Matcher m = p.matcher(msg);
    return m.matches() ? m.group(1) : msg;
  }

  private ReviewInput newInput(CommentInput c) {
    ReviewInput in = new ReviewInput();
    in.comments = new HashMap<>();
    in.comments.put(c.path, Lists.newArrayList(c));
    return in;
  }

  private void addComment(PushOneCommit.Result r, String message) throws Exception {
    addComment(r, message, false, false, null, 1, null);
  }

  private void addCommentOnLine(PushOneCommit.Result r, String message, int line) throws Exception {
    addComment(r, message, false, false, null, line, null);
  }

  private void addCommentOnRange(PushOneCommit.Result r, String message, Comment.Range range)
      throws Exception {
    addComment(r, message, false, false, null, null, range);
  }

  private Comment.Range commentRangeInLines(int startLine, int endLine) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.endLine = endLine;
    return range;
  }

  private void addComment(
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo)
      throws Exception {
    addComment(r, message, omitDuplicateComments, unresolved, inReplyTo, 1, null);
  }

  private void addComment(
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo,
      Integer line,
      Comment.Range range)
      throws Exception {
    CommentInput c = new CommentInput();
    c.message = message;
    c.path = FILE_NAME;
    c.unresolved = unresolved;
    c.inReplyTo = inReplyTo;
    c.line = line;
    c.range = range;
    ReviewInput in = newInput(c);
    in.omitDuplicateComments = omitDuplicateComments;
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
  }

  private CommentInfo addDraft(String changeId, String revId, DraftInput in) throws Exception {
    return gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }

  private void updateDraft(String changeId, String revId, DraftInput in, String uuid)
      throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).update(in);
  }

  private void deleteDraft(String changeId, String revId, String uuid) throws Exception {
    gApi.changes().id(changeId).revision(revId).draft(uuid).delete();
  }

  private CommentInfo getPublishedComment(String changeId, String revId, String uuid)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).comment(uuid).get();
  }

  private Map<String, List<CommentInfo>> getPublishedComments(String changeId, String revId)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).comments();
  }

  private List<CommentInfo> getPublishedCommentsAsList(String changeId) throws Exception {
    return gApi.changes().id(changeId).commentsAsList(false);
  }

  private Map<String, List<CommentInfo>> getDraftComments(String changeId, String revId)
      throws Exception {
    return gApi.changes().id(changeId).revision(revId).drafts();
  }

  private List<CommentInfo> getDraftCommentsAsList(String changeId) throws Exception {
    return gApi.changes().id(changeId).draftsAsList();
  }

  private CommentInfo getDraftComment(String changeId, String revId, String uuid) throws Exception {
    return gApi.changes().id(changeId).revision(revId).draft(uuid).get();
  }

  private static CommentInput newComment(String file, String message) {
    return newComment(file, Side.REVISION, 0, message, false);
  }

  private static CommentInput newCommentWithOnlyMandatoryFields(String path, String message) {
    CommentInput c = new CommentInput();
    return populate(c, path, null, null, null, null, message, false);
  }

  private static CommentInput newComment(
      String path, Side side, int line, String message, Boolean unresolved) {
    CommentInput c = new CommentInput();
    return populate(c, path, side, null, line, message, unresolved);
  }

  private static CommentInput newCommentOnParent(
      String path, int parent, int line, String message) {
    CommentInput c = new CommentInput();
    return populate(c, path, Side.PARENT, parent, line, message, false);
  }

  private DraftInput newDraft(String path, Side side, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, null, line, message, false);
  }

  private DraftInput newDraft(String path, Side side, Comment.Range range, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, side, null, range.startLine, range, message, false);
  }

  private DraftInput newDraftOnParent(String path, int parent, int line, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, Side.PARENT, parent, line, message, false);
  }

  private DraftInput newDraftWithOnlyMandatoryFields(String path, String message) {
    DraftInput d = new DraftInput();
    return populate(d, path, null, null, null, null, message, false);
  }

  private static <C extends Comment> C populate(
      C c,
      String path,
      Side side,
      Integer parent,
      Integer line,
      Comment.Range range,
      String message,
      Boolean unresolved) {
    c.path = path;
    c.side = side;
    c.parent = parent;
    c.line = line != null && line != 0 ? line : null;
    c.message = message;
    c.unresolved = unresolved;
    if (range != null) {
      c.range = range;
    }
    return c;
  }

  private static <C extends Comment> C populate(
      C c, String path, Side side, Integer parent, int line, String message, Boolean unresolved) {
    return populate(c, path, side, parent, line, null, message, unresolved);
  }

  private static Comment.Range createLineRange(int startChar, int endChar) {
    Comment.Range range = new Comment.Range();
    range.startLine = 1;
    range.startCharacter = startChar;
    range.endLine = 1;
    range.endCharacter = endChar;
    return range;
  }

  private static Function<CommentInfo, CommentInput> infoToInput(String path) {
    return infoToInput(path, CommentInput::new);
  }

  private static Function<CommentInfo, DraftInput> infoToDraft(String path) {
    return infoToInput(path, DraftInput::new);
  }

  private static <I extends Comment> Function<CommentInfo, I> infoToInput(
      String path, Supplier<I> supplier) {
    return info -> {
      I i = supplier.get();
      i.path = path;
      copy(info, i);
      return i;
    };
  }

  private static void copy(Comment from, Comment to) {
    to.side = from.side == null ? Side.REVISION : from.side;
    to.parent = from.parent;
    to.line = from.line;
    to.message = from.message;
    to.range = from.range;
    to.unresolved = from.unresolved;
    to.inReplyTo = from.inReplyTo;
  }
}
