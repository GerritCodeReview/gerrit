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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.sql.Timestamp;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/** Tests for comment validation in {@link PostReview}. */
public class PostReviewIT extends AbstractDaemonTest {
  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentHelper testCommentHelper;

  private static final String COMMENT_TEXT = "The comment text";

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
  public void validateCommentsInInput_commentOK() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(
            ImmutableList.of(
                CommentForValidation.create(
                    CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
    gApi.changes().id(r.getChangeId()).current().review(input);

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateCommentsInInput_commentRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.FILE_COMMENT, COMMENT_TEXT);
    when(mockCommentValidator.validateComments(
            ImmutableList.of(CommentForValidation.create(CommentType.FILE_COMMENT, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));

    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(badRequestException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) badRequestException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(COMMENT_TEXT);
    assertThat(badRequestException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void validateCommentsInInput_commentCleanedUp() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    // posting a comment which is empty after trim is a no-op, as the empty comment is dropped
    // during comment cleanup
    ReviewInput input = new ReviewInput();
    CommentInput comment =
        TestCommentHelper.populate(
            new CommentInput(), r.getChange().currentFilePaths().get(0), " ");
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));
    gApi.changes().id(r.getChangeId()).current().review(input);

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void validateDrafts_draftOK() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(
            ImmutableList.of(
                CommentForValidation.create(
                    CommentForValidation.CommentType.INLINE_COMMENT, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of());

    DraftInput draft =
        testCommentHelper.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().getName()).createDraft(draft).get();
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;

    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateDrafts_draftRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.INLINE_COMMENT, COMMENT_TEXT);
    when(mockCommentValidator.validateComments(
            ImmutableList.of(
                CommentForValidation.create(
                    CommentForValidation.CommentType.INLINE_COMMENT, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));

    DraftInput draft =
        testCommentHelper.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(r.getChangeId(), r.getCommit().getName(), draft);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(badRequestException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) badRequestException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(draft.message);
    assertThat(badRequestException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void validateDrafts_inlineVsFileComments_allOK() throws Exception {
    PushOneCommit.Result r = createChange();
    DraftInput draftInline =
        testCommentHelper.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(r.getChangeId(), r.getCommit().getName(), draftInline);
    DraftInput draftFile = testCommentHelper.newDraft(COMMENT_TEXT);
    testCommentHelper.addDraft(r.getChangeId(), r.getCommit().getName(), draftFile);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    when(mockCommentValidator.validateComments(capture.capture(), any()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(2);

    assertThat(capture.getAllValues()).hasSize(1);
    assertThat(capture.getValue())
        .containsExactly(
            CommentForValidation.create(
                CommentForValidation.CommentType.INLINE_COMMENT, draftInline.message),
            CommentForValidation.create(
                CommentForValidation.CommentType.FILE_COMMENT, draftFile.message));
  }

  @Test
  public void validateCommentsInChangeMessage_messageOK() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(
            ImmutableList.of(CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    int numMessages = gApi.changes().id(r.getChangeId()).get().messages.size();
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(gApi.changes().id(r.getChangeId()).get().messages).hasSize(numMessages + 1);
    ChangeMessageInfo message =
        Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(message.message).contains(COMMENT_TEXT);
  }

  @Test
  public void validateCommentsInChangeMessage_messageRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT);
    when(mockCommentValidator.validateComments(
            ImmutableList.of(CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT)),
            CommentValidationContext.builder()
                .changeId(r.getChange().getId().get())
                .project(r.getChange().project().get())
                .build()))
        .thenReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    assertThat(gApi.changes().id(r.getChangeId()).get().messages)
        .hasSize(1); // From the initial commit.
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(badRequestException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) badRequestException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(COMMENT_TEXT);
    assertThat(badRequestException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(gApi.changes().id(r.getChangeId()).get().messages)
        .hasSize(1); // Unchanged from before.
    ChangeMessageInfo message =
        Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(message.message).doesNotContain(COMMENT_TEXT);
  }

  private static CommentInput newComment(String path) {
    return TestCommentHelper.populate(new CommentInput(), path, PostReviewIT.COMMENT_TEXT);
  }
}
