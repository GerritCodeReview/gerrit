// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.git.receive;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * Tests for comment validation when publishing drafts via the {@code --publish-comments} option.
 */
public class ReceiveCommitsCommentValidationIT extends AbstractDaemonTest {
  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentHelper testCommentHelper;

  private static final int COMMENT_SIZE_LIMIT = 666;

  private static final String COMMENT_TEXT = "The comment text";
  private static final CommentForValidation COMMENT_FOR_VALIDATION =
      CommentForValidation.create(
          CommentForValidation.CommentSource.HUMAN,
          CommentForValidation.CommentType.FILE_COMMENT,
          COMMENT_TEXT,
          COMMENT_TEXT.length());

  @Captor private ArgumentCaptor<ImmutableList<CommentForValidation>> capture;
  @Captor private ArgumentCaptor<CommentValidationContext> captureCtx;

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        CommentValidator mockCommentValidator = mock(CommentValidator.class);
        bind(CommentValidator.class)
            .annotatedWith(Exports.named(mockCommentValidator.getClass()))
            .toInstance(mockCommentValidator);
        bind(CommentValidator.class).toInstance(mockCommentValidator);
      }
    };
  }

  @Before
  public void resetMock() {
    initMocks(this);
    clearInvocations(mockCommentValidator);
  }

  @Test
  public void validateComments_commentOK() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    when(mockCommentValidator.validateComments(
            CommentValidationContext.create(
                result.getChange().getId().get(), result.getChange().project().get()),
            ImmutableList.of(COMMENT_FOR_VALIDATION)))
        .thenReturn(ImmutableList.of());
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    amendResult.assertOkStatus();
    amendResult.assertNotMessage("Comment validation failure:");
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);
  }

  @Test
  public void attentionSetUpdatedReviewerAdded() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).addReviewer(user.email());
    gApi.changes().id(changeId).attention(user.email()).remove(new AttentionSetInput("removed"));
    String revId = result.getCommit().getName();
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    AttentionSetUpdate attentionSetUpdate =
        Iterables.getOnlyElement(amendResult.getChange().attentionSet());
    assertThat(attentionSetUpdate.account()).isEqualTo(user.id());
    assertThat(attentionSetUpdate.reason()).isEqualTo("owner or uploader replied");
    assertThat(attentionSetUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
  }

  @Test
  public void attentionSetUpdatedReviewerNotAddedWhenRemoved() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).addReviewer(user.email());
    gApi.changes().id(changeId).attention(user.email()).remove(new AttentionSetInput("removed"));
    String revId = result.getCommit().getName();
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    Result amendResult =
        amendChange(
            changeId, "refs/for/master%publish-comments,cc=" + user.email(), admin, testRepo);
    AttentionSetUpdate attentionSetUpdate =
        Iterables.getOnlyElement(amendResult.getChange().attentionSet());
    assertThat(attentionSetUpdate.account()).isEqualTo(user.id());
    assertThat(attentionSetUpdate.reason()).isEqualTo("removed");
    assertThat(attentionSetUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
  }

  @Test
  public void attentionSetNotUpdatedWhenNoCommentsPublished() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).addReviewer(user.email());
    gApi.changes().id(changeId).attention(user.email()).remove(new AttentionSetInput("removed"));
    ImmutableSet<AttentionSetUpdate> attentionSet = result.getChange().attentionSet();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(attentionSet).isEqualTo(amendResult.getChange().attentionSet());
  }

  @Test
  public void validateComments_commentRejected() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    when(mockCommentValidator.validateComments(
            CommentValidationContext.create(
                result.getChange().getId().get(), result.getChange().project().get()),
            ImmutableList.of(COMMENT_FOR_VALIDATION)))
        .thenReturn(ImmutableList.of(COMMENT_FOR_VALIDATION.failValidation("Oh no!")));
    DraftInput comment = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, comment);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    amendResult.assertOkStatus();
    amendResult.assertMessage("Comment validation failure:");
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
  }

  @Test
  public void validateComments_inlineVsFileComments_allOK() throws Exception {
    when(mockCommentValidator.validateComments(captureCtx.capture(), capture.capture()))
        .thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    DraftInput draftFile = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftFile);
    DraftInput draftInline =
        testCommentHelper.newDraft(
            result.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(2);

    assertThat(capture.getAllValues()).hasSize(1);

    assertThat(captureCtx.getValue().getProject()).isEqualTo(result.getChange().project().get());
    assertThat(captureCtx.getValue().getChangeId()).isEqualTo(result.getChange().getId().get());

    assertThat(capture.getAllValues().get(0))
        .containsExactly(
            CommentForValidation.create(
                CommentForValidation.CommentSource.HUMAN,
                CommentForValidation.CommentType.INLINE_COMMENT,
                draftInline.message,
                draftInline.message.length()),
            CommentForValidation.create(
                CommentForValidation.CommentSource.HUMAN,
                CommentForValidation.CommentType.FILE_COMMENT,
                draftFile.message,
                draftFile.message.length()));
  }

  @Test
  @GerritConfig(name = "change.commentSizeLimit", value = "" + COMMENT_SIZE_LIMIT)
  public void validateComments_enforceLimits_commentTooLarge() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    int commentLength = COMMENT_SIZE_LIMIT + 1;
    DraftInput comment =
        testCommentHelper.newDraft(new String(new char[commentLength]).replace("\0", "x"));
    testCommentHelper.addDraft(changeId, result.getCommit().getName(), comment);

    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    amendResult.assertOkStatus();
    amendResult.assertMessage(
        String.format("Comment size exceeds limit (%d > %d)", commentLength, COMMENT_SIZE_LIMIT));
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).isEmpty();
  }

  @Test
  @GerritConfig(name = "change.maxComments", value = "8")
  public void countComments_limitNumberOfComments() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());
    // Start out with 1 change message.
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    String filePath = result.getChange().currentFilePaths().get(0);
    DraftInput draftInline = testCommentHelper.newDraft(filePath, Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    // Publishes the 1 draft and adds 2 change messages.
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);

    for (int i = 0; i < 2; ++i) {
      // Adds 1 robot comment and 1 change message.
      testCommentHelper.addRobotComment(
          changeId,
          TestCommentHelper.createRobotCommentInput(result.getChange().currentFilePaths().get(0)));
    }
    // We now have 1 comment, 2 robot comments, 5 change messages.

    draftInline = testCommentHelper.newDraft(filePath, Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    // Publishes the 1 draft and adds 2 change messages. The latter 2 are autogenerated and are not
    // subject to validation.
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);
    amendResult.assertMessage("exceeding maximum number of comments");
  }

  @Test
  @GerritConfig(name = "change.cumulativeCommentSizeLimit", value = "500")
  public void limitCumulativeCommentSize() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    String revId = result.getCommit().getName();
    String filePath = result.getChange().currentFilePaths().get(0);
    String commentText400Bytes = new String(new char[400]).replace("\0", "x");
    DraftInput draftInline =
        testCommentHelper.newDraft(filePath, Side.REVISION, 1, commentText400Bytes);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);

    draftInline = testCommentHelper.newDraft(filePath, Side.REVISION, 1, commentText400Bytes);
    testCommentHelper.addDraft(changeId, revId, draftInline);
    Result amendResult = amendChange(changeId, "refs/for/master%publish-comments", admin, testRepo);
    assertThat(testCommentHelper.getPublishedComments(result.getChangeId())).hasSize(1);
    amendResult.assertMessage("exceeding maximum cumulative size of comments");
  }
}
