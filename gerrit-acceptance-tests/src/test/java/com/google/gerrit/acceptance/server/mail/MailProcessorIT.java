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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.MailUtil;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.gerrit.server.mail.receive.MailProcessor;
import com.google.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Test;

public class MailProcessorIT extends AbstractDaemonTest {
  @Inject private MailProcessor mailProcessor;

  @Test
  public void parseAndPersistChangeMessage() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            "Test Message",
            null,
            null,
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

    mailProcessor.process(b.build());

    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\nTest Message");
    assertThat(Iterables.getLast(messages).tag).isEqualTo("mailMessageId=some id");
  }

  @Test
  public void parseAndPersistInlineComment() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            null,
            "Some Inline Comment",
            null,
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

    mailProcessor.process(b.build());

    // Assert messages
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n(1 comment)");
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
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            null,
            null,
            "Some Comment on File 1",
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

    mailProcessor.process(b.build());

    // Assert messages
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeId).get().messages;
    assertThat(messages).hasSize(3);
    assertThat(Iterables.getLast(messages).message).isEqualTo("Patch Set 1:\n(1 comment)");
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
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            null,
            "Some Inline Comment",
            null,
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

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
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));
    assertThat(comments).hasSize(2);

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            null,
            "Some Inline Comment",
            null,
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

    // Set account state to inactive
    gApi.accounts().id("user").setActive(false);

    mailProcessor.process(b.build());
    comments = gApi.changes().id(changeId).current().commentsAsList();

    // Check that comment size has not changed
    assertThat(comments).hasSize(2);

    // Reset
    gApi.accounts().id("user").setActive(true);
  }

  private static CommentInput newComment(String path, Side side, int line, String message) {
    CommentInput c = new CommentInput();
    c.path = path;
    c.side = side;
    c.line = line != 0 ? line : null;
    c.message = message;
    if (line != 0) {
      Comment.Range range = new Comment.Range();
      range.startLine = line;
      range.startCharacter = 1;
      range.endLine = line;
      range.endCharacter = 5;
      c.range = range;
    }
    return c;
  }

  /**
   * Creates a plaintext message body with the specified comments.
   *
   * @param changeMessage
   * @param c1 Comment in reply to first inline comment.
   * @param f1 Comment on file one.
   * @param fc1 Comment in reply to a comment of file 1.
   * @return A string with all inline comments and the original quoted email.
   */
  private static String newPlaintextBody(
      String changeURL, String changeMessage, String c1, String f1, String fc1) {
    return (changeMessage == null ? "" : changeMessage + "\n")
        + "> Foo Bar has posted comments on this change. (  \n"
        + "> "
        + changeURL
        + " )\n"
        + "> \n"
        + "> Change subject: Test change\n"
        + "> ...............................................................\n"
        + "> \n"
        + "> \n"
        + "> Patch Set 1: Code-Review+1\n"
        + "> \n"
        + "> (3 comments)\n"
        + "> \n"
        + "> "
        + changeURL
        + "/gerrit-server/test.txt\n"
        + "> File  \n"
        + "> gerrit-server/test.txt:\n"
        + (f1 == null ? "" : f1 + "\n")
        + "> \n"
        + "> Patch Set #4:\n"
        + "> "
        + changeURL
        + "/gerrit-server/test.txt\n"
        + "> \n"
        + "> Some comment"
        + "> \n"
        + (fc1 == null ? "" : fc1 + "\n")
        + "> "
        + changeURL
        + "/gerrit-server/test.txt@2\n"
        + "> PS1, Line 2: throw new Exception(\"Object has unsupported: \" +\n"
        + ">               :             entry.getValue() +\n"
        + ">               :             \" must be java.util.Date\");\n"
        + "> Should entry.getKey() be included in this message?\n"
        + "> \n"
        + (c1 == null ? "" : c1 + "\n")
        + "> \n";
  }

  private static String textFooterForChange(String changeId, String timestamp) {
    return "Gerrit-Change-Id: "
        + changeId
        + "\n"
        + "Gerrit-PatchSet: 1\n"
        + "Gerrit-MessageType: comment\n"
        + "Gerrit-Comment-Date: "
        + timestamp
        + "\n";
  }

  private MailMessage.Builder messageBuilderWithDefaultFields() {
    MailMessage.Builder b = MailMessage.builder();
    b.id("some id");
    Address address = new Address(user.fullName, user.email);
    b.from(address);
    b.addTo(address);
    b.subject("");
    b.dateReceived(new DateTime());
    return b;
  }

  private String createChangeWithReview() throws Exception {
    // Create change
    String file = "gerrit-server/test.txt";
    String contents = "contents \nlorem \nipsum \nlorem";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "first subject", file, contents);
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();

    // Review it
    ReviewInput input = new ReviewInput();
    input.message = "I have two comments";
    input.comments = new HashMap<>();
    CommentInput c1 = newComment(file, Side.REVISION, 0, "comment on file");
    CommentInput c2 = newComment(file, Side.REVISION, 2, "inline comment");
    input.comments.put(c1.path, ImmutableList.of(c1, c2));
    revision(r).review(input);
    return changeId;
  }
}
