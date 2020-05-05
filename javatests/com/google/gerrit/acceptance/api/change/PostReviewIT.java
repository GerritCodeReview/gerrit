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
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/** Tests for comment validation in {@link PostReview}. */
public class PostReviewIT extends AbstractDaemonTest {

  @Inject private CommentValidator mockCommentValidator;
  @Inject private TestCommentHelper testCommentHelper;
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String COMMENT_TEXT = "The comment text";
  private static final CommentForValidation FILE_COMMENT_FOR_VALIDATION =
      CommentForValidation.create(
          CommentForValidation.CommentSource.HUMAN,
          CommentForValidation.CommentType.FILE_COMMENT,
          COMMENT_TEXT,
          COMMENT_TEXT.length());
  private static final CommentForValidation INLINE_COMMENT_FOR_VALIDATION =
      CommentForValidation.create(
          CommentForValidation.CommentSource.HUMAN,
          CommentForValidation.CommentType.INLINE_COMMENT,
          COMMENT_TEXT,
          COMMENT_TEXT.length());
  private static final CommentForValidation CHANGE_MESSAGE_FOR_VALIDATION =
      CommentForValidation.create(
          CommentForValidation.CommentSource.HUMAN,
          CommentForValidation.CommentType.CHANGE_MESSAGE,
          COMMENT_TEXT,
          COMMENT_TEXT.length());

  @Captor private ArgumentCaptor<ImmutableList<CommentForValidation>> captor;

  private static final Correspondence<CommentForValidation, CommentForValidation>
      COMMENT_CORRESPONDENCE =
          Correspondence.from(
              (left, right) ->
                  left != null
                      && right != null
                      && left.getSource() == right.getSource()
                      && left.getType() == right.getType()
                      && left.getText().equals(right.getText()),
              "matches (ignoring size approximation)");

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
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
    gApi.changes().id(r.getChangeId()).current().review(input);

    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION, FILE_COMMENT_FOR_VALIDATION);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateCommentsInInput_commentRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of(FILE_COMMENT_FOR_VALIDATION.failValidation("Oh no!")));

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION, FILE_COMMENT_FOR_VALIDATION);
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
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of());

    DraftInput draft =
        testCommentHelper.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().getName()).createDraft(draft);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    input.drafts = DraftHandling.PUBLISH;

    gApi.changes().id(r.getChangeId()).current().review(input);
    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION, INLINE_COMMENT_FOR_VALIDATION);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  public void validateDrafts_draftRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of(INLINE_COMMENT_FOR_VALIDATION.failValidation("Oh no!")));

    DraftInput draft =
        testCommentHelper.newDraft(
            r.getChange().currentFilePaths().get(0), Side.REVISION, 1, COMMENT_TEXT);
    testCommentHelper.addDraft(r.getChangeId(), r.getCommit().getName(), draft);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    input.drafts = DraftHandling.PUBLISH;
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION, INLINE_COMMENT_FOR_VALIDATION);
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

    when(mockCommentValidator.validateComments(any(), captor.capture()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    input.drafts = DraftHandling.PUBLISH;
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(2);

    assertValidatorCalledWith(
        CHANGE_MESSAGE_FOR_VALIDATION,
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
  public void validateCommentsInChangeMessage_messageOK() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    int numMessages = gApi.changes().id(r.getChangeId()).get().messages.size();
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION);
    assertThat(gApi.changes().id(r.getChangeId()).get().messages).hasSize(numMessages + 1);
    ChangeMessageInfo message =
        Iterables.getLast(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(message.message).contains(COMMENT_TEXT);
  }

  @Test
  public void validateCommentsInChangeMessage_messageRejected() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(eq(contextFor(r)), captor.capture()))
        .thenReturn(ImmutableList.of(CHANGE_MESSAGE_FOR_VALIDATION.failValidation("Oh no!")));

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    assertThat(gApi.changes().id(r.getChangeId()).get().messages)
        .hasSize(1); // From the initial commit.
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(input));
    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION);
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

  @Test
  @GerritConfig(name = "change.maxComments", value = "7")
  public void restrictNumberOfComments() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());

    PushOneCommit.Result r = createChange();
    String filePath = r.getChange().currentFilePaths().get(0);
    CommentInput commentInput = new CommentInput();
    commentInput.line = 1;
    commentInput.message = "foo";
    commentInput.path = filePath;
    RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(filePath);
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments = ImmutableMap.of(filePath, ImmutableList.of(commentInput));
    reviewInput.robotComments = ImmutableMap.of(filePath, ImmutableList.of(robotCommentInput));
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    // Counting change messages plus comments we now have 4.

    // reviewInput still has both a user and a robot comment (and deduplication is false). We also
    // create a draft, and there's the change message, so that in total there would be 8 comments.
    // The limit is set to 7, so this verifies that all new comments are considered.
    DraftInput draftInline = testCommentHelper.newDraft(filePath, Side.REVISION, 1, "a draft");
    testCommentHelper.addDraft(r.getChangeId(), r.getPatchSetId().getId(), draftInline);
    reviewInput.drafts = DraftHandling.PUBLISH;
    reviewInput.omitDuplicateComments = false;

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(reviewInput));
    assertThat(exception)
        .hasMessageThat()
        .contains("Exceeding maximum number of comments: 4 (existing) + 4 (new) > 7");

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
    assertThat(getRobotComments(r.getChangeId())).hasSize(1);
  }

  @Test
  @GerritConfig(name = "change.cumulativeCommentSizeLimit", value = "7k")
  public void validateCumulativeCommentSize() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(eq(contextFor(r)), any()))
        .thenReturn(ImmutableList.of());

    // Use large sizes because autogenerated messages already have O(100) bytes.
    String commentText2000Bytes = new String(new char[2000]).replace("\0", "x");
    String filePath = r.getChange().currentFilePaths().get(0);
    ReviewInput reviewInput = new ReviewInput().message(commentText2000Bytes);
    CommentInput commentInput = new CommentInput();
    commentInput.line = 1;
    commentInput.message = commentText2000Bytes;
    commentInput.path = filePath;
    reviewInput.comments = ImmutableMap.of(filePath, ImmutableList.of(commentInput));

    // Use up ~4000 bytes.
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);

    // Hit the limit when trying that again.
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(reviewInput));
    assertThat(exception)
        .hasMessageThat()
        .contains("Exceeding maximum cumulative size of comments");
  }

  @Test
  public void ccToReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    // User adds themselves and changes state
    requestScopeOperations.setApiUser(user.id());

    ReviewInput input = new ReviewInput().reviewer(user.id().toString(), ReviewerState.CC, false);
    gApi.changes().id(r.getChangeId()).current().review(input);

    Map<ReviewerState, Collection<AccountInfo>> reviewers =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = Iterables.getOnlyElement(reviewers.get(ReviewerState.CC));
    assertThat(reviewer._accountId).isEqualTo(user.id().get());

    // CC -> Reviewer
    ReviewInput input2 = new ReviewInput().reviewer(user.id().toString());
    gApi.changes().id(r.getChangeId()).current().review(input2);

    Map<ReviewerState, Collection<AccountInfo>> reviewers2 =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers2).hasSize(1);
    AccountInfo reviewer2 = Iterables.getOnlyElement(reviewers2.get(ReviewerState.REVIEWER));
    assertThat(reviewer2._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void reviewerToCc() throws Exception {
    // Admin owns the change
    PushOneCommit.Result r = createChange();
    // User adds themselves and changes state
    requestScopeOperations.setApiUser(user.id());

    ReviewInput input = new ReviewInput().reviewer(user.id().toString());
    gApi.changes().id(r.getChangeId()).current().review(input);

    Map<ReviewerState, Collection<AccountInfo>> reviewers =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = Iterables.getOnlyElement(reviewers.get(ReviewerState.REVIEWER));
    assertThat(reviewer._accountId).isEqualTo(user.id().get());

    // Reviewer -> CC
    ReviewInput input2 = new ReviewInput().reviewer(user.id().toString(), ReviewerState.CC, false);
    gApi.changes().id(r.getChangeId()).current().review(input2);

    Map<ReviewerState, Collection<AccountInfo>> reviewers2 =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers2).hasSize(1);
    AccountInfo reviewer2 = Iterables.getOnlyElement(reviewers2.get(ReviewerState.CC));
    assertThat(reviewer2._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void votingMakesCallerReviewer() throws Exception {
    // Admin owns the change
    PushOneCommit.Result r = createChange();
    // User adds themselves and changes state
    requestScopeOperations.setApiUser(user.id());

    ReviewInput input = new ReviewInput().label("Code-Review", 1);
    gApi.changes().id(r.getChangeId()).current().review(input);

    Map<ReviewerState, Collection<AccountInfo>> reviewers =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = Iterables.getOnlyElement(reviewers.get(ReviewerState.REVIEWER));
    assertThat(reviewer._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void commentingMakesUserCC() throws Exception {
    // Admin owns the change
    PushOneCommit.Result r = createChange();
    // User adds themselves and changes state
    requestScopeOperations.setApiUser(user.id());

    ReviewInput input = new ReviewInput().message("Foo bar!");
    gApi.changes().id(r.getChangeId()).current().review(input);

    Map<ReviewerState, Collection<AccountInfo>> reviewers =
        gApi.changes().id(r.getChangeId()).get().reviewers;
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = Iterables.getOnlyElement(reviewers.get(ReviewerState.CC));
    assertThat(reviewer._accountId).isEqualTo(user.id().get());
  }

  private List<RobotCommentInfo> getRobotComments(String changeId) throws RestApiException {
    return gApi.changes().id(changeId).robotComments().values().stream()
        .flatMap(Collection::stream)
        .collect(toList());
  }

  private static CommentInput newComment(String path) {
    return TestCommentHelper.populate(new CommentInput(), path, PostReviewIT.COMMENT_TEXT);
  }

  private static CommentValidationContext contextFor(PushOneCommit.Result result) {
    return CommentValidationContext.create(
        result.getChange().getId().get(), result.getChange().project().get());
  }

  private void assertValidatorCalledWith(CommentForValidation... commentsForValidation) {
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getValue())
        .comparingElementsUsing(COMMENT_CORRESPONDENCE)
        .containsExactly(commentsForValidation);
  }
}
