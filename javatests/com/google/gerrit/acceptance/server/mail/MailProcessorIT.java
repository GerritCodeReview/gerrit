// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.mail.MailProcessingUtil;
import com.google.gerrit.server.mail.receive.MailProcessor;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MailProcessorIT extends AbstractMailIT {
  @Inject private MailProcessor mailProcessor;
  @Inject private AccountOperations accountOperations;
  @Inject private TestCommentHelper testCommentHelper;

  private static final CommentValidator mockCommentValidator = mock(CommentValidator.class);

  private static final String COMMENT_TEXT = "The comment text";

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(CommentValidator.class)
            .annotatedWith(Exports.named(mockCommentValidator.getClass()))
            .toInstance(mockCommentValidator);
        bind(CommentValidator.class).toInstance(mockCommentValidator);
      }
    };
  }

  @BeforeClass
  public static void setUpMock() {
    // Let the mock comment validator accept all comments during test setup.
    when(mockCommentValidator.validateComments(any(), any())).thenReturn(ImmutableList.of());
  }

  @Before
  public void setUp() {
    clearInvocations(mockCommentValidator);
  }

  @Test
  public void parseAndPersistChangeMessage() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", "Test Message", null, null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    mailProcessor.process(b.build());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n\nTest Message");
    assertThat(Iterables.getLast(messages).tag).isEqualTo("mailMessageId=some id");
  }

  @Test
  public void parseAndPersistInlineComment() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, "Some Inline Comment", null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    mailProcessor.process(b.build());

    // Assert messages
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n\n(1 comment)");
    assertThat(Iterables.getLast(messages).tag).isEqualTo("mailMessageId=some id");

    // Assert comment
    comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(3);
    assertThat(comments.get(2).message).isEqualTo("Some Inline Comment");
    assertThat(comments.get(2).tag).isEqualTo("mailMessageId=some id");
    assertThat(comments.get(2).inReplyTo).isEqualTo(comments.get(1).id);
  }

  @Test
  public void parseAndPersistFileComment() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            getChangeUrl(changeInfo) + "/1", null, null, "Some Comment on File 1", null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    mailProcessor.process(b.build());

    // Assert messages
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n\n(1 comment)");
    assertThat(Iterables.getLast(messages).tag).isEqualTo("mailMessageId=some id");

    // Assert comment
    comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(3);
    assertThat(comments.get(0).message).isEqualTo("Some Comment on File 1");
    assertThat(comments.get(0).inReplyTo).isNull();
    assertThat(comments.get(0).tag).isEqualTo("mailMessageId=some id");
    assertThat(comments.get(0).path).isEqualTo("gerrit-server/test.txt");
  }

  @Test
  public void parseAndPersistMessageTwice() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, "Some Inline Comment", null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    mailProcessor.process(b.build());
    comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(3);

    // Check that the comment has not been persisted a second time
    mailProcessor.process(b.build());
    comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(3);
  }

  @Test
  public void parseAndPersistMessageFromInactiveAccount() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));
    assertThat(comments).hasSize(2);

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, "Some Inline Comment", null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    // Set account state to inactive
    accountOperations.account(user.id()).forUpdate().inactive().update();

    mailProcessor.process(b.build());
    comments = gApi.changes().id(changeId).current().commentsAsList();

    // Check that comment size has not changed
    assertThat(comments).hasSize(2);
  }

  @Test
  public void sendNotificationAfterPersistingComments() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(2);
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", "Test Message", null, null, null);
    MailMessage.Builder b =
        messageBuilderWithDefaultFields()
            .from(user.getEmailAddress())
            .textContent(txt + textFooterForChange(changeInfo._number, ts));

    sender.clear();
    mailProcessor.process(b.build());

    assertNotifyTo(admin);
  }

  @Test
  public void sendNotificationOnMissingMetadatas() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    assertThat(comments).hasSize(2);
    String ts = "null"; // Erroneous timestamp to be used in erroneous metadatas

    // Build Message
    String txt =
        newPlaintextBody(getChangeUrl(changeInfo) + "/1", "Test Message", null, null, null);
    MailMessage.Builder b =
        messageBuilderWithDefaultFields()
            .from(user.getEmailAddress())
            .textContent(txt + textFooterForChange(changeInfo._number, ts));

    sender.clear();
    mailProcessor.process(b.build());

    assertNotifyTo(user);
    Message message = sender.nextMessage();
    assertThat(message.body()).contains("was unable to parse your email");
    assertThat(message.headers()).containsKey("Subject");
  }

  @Test
  public void validateChangeMessage_rejected() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    setupFailValidation(
        CommentForValidation.CommentType.CHANGE_MESSAGE, changeInfo.project, changeInfo._number);

    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt = newPlaintextBody(getChangeUrl(changeInfo) + "/1", COMMENT_TEXT, null, null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    Collection<CommentInfo> commentsBefore = testCommentHelper.getPublishedComments(changeId);
    mailProcessor.process(b.build());
    assertThat(testCommentHelper.getPublishedComments(changeId)).isEqualTo(commentsBefore);

    assertNotifyTo(user);
    Message message = sender.nextMessage();
    assertThat(message.body()).contains("rejected one or more comments");
  }

  @Test
  public void validateInlineComment_rejected() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    setupFailValidation(
        CommentForValidation.CommentType.INLINE_COMMENT, changeInfo.project, changeInfo._number);

    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt = newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, COMMENT_TEXT, null, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    Collection<CommentInfo> commentsBefore = testCommentHelper.getPublishedComments(changeId);
    mailProcessor.process(b.build());
    assertThat(testCommentHelper.getPublishedComments(changeId)).isEqualTo(commentsBefore);

    assertNotifyTo(user);
    Message message = sender.nextMessage();
    assertThat(message.body()).contains("rejected one or more comments");
  }

  @Test
  public void validateFileComment_rejected() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    setupFailValidation(
        CommentForValidation.CommentType.FILE_COMMENT, changeInfo.project, changeInfo._number);

    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt = newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, null, COMMENT_TEXT, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    Collection<CommentInfo> commentsBefore = testCommentHelper.getPublishedComments(changeId);
    mailProcessor.process(b.build());
    assertThat(testCommentHelper.getPublishedComments(changeId)).isEqualTo(commentsBefore);

    assertNotifyTo(user);
    Message message = sender.nextMessage();
    assertThat(message.body()).contains("rejected one or more comments");
  }

  @Test
  @GerritConfig(name = "change.maxComments", value = "4")
  public void limitNumberOfComments() throws Exception {
    String changeId = createChangeWithReview();
    CommentInput commentInput = new CommentInput();
    commentInput.line = 1;
    commentInput.message = "foo";
    commentInput.path = FILE_NAME;
    RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(FILE_NAME);
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments = ImmutableMap.of(FILE_NAME, ImmutableList.of(commentInput));
    reviewInput.robotComments = ImmutableMap.of(FILE_NAME, ImmutableList.of(robotCommentInput));
    gApi.changes().id(changeId).current().review(reviewInput);

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailProcessingUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt = newPlaintextBody(getChangeUrl(changeInfo) + "/1", null, null, COMMENT_TEXT, null);
    b.textContent(txt + textFooterForChange(changeInfo._number, ts));

    Collection<CommentInfo> commentsBefore = testCommentHelper.getPublishedComments(changeId);
    mailProcessor.process(b.build());
    assertThat(testCommentHelper.getPublishedComments(changeId)).isEqualTo(commentsBefore);

    assertNotifyTo(user);
    Message message = sender.nextMessage();
    assertThat(message.body()).contains("rejected one or more comments");
  }

  private String getChangeUrl(ChangeInfo changeInfo) {
    return canonicalWebUrl.get() + "c/" + changeInfo.project + "/+/" + changeInfo._number;
  }

  private void setupFailValidation(
      CommentForValidation.CommentType type, String failProject, int failChange) {
    CommentForValidation commentForValidation =
        CommentForValidation.create(
            CommentForValidation.CommentSource.HUMAN, type, COMMENT_TEXT, COMMENT_TEXT.length());

    when(mockCommentValidator.validateComments(
            CommentValidationContext.create(failChange, failProject),
            ImmutableList.of(commentForValidation)))
        .thenReturn(ImmutableList.of(commentForValidation.failValidation("Oh no!")));
  }
}
