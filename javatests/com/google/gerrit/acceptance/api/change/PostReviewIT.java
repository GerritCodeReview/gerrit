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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestExtensions.TestSubmitRule;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.gerrit.server.update.CommentsRejectedException;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/** Tests for comment validation in {@link PostReview}. */
public class PostReviewIT extends AbstractDaemonTest {

  @Inject private TestCommentHelper testCommentHelper;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private ProjectOperations projectOperations;

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

  private static CommentValidator mockCommentValidator;
  @Captor private ArgumentCaptor<ImmutableList<CommentForValidation>> captor;
  @Captor private ArgumentCaptor<CommentValidationContext> captorCtx;

  @SuppressWarnings("unused")
  private AutoCloseable mockCommentValidatorPlugin;

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

  public static class MockCommentValidatorModule extends FactoryModule {
    @Override
    public void configure() {
      // by default return no validation errors
      bind(CommentValidator.class)
          .annotatedWith(Exports.named(mockCommentValidator.getClass()))
          .toInstance(mockCommentValidator);
      bind(CommentValidator.class).toInstance(mockCommentValidator);
    }
  }

  @Before
  public void resetMockCommentValidator() throws Exception {
    mockCommentValidator = mock(CommentValidator.class);
    initMocks(this);
    mockCommentValidatorPlugin = super.installPlugin("validator", MockCommentValidatorModule.class);
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());
  }

  @Test
  public void validateCommentsInInput_commentOK() throws Exception {
    PushOneCommit.Result r = createChange();
    when(mockCommentValidator.validateComments(captorCtx.capture(), captor.capture()))
        .thenReturn(ImmutableList.of());

    ReviewInput input = new ReviewInput().message(COMMENT_TEXT);
    CommentInput comment = newComment(r.getChange().currentFilePaths().get(0));
    comment.updated = new Timestamp(0);
    input.comments = ImmutableMap.of(comment.path, ImmutableList.of(comment));

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).isEmpty();
    gApi.changes().id(r.getChangeId()).current().review(input);

    assertValidatorCalledWith(CHANGE_MESSAGE_FOR_VALIDATION, FILE_COMMENT_FOR_VALIDATION);
    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
    assertThat(captorCtx.getAllValues()).containsExactly(contextFor(r));
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
    // Comment validators called twice: first when the draft was created, and second when it was
    // published.
    assertValidatorCalledWith(
        /* numInvocations= */ 2, CHANGE_MESSAGE_FOR_VALIDATION, INLINE_COMMENT_FOR_VALIDATION);
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
    BadRequestException badRequestException =
        assertThrows(
            BadRequestException.class,
            () -> testCommentHelper.addDraft(r.getChangeId(), r.getCommit().getName(), draft));
    assertValidatorCalledWith(INLINE_COMMENT_FOR_VALIDATION);
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
  @GerritConfig(name = "change.maxComments", value = "5")
  public void restrictNumberOfComments() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());

    PushOneCommit.Result r = createChange();
    String filePath = r.getChange().currentFilePaths().get(0);
    CommentInput commentInput = new CommentInput();
    commentInput.line = 1;
    commentInput.message = "foo";
    commentInput.path = filePath;
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments = ImmutableMap.of(filePath, ImmutableList.of(commentInput));
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    // reviewInput still has both a user comment (and deduplication is false). We also
    // create a draft, and there's the change message, so that in total there would be 6 comments.
    // The limit is set to 5, so this verifies that all new comments are considered.
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
        .contains("Exceeding maximum number of comments: 3 (existing) + 3 (new) > 5");

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(1);
  }

  @Test
  @GerritConfig(name = "change.maxCommentsPerUser", value = "2")
  public void restrictNumberOfCommentsPerUser() throws Exception {
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());

    PushOneCommit.Result r = createChange();
    String filePath = r.getChange().currentFilePaths().get(0);

    CommentInput comment1 = new CommentInput();
    comment1.line = 1;
    comment1.message = "first comment";
    comment1.path = filePath;

    CommentInput comment2 = new CommentInput();
    comment2.line = 2;
    comment2.message = "second comment";
    comment2.path = filePath;

    CommentInput comment3 = new CommentInput();
    comment3.line = 3;
    comment3.message = "third comment";
    comment3.path = filePath;

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments =
        ImmutableMap.of(filePath, ImmutableList.of(comment1, comment2, comment3));

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(r.getChangeId()).current().review(reviewInput));

    assertThat(exception)
        .hasMessageThat()
        .contains("Exceeding maximum comments per user: 1 (existing) + 4 (new) > 2");

    assertThat(testCommentHelper.getPublishedComments(r.getChangeId())).hasSize(0);
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

    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
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

  @Test
  public void extendChangeMessageFromPlugin() throws Exception {
    PushOneCommit.Result r = createChange();

    String testMessage = "hello from plugin";
    TestOnPostReview testOnPostReview = new TestOnPostReview(testMessage);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      gApi.changes().id(r.getChangeId()).current().review(input);
      Collection<ChangeMessageInfo> messages = gApi.changes().id(r.getChangeId()).get().messages;
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo(String.format("Patch Set 1: Code-Review+1\n\n%s\n", testMessage));
    }
  }

  @Test
  public void extendChangeMessageFromMultiplePlugins() throws Exception {
    PushOneCommit.Result r = createChange();

    String testMessage1 = "hello from plugin 1";
    String testMessage2 = "message from plugin 2";
    TestOnPostReview testOnPostReview1 = new TestOnPostReview(testMessage1);
    TestOnPostReview testOnPostReview2 = new TestOnPostReview(testMessage2);
    try (Registration registration =
        extensionRegistry.newRegistration().add(testOnPostReview1).add(testOnPostReview2)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      gApi.changes().id(r.getChangeId()).current().review(input);
      Collection<ChangeMessageInfo> messages = gApi.changes().id(r.getChangeId()).get().messages;
      assertThat(Iterables.getLast(messages).message)
          .isEqualTo(
              String.format(
                  "Patch Set 1: Code-Review+1\n\n%s\n\n%s\n", testMessage1, testMessage2));
    }
  }

  @Test
  public void onPostReviewExtensionThatDoesntExtendTheChangeMessage() throws Exception {
    PushOneCommit.Result r = createChange();

    TestOnPostReview testOnPostReview = new TestOnPostReview(/* message= */ null);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      gApi.changes().id(r.getChangeId()).current().review(input);
      Collection<ChangeMessageInfo> messages = gApi.changes().id(r.getChangeId()).get().messages;
      assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1: Code-Review+1");
    }
  }

  @Test
  public void onPostReviewCallbackGetsCorrectChangeAndPatchSet() throws Exception {
    PushOneCommit.Result r = createChange();
    amendChange(r.getChangeId());

    TestOnPostReview testOnPostReview = new TestOnPostReview(/* message= */ null);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);

      // Vote on current patch set.
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertChangeAndPatchSet(r.getChange().getId(), 2);

      // Vote on old patch set.
      gApi.changes().id(r.getChangeId()).revision(1).review(input);
      testOnPostReview.assertChangeAndPatchSet(r.getChange().getId(), 1);
    }
  }

  @Test
  public void onPostReviewCallbackGetsCorrectUser() throws Exception {
    PushOneCommit.Result r = createChange();

    TestOnPostReview testOnPostReview = new TestOnPostReview(/* message= */ null);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);

      // Vote from admin.
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertUser(admin);

      // Vote from user.
      requestScopeOperations.setApiUser(user.id());
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertUser(user);
    }
  }

  @Test
  public void onPostReviewApprovedIsReturnedForLabelsAndDetailedLabels() throws Exception {
    // Create Verify label with NO_BLOCK function and allow voting on it.
    // When the NO_BLOCK function is used for a label, the "approved" is set by the
    // LabelsJson.setLabelScores method instead of LabelsJson.initLabels method.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
                  LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"))
              .setNoBlockFunction();
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
    PushOneCommit.Result r = createChange();
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2).label(LabelId.VERIFIED, 1);
    gApi.changes().id(r.getChangeId()).current().review(input);

    input = new ReviewInput();
    input.message = "first message";
    input.responseFormatOptions = ImmutableList.of(ListChangesOption.DETAILED_LABELS);
    ReviewResult reviewResult = gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(reviewResult.changeInfo.labels.keySet())
        .containsExactly(LabelId.CODE_REVIEW, LabelId.VERIFIED);
    assertThat(reviewResult.changeInfo.labels.get(LabelId.CODE_REVIEW).approved).isNotNull();
    assertThat(reviewResult.changeInfo.labels.get(LabelId.VERIFIED).approved).isNotNull();

    input = new ReviewInput();
    input.message = "second message";
    input.responseFormatOptions = ImmutableList.of(ListChangesOption.LABELS);
    reviewResult = gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(reviewResult.changeInfo.labels).containsKey(LabelId.CODE_REVIEW);
    assertThat(reviewResult.changeInfo.labels.get(LabelId.CODE_REVIEW).approved).isNotNull();
    assertThat(reviewResult.changeInfo.labels.get(LabelId.VERIFIED).approved).isNotNull();
  }

  @Test
  public void onPostReviewCallbackGetsCorrectApprovals() throws Exception {
    PushOneCommit.Result r = createChange();

    TestOnPostReview testOnPostReview = new TestOnPostReview(/* message= */ null);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      // Add a new vote.
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertApproval(
          LabelId.CODE_REVIEW, /* expectedOldValue= */ 0, /* expectedNewValue= */ 1);

      // Update an existing vote.
      input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertApproval(
          LabelId.CODE_REVIEW, /* expectedOldValue= */ 1, /* expectedNewValue= */ 2);

      // Post without changing the vote.
      input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertApproval(
          LabelId.CODE_REVIEW, /* expectedOldValue= */ null, /* expectedNewValue= */ 2);

      // Delete the vote.
      input = new ReviewInput().label(LabelId.CODE_REVIEW, 0);
      gApi.changes().id(r.getChangeId()).current().review(input);
      testOnPostReview.assertApproval(
          LabelId.CODE_REVIEW, /* expectedOldValue= */ 2, /* expectedNewValue= */ 0);
    }
  }

  @Test
  public void votingTheSameVoteSecondTime() throws Exception {
    PushOneCommit.Result r = createChange();

    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    sender.clear();

    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(r.getChange().approvals().values()).hasSize(1);

    // Post without changing the vote.
    ChangeNotes notes = notesFactory.create(project, r.getChange().getId());
    ObjectId metaId = notes.getMetaId();
    assertAttentionSet(notes.getAttentionSet(), user.id());
    input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);
    notes = notesFactory.create(project, r.getChange().getId());
    // Change meta ID did not change since the update is No/Op. Attention set is same.
    assertThat(notes.getMetaId()).isEqualTo(metaId);
    assertAttentionSet(notes.getAttentionSet(), user.id());

    // Second vote replaced the original vote, so still only one vote.
    assertThat(r.getChange().approvals().values()).hasSize(1);
    List<ChangeMessageInfo> changeMessages = gApi.changes().id(r.getChangeId()).messages();

    // Only the last change message is about Code-Review+2
    assertThat(Iterables.getLast(changeMessages).message).isEqualTo("Patch Set 1: Code-Review+2");
    changeMessages.remove(changeMessages.size() - 1);
    assertThat(Iterables.getLast(changeMessages).message)
        .isNotEqualTo("Patch Set 1: Code-Review+2");

    // Only one email is about Code-Review +2 was sent.
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .contains("Patch Set 1: Code-Review+2");
  }

  @Test
  public void votingTheSameVoteSecondTimeExtendsOnPostReviewWithOldNullValue() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(r.getChange().approvals().values()).hasSize(1);

    TestOnPostReview testOnPostReview = new TestOnPostReview(/* message= */ null);
    try (Registration registration = extensionRegistry.newRegistration().add(testOnPostReview)) {
      // Post without changing the vote.
      input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
      gApi.changes().id(r.getChangeId()).current().review(input);

      testOnPostReview.assertApproval(
          LabelId.CODE_REVIEW, /* expectedOldValue= */ null, /* expectedNewValue= */ 2);
    }
  }

  @Test
  public void votingTheSameVoteSecondTimeDoesNotFireOnCommentAdded() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add a new vote.
    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
    gApi.changes().id(r.getChangeId()).current().review(input);
    assertThat(r.getChange().approvals().values()).hasSize(1);

    TestListener testListener = new TestListener();
    try (Registration registration = extensionRegistry.newRegistration().add(testListener)) {
      // Post without changing the vote.
      input = new ReviewInput().label(LabelId.CODE_REVIEW, 2);
      gApi.changes().id(r.getChangeId()).current().review(input);

      // Event not fired.
      assertThat(testListener.lastCommentAddedEvent).isNull();
    }
  }

  @Test
  public void currentRevisionNumberIsSetOnReturnedChangeInfo() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo changeInfo =
        gApi.changes().id(r.getChangeId()).current().review(ReviewInput.dislike()).changeInfo;
    assertThat(changeInfo.currentRevisionNumber).isEqualTo(1);
    amendChange(r.getChangeId());
    changeInfo =
        gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend()).changeInfo;
    assertThat(changeInfo.currentRevisionNumber).isEqualTo(2);

    // Check that the current revision number is also returned when list changes options are
    // requested.
    ReviewInput reviewInput = ReviewInput.approve();
    reviewInput.responseFormatOptions =
        ImmutableList.copyOf(EnumSet.allOf(ListChangesOption.class));
    changeInfo = gApi.changes().id(r.getChangeId()).current().review(reviewInput).changeInfo;
    assertThat(changeInfo.currentRevisionNumber).isEqualTo(2);
  }

  @Test
  public void submitRulesAreInvokedOnlyOnce() throws Exception {
    PushOneCommit.Result r = createChange();

    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      gApi.changes().id(r.getChangeId()).current().review(input);
    }

    assertThat(testSubmitRule.count()).isEqualTo(1);
  }

  @Test
  public void submitRulesAreInvokedOnlyOnce_allOptionsSet() throws Exception {
    PushOneCommit.Result r = createChange();

    TestSubmitRule testSubmitRule = new TestSubmitRule();
    try (Registration registration = extensionRegistry.newRegistration().add(testSubmitRule)) {
      ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 1);
      input.responseFormatOptions = ImmutableList.copyOf(EnumSet.allOf(ListChangesOption.class));
      gApi.changes().id(r.getChangeId()).current().review(input);
    }

    assertThat(testSubmitRule.count()).isEqualTo(1);
  }

  @Test
  public void addingReviewers() throws Exception {
    PushOneCommit.Result r = createChange();

    TestAccount user2 = accountCreator.user2();

    TestReviewerAddedListener testReviewerAddedListener = new TestReviewerAddedListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testReviewerAddedListener)) {
      // add user and user2
      ReviewResult reviewResult =
          gApi.changes()
              .id(r.getChangeId())
              .current()
              .review(ReviewInput.create().reviewer(user.email()).reviewer(user2.email()));

      assertThat(
              reviewResult.reviewers.values().stream()
                  .filter(a -> a.reviewers != null)
                  .map(a -> Iterables.getOnlyElement(a.reviewers).name)
                  .collect(toImmutableSet()))
          .containsExactly(user.fullName(), user2.fullName());
    }

    assertThat(
            gApi.changes().id(r.getChangeId()).reviewers().stream()
                .map(a -> a.name)
                .collect(toImmutableSet()))
        .containsExactly(user.fullName(), user2.fullName());

    // Ensure only one batch email was sent for this operation
    FakeEmailSender.Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .containsMatch(
            Pattern.quote("Hello ")
                + "("
                + Pattern.quote(String.format("%s, %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s, %s", user2.fullName(), user.fullName()))
                + ")");
    assertThat(message.htmlBody())
        .containsMatch(
            "("
                + Pattern.quote(String.format("%s and %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s and %s", user2.fullName(), user.fullName()))
                + ")"
                + Pattern.quote(" to <strong>review</strong> this change"));

    // Ensure that a batch event has been sent:
    // * 1 batch event for adding user and user2 as reviewers
    assertThat(testReviewerAddedListener.receivedEvents).hasSize(1);
    assertThat(testReviewerAddedListener.getReviewerIds()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void rejectAddingReviewerIfReviewerUserIdentifierIsMissing() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add reviewer with ReviewerInput where the 'reviewer' field is not set.
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.state = ReviewerState.REVIEWER;

    ReviewInput reviewInput = ReviewInput.create();
    reviewInput.reviewers = ImmutableList.of(reviewerInput);

    ReviewResult reviewResult = gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    assertThat(reviewResult.error).isEqualTo("error adding reviewer");
    assertThat(reviewResult.reviewers.keySet()).containsExactly(reviewerInput.reviewer);
    assertThat(reviewResult.reviewers.get(reviewerInput.reviewer).error)
        .isEqualTo("reviewer user identifier is required");

    // Add reviewer with ReviewerInput where the 'reviewer' field is set to an empty string.
    reviewerInput.reviewer = "";
    reviewResult = gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    assertThat(reviewResult.error).isEqualTo("error adding reviewer");
    assertThat(reviewResult.reviewers.keySet()).containsExactly(reviewerInput.reviewer);
    assertThat(reviewResult.reviewers.get(reviewerInput.reviewer).error)
        .isEqualTo("reviewer user identifier is required");

    // Add reviewer with ReviewerInput where the 'reviewer' field is set to an empty string after
    // trimming it.
    reviewerInput.reviewer = "   ";
    reviewResult = gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    assertThat(reviewResult.error).isEqualTo("error adding reviewer");
    assertThat(reviewResult.reviewers.keySet()).containsExactly(reviewerInput.reviewer);
    assertThat(reviewResult.reviewers.get(reviewerInput.reviewer).error)
        .isEqualTo("reviewer user identifier is required");
  }

  @Test
  public void deletingReviewers() throws Exception {
    PushOneCommit.Result r = createChange();

    TestAccount user2 = accountCreator.user2();

    // add user and user2
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.create().reviewer(user.email()).reviewer(user2.email()));

    sender.clear();

    TestReviewerDeletedListener testReviewerDeletedListener = new TestReviewerDeletedListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testReviewerDeletedListener)) {
      // remove user and user2
      ReviewResult reviewResult =
          gApi.changes()
              .id(r.getChangeId())
              .current()
              .review(
                  ReviewInput.create()
                      .reviewer(user.email(), ReviewerState.REMOVED, /* confirmed= */ true)
                      .reviewer(user2.email(), ReviewerState.REMOVED, /* confirmed= */ true));

      assertThat(
              reviewResult.reviewers.values().stream()
                  .map(a -> a.removed.name)
                  .collect(toImmutableSet()))
          .containsExactly(user.fullName(), user2.fullName());
    }

    assertThat(gApi.changes().id(r.getChangeId()).reviewers()).isEmpty();

    // Ensure only one batch email was sent for this operation
    FakeEmailSender.Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .containsMatch(
            Pattern.quote("removed ")
                + "("
                + Pattern.quote(String.format("%s, %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s, %s", user2.fullName(), user.fullName()))
                + ")");
    assertThat(message.htmlBody())
        .containsMatch(
            Pattern.quote("removed ")
                + "("
                + Pattern.quote(String.format("%s and %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s and %s", user2.fullName(), user.fullName()))
                + ")");

    // Ensure that events have been sent:
    // * 2 events for removing user and user2 as reviewers (one event per removed reviewer, batch
    //   event not available for reviewer removal)
    assertThat(testReviewerDeletedListener.receivedEvents).hasSize(2);
    assertThat(testReviewerDeletedListener.getReviewerIds()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void addingAndDeletingReviewers() throws Exception {
    PushOneCommit.Result r = createChange();

    TestAccount user2 = accountCreator.user2();
    TestAccount user3 = accountCreator.create("user3", "user3@email.com", "user3", "user3");
    TestAccount user4 = accountCreator.create("user4", "user4@email.com", "user4", "user4");

    // add user and user2
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.create().reviewer(user.email()).reviewer(user2.email()));

    sender.clear();

    TestReviewerAddedListener testReviewerAddedListener = new TestReviewerAddedListener();
    TestReviewerDeletedListener testReviewerDeletedListener = new TestReviewerDeletedListener();
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(testReviewerAddedListener)
            .add(testReviewerDeletedListener)) {
      // remove user and user2 while adding user3 and user4
      ReviewResult reviewResult =
          gApi.changes()
              .id(r.getChangeId())
              .current()
              .review(
                  ReviewInput.create()
                      .reviewer(user.email(), ReviewerState.REMOVED, /* confirmed= */ true)
                      .reviewer(user2.email(), ReviewerState.REMOVED, /* confirmed= */ true)
                      .reviewer(user3.email())
                      .reviewer(user4.email()));

      assertThat(
              reviewResult.reviewers.values().stream()
                  .filter(a -> a.removed != null)
                  .map(a -> a.removed.name)
                  .collect(toImmutableSet()))
          .containsExactly(user.fullName(), user2.fullName());
      assertThat(
              reviewResult.reviewers.values().stream()
                  .filter(a -> a.reviewers != null)
                  .map(a -> Iterables.getOnlyElement(a.reviewers).name)
                  .collect(toImmutableSet()))
          .containsExactly(user3.fullName(), user4.fullName());
    }

    assertThat(
            gApi.changes().id(r.getChangeId()).reviewers().stream()
                .map(a -> a.name)
                .collect(toImmutableSet()))
        .containsExactly(user3.fullName(), user4.fullName());

    // Ensure only one batch email was sent for this operation
    FakeEmailSender.Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .containsMatch(
            Pattern.quote("Hello ")
                + "("
                + Pattern.quote(String.format("%s, %s", user3.fullName(), user4.fullName()))
                + "|"
                + Pattern.quote(String.format("%s, %s", user4.fullName(), user3.fullName()))
                + ")");
    assertThat(message.htmlBody())
        .containsMatch(
            "("
                + Pattern.quote(String.format("%s and %s", user3.fullName(), user4.fullName()))
                + "|"
                + Pattern.quote(String.format("%s and %s", user4.fullName(), user3.fullName()))
                + ")"
                + Pattern.quote(" to <strong>review</strong> this change"));

    assertThat(message.body())
        .containsMatch(
            Pattern.quote("removed ")
                + "("
                + Pattern.quote(String.format("%s, %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s, %s", user2.fullName(), user.fullName()))
                + ")");
    assertThat(message.htmlBody())
        .containsMatch(
            Pattern.quote("removed ")
                + "("
                + Pattern.quote(String.format("%s and %s", user.fullName(), user2.fullName()))
                + "|"
                + Pattern.quote(String.format("%s and %s", user2.fullName(), user.fullName()))
                + ")");

    // Ensure that events have been sent:
    // * 1 batch event for adding user3 and user4 as reviewers
    // * 2 events for removing user and user2 as reviewers (one event per removed reviewer, batch
    //   event not available for reviewer removal)
    assertThat(testReviewerAddedListener.receivedEvents).hasSize(1);
    assertThat(testReviewerAddedListener.getReviewerIds()).containsExactly(user3.id(), user4.id());
    assertThat(testReviewerDeletedListener.receivedEvents).hasSize(2);
    assertThat(testReviewerDeletedListener.getReviewerIds()).containsExactly(user.id(), user2.id());
  }

  @Test
  public void deletingNonExistingReviewerFails() throws Exception {
    PushOneCommit.Result r = createChange();

    ResourceNotFoundException resourceNotFoundException =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.changes()
                    .id(r.getChangeId())
                    .current()
                    .review(
                        ReviewInput.create()
                            .reviewer(user.email(), ReviewerState.REMOVED, /* confirmed= */ true)));
    assertThat(resourceNotFoundException)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Reviewer %s doesn't exist in the change, hence can't delete it", user.fullName()));
  }

  @Test
  public void addingAndDeletingSameReviewerFails() throws Exception {
    PushOneCommit.Result r = createChange();

    ResourceNotFoundException resourceNotFoundException =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                gApi.changes()
                    .id(r.getChangeId())
                    .current()
                    .review(
                        ReviewInput.create()
                            .reviewer(user.email())
                            .reviewer(user.email(), ReviewerState.REMOVED, true)));
    assertThat(resourceNotFoundException)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Reviewer %s doesn't exist in the change," + " hence can't delete it",
                user.fullName()));
  }

  @Test
  public void votesInChangeMessageAreSorted() throws Exception {
    // Create Verify label and allow voting on it.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();

    ReviewInput input = new ReviewInput().label(LabelId.CODE_REVIEW, 2).label(LabelId.VERIFIED, 1);
    gApi.changes().id(r.getChangeId()).current().review(input);

    Collection<ChangeMessageInfo> messages = gApi.changes().id(r.getChangeId()).get().messages;
    assertThat(Iterables.getLast(messages).message)
        .isEqualTo(String.format("Patch Set 1: Code-Review+2 Verified+1"));
  }

  @Test
  public void setReadyForReviewSendsNotificationsForRevertedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
    RevertInput in = new RevertInput();
    in.workInProgress = true;
    String changeId = gApi.changes().id(r.getChangeId()).revert(in).get().changeId;
    requestScopeOperations.setApiUser(admin.id());
    assertThat(gApi.changes().id(changeId).get().workInProgress).isTrue();

    ReviewResult reviewResult =
        gApi.changes().id(changeId).current().review(ReviewInput.create().setReady(true));
    assertThat(reviewResult.ready).isTrue();

    assertThat(gApi.changes().id(changeId).get().workInProgress).isNull();
    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Patch Set 1: Reverted
    List<ChangeMessageInfo> sourceMessages =
        new ArrayList<>(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(sourceMessages).hasSize(4);
    String expectedMessage = String.format("Created a revert of this change as %s", changeId);
    assertThat(sourceMessages.get(3).message).isEqualTo(expectedMessage);
  }

  private static class TestListener implements CommentAddedListener {
    public CommentAddedListener.Event lastCommentAddedEvent;

    @Override
    public void onCommentAdded(Event event) {
      lastCommentAddedEvent = event;
    }
  }

  private static CommentInput newComment(String path) {
    return TestCommentHelper.populate(new CommentInput(), path, PostReviewIT.COMMENT_TEXT);
  }

  private static CommentValidationContext contextFor(PushOneCommit.Result result) {
    return CommentValidationContext.create(
        result.getChange().getId().get(),
        result.getChange().project().get(),
        result.getChange().change().getDest().branch());
  }

  private void assertValidatorCalledWith(CommentForValidation... commentsForValidation) {
    assertValidatorCalledWith(/* numInvocations= */ 1, commentsForValidation);
  }

  private void assertValidatorCalledWith(
      int numInvocations, CommentForValidation... commentsForValidation) {
    assertThat(captor.getAllValues()).hasSize(numInvocations);
    assertThat(captor.getValue())
        .comparingElementsUsing(COMMENT_CORRESPONDENCE)
        .containsExactly(commentsForValidation);
  }

  private static class TestOnPostReview implements OnPostReview {
    private final Optional<String> message;

    private Change.Id changeId;
    private PatchSet.Id patchSetId;
    private Account.Id accountId;
    private Map<String, Short> oldApprovals;
    private Map<String, Short> approvals;

    TestOnPostReview(@Nullable String message) {
      this.message = Optional.ofNullable(message);
    }

    @Override
    public Optional<String> getChangeMessageAddOn(
        Instant when,
        IdentifiedUser user,
        ChangeNotes changeNotes,
        PatchSet patchSet,
        Map<String, Short> oldApprovals,
        Map<String, Short> approvals) {
      this.changeId = changeNotes.getChangeId();
      this.patchSetId = patchSet.id();
      this.accountId = user.getAccountId();
      this.oldApprovals = oldApprovals;
      this.approvals = approvals;
      return message;
    }

    public void assertChangeAndPatchSet(Change.Id expectedChangeId, int expectedPatchSetNum) {
      assertThat(changeId).isEqualTo(expectedChangeId);
      assertThat(patchSetId.get()).isEqualTo(expectedPatchSetNum);
    }

    public void assertUser(TestAccount expectedUser) {
      assertThat(accountId).isEqualTo(expectedUser.id());
    }

    public void assertApproval(
        String labelName, @Nullable Integer expectedOldValue, int expectedNewValue) {
      assertThat(oldApprovals)
          .containsExactly(
              labelName, expectedOldValue != null ? expectedOldValue.shortValue() : null);
      assertThat(approvals).containsExactly(labelName, (short) expectedNewValue);
    }
  }

  private static class TestReviewerAddedListener implements ReviewerAddedListener {
    List<ReviewerAddedListener.Event> receivedEvents = new ArrayList<>();

    @Override
    public void onReviewersAdded(ReviewerAddedListener.Event event) {
      receivedEvents.add(event);
    }

    public ImmutableSet<Account.Id> getReviewerIds() {
      return receivedEvents.stream()
          .flatMap(e -> e.getReviewers().stream())
          .map(accountInfo -> Account.id(accountInfo._accountId))
          .collect(toImmutableSet());
    }
  }

  private static class TestReviewerDeletedListener implements ReviewerDeletedListener {
    List<ReviewerDeletedListener.Event> receivedEvents = new ArrayList<>();

    @Override
    public void onReviewerDeleted(ReviewerDeletedListener.Event event) {
      receivedEvents.add(event);
    }

    public ImmutableSet<Account.Id> getReviewerIds() {
      return receivedEvents.stream()
          .map(ReviewerDeletedListener.Event::getReviewer)
          .map(accountInfo -> Account.id(accountInfo._accountId))
          .collect(toImmutableSet());
    }
  }

  private static void assertAttentionSet(
      ImmutableSet<AttentionSetUpdate> attentionSet, Account.Id... accounts) {
    assertThat(attentionSet.stream().map(AttentionSetUpdate::account).collect(Collectors.toList()))
        .containsExactlyElementsIn(accounts);
  }
}
