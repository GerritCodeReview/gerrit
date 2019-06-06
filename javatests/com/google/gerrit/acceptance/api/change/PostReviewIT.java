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
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.testing.TestCommentUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.sql.Timestamp;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests for comment validation in {@link PostReview}. */
public class PostReviewIT extends AbstractDaemonTest {
  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentUtil testCommentUtil;

  private static final String COMMENT_TEXT = "The comment text";

  private Capture<ImmutableList<CommentForValidation>> capture = new Capture<>();

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        CommentValidator mockCommentValidator = EasyMock.createMock(CommentValidator.class);
        bind(CommentValidator.class)
            .annotatedWith(Exports.named(mockCommentValidator.getClass()))
            .toInstance(mockCommentValidator);
        bind(CommentValidator.class).toInstance(mockCommentValidator);
      }
    };
  }

  @Before
  public void resetMock() {
    EasyMock.reset(mockCommentValidator);
  }

  @After
  public void verifyMock() {
    EasyMock.verify(mockCommentValidator);
  }

  @Test
  public void validateCommentsInInput_commentOK() throws Exception {
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(
                        CommentForValidation.CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);

    PushOneCommit.Result r = createChange();

    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();
    gApi.changes().id(r.getChangeId()).current().review(input);

    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateCommentsInInput_commentRejected() throws Exception {
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.FILE_COMMENT, COMMENT_TEXT);
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(CommentType.FILE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    EasyMock.replay(mockCommentValidator);

    PushOneCommit.Result r = createChange();

    ReviewInput input = new ReviewInput();
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();
    RestApiException restApiException =
        assertThrows(
            RestApiException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(restApiException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) restApiException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(COMMENT_TEXT);
    assertThat(restApiException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void validateDrafts_draftOK() throws Exception {
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(
                        CommentForValidation.CommentType.INLINE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);

    PushOneCommit.Result r = createChange();

    DraftInput draft =
        testCommentUtil.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().getName()).createDraft(draft).get();
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;

    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateDrafts_draftRejected() throws Exception {
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.INLINE_COMMENT, COMMENT_TEXT);
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(
                        CommentForValidation.CommentType.INLINE_COMMENT, COMMENT_TEXT))))
        .andReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    EasyMock.replay(mockCommentValidator);
    PushOneCommit.Result r = createChange();

    DraftInput draft =
        testCommentUtil.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentUtil.addDraft(r.getChangeId(), r.getCommit().getName(), draft);
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;
    RestApiException restApiException =
        assertThrows(
            RestApiException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(restApiException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) restApiException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(draft.message);
    assertThat(restApiException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void validateDrafts_inlineVsFileComments_allOK() throws Exception {
    PushOneCommit.Result r = createChange();
    DraftInput draftInline =
        testCommentUtil.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentUtil.addDraft(r.getChangeId(), r.getCommit().getName(), draftInline);
    DraftInput draftFile = testCommentUtil.newDraft(COMMENT_TEXT);
    testCommentUtil.addDraft(r.getChangeId(), r.getCommit().getName(), draftFile);
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).isEmpty();

    EasyMock.expect(mockCommentValidator.validateComments(EasyMock.capture(capture)))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);

    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(testCommentUtil.getPublishedComments(r.getChangeId())).hasSize(2);

    assertThat(capture.getValues()).hasSize(1);
    assertThat(capture.getValue())
        .containsExactly(
            CommentForValidation.create(
                CommentForValidation.CommentType.INLINE_COMMENT, draftInline.message),
            CommentForValidation.create(
                CommentForValidation.CommentType.FILE_COMMENT, draftFile.message));
  }

  @Test
  public void validateCommentsInChangeMessage_messageOK() throws Exception {
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT))))
        .andReturn(ImmutableList.of());
    EasyMock.replay(mockCommentValidator);
    PushOneCommit.Result r = createChange();

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
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT);
    EasyMock.expect(
            mockCommentValidator.validateComments(
                ImmutableList.of(
                    CommentForValidation.create(CommentType.CHANGE_MESSAGE, COMMENT_TEXT))))
        .andReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    EasyMock.replay(mockCommentValidator);
    PushOneCommit.Result r = createChange();

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    assertThat(gApi.changes().id(r.getChangeId()).get().messages)
        .hasSize(1); // From the initial commit.
    RestApiException restApiException =
        assertThrows(
            RestApiException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertThat(restApiException.getCause()).isInstanceOf(CommentsRejectedException.class);
    assertThat(
            Iterables.getOnlyElement(
                    ((CommentsRejectedException) restApiException.getCause())
                        .getCommentValidationFailures())
                .getComment()
                .getText())
        .isEqualTo(COMMENT_TEXT);
    assertThat(restApiException.getCause()).hasMessageThat().contains("Oh no!");
    assertThat(gApi.changes().id(r.getChangeId()).get().messages)
        .hasSize(1); // Unchanged from before.
    ChangeMessageInfo message =
        Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(message.message).doesNotContain(COMMENT_TEXT);
  }

  @Test
  public void restApiErrorCode() throws Exception {
    // Test that the correct error code is surfaced at the REST API.
    CommentForValidation commentForValidation =
        CommentForValidation.create(CommentType.INLINE_COMMENT, COMMENT_TEXT);
    EasyMock.expect(mockCommentValidator.validateComments(EasyMock.anyObject()))
        .andReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
    EasyMock.replay(mockCommentValidator);
    PushOneCommit.Result r = createChange();
    DraftInput draft = testCommentUtil.newDraft(COMMENT_TEXT);
    testCommentUtil.addDraft(r.getChangeId(), r.getCommit().getName(), draft);
    ReviewInput input = new ReviewInput();
    input.drafts = DraftHandling.PUBLISH;
    RestResponse response =
        userRestSession.post(
            "/changes/" + r.getChangeId() + "/revisions/" + r.getCommit().getName() + "/review",
            input);
    response.assertStatus(SC_BAD_REQUEST);
  }

  private static CommentInput newComment(String path) {
    return TestCommentUtil.populate(new CommentInput(), path, PostReviewIT.COMMENT_TEXT);
  }
}
