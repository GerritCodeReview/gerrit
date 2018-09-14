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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.mail.MailProcessingUtil;
import com.google.gerrit.server.mail.receive.MailProcessor;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

public class MailProcessorIT extends AbstractMailIT {
  @Inject private MailProcessor mailProcessor;
  @Inject private AccountOperations accountOperations;

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

  private String getChangeUrl(ChangeInfo changeInfo) {
    return canonicalWebUrl.get() + "c/" + changeInfo.project + "/+/" + changeInfo._number;
  }
}
