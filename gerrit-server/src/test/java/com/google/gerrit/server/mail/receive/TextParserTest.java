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

package com.google.gerrit.server.mail.receive;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Comment;
import java.util.List;
import org.junit.Test;

/** Tests all email text parsing capabilities. */
public class TextParserTest extends AbstractParserTest {
  private static final String quotedFooter =
      ""
          + "> To view, visit https://gerrit-review.googlesource.com/123\n"
          + "> To unsubscribe, visit https://gerrit-review.googlesource.com\n"
          + "> \n"
          + "> Gerrit-MessageType: comment\n"
          + "> Gerrit-Change-Id: Ie1234021bf1e8d1425641af58fd648fc011db153\n"
          + "> Gerrit-PatchSet: 1\n"
          + "> Gerrit-Project: gerrit\n"
          + "> Gerrit-Branch: master\n"
          + "> Gerrit-Owner: Foo Bar <foo@bar.com>\n"
          + "> Gerrit-HasComments: Yes";

  @Test
  public void simpleChangeMessage() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent("Looks good to me\n" + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(1);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
  }

  @Test
  public void simpleInlineComments() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(
        newPlaintextBody(
                "Looks good to me",
                "I have a comment on this.",
                null,
                "Also have a comment here.",
                null,
                null,
                null)
            + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertInlineComment("I have a comment on this.", parsedComments.get(1), comments.get(1));
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(3));
  }

  @Test
  public void simpleFileComment() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(
        newPlaintextBody(
                "Looks good to me",
                null,
                null,
                "Also have a comment here.",
                "This is a nice file",
                null,
                null)
            + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertFileComment("This is a nice file", parsedComments.get(1), comments.get(1).key.filename);
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(3));
  }

  @Test
  public void noComments() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(newPlaintextBody(null, null, null, null, null, null, null) + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).isEmpty();
  }

  @Test
  public void noChangeMessage() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(
        newPlaintextBody(
                null, null, null, "Also have a comment here.", "This is a nice file", null, null)
            + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(2);
    assertFileComment("This is a nice file", parsedComments.get(0), comments.get(1).key.filename);
    assertInlineComment("Also have a comment here.", parsedComments.get(1), comments.get(3));
  }

  @Test
  public void allCommentsGmail() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(
        (newPlaintextBody(
                    "Looks good to me",
                    null,
                    null,
                    "Also have a comment here.",
                    "This is a nice file",
                    null,
                    null)
                + quotedFooter)
            .replace("> ", ">> "));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertFileComment("This is a nice file", parsedComments.get(1), comments.get(1).key.filename);
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(3));
  }

  @Test
  public void replyToFileComment() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.textContent(
        newPlaintextBody(
                "Looks good to me",
                null,
                null,
                null,
                null,
                null,
                "Comment in reply to file comment")
            + quotedFooter);

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = TextParser.parse(b.build(), comments, changeURL);

    assertThat(parsedComments).hasSize(2);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertInlineComment("Comment in reply to file comment", parsedComments.get(1), comments.get(0));
  }

  /**
   * Creates a plaintext message body with the specified comments.
   *
   * @param changeMessage {@code String} general message on the change.
   * @param c1 {@code Comment} in reply to first inline comment.
   * @param c2 {@code Comment} in reply to second inline comment.
   * @param c3 {@code Comment} in reply to third inline comment.
   * @param f1 {@code Comment} on file one.
   * @param f2 {@code Comment} on file two.
   * @param fc1 {@code Comment} in reply to a comment of file 1.
   * @return A {@code String} with all inline comments and the original quoted email.
   */
  private static String newPlaintextBody(
      String changeMessage, String c1, String c2, String c3, String f1, String f2, String fc1) {
    return (changeMessage == null ? "" : changeMessage + "\n")
        + "> Foo Bar has posted comments on this change. (  \n"
        + "> "
        + changeURL
        + "/1 )\n"
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
        + "/1/gerrit-server/test.txt\n"
        + "> File  \n"
        + "> gerrit-server/test.txt:\n"
        + (f1 == null ? "" : f1 + "\n")
        + "> \n"
        + "> Patch Set #4:\n"
        + "> "
        + changeURL
        + "/1/gerrit-server/test.txt\n"
        + "> \n"
        + "> Some comment"
        + "> \n"
        + (fc1 == null ? "" : fc1 + "\n")
        + "> "
        + changeURL
        + "/1/gerrit-server/test.txt@2\n"
        + "> PS1, Line 2: throw new Exception(\"Object has unsupported: \" +\n"
        + ">               :             entry.getValue() +\n"
        + ">               :             \" must be java.util.Date\");\n"
        + "> Should entry.getKey() be included in this message?\n"
        + "> \n"
        + (c1 == null ? "" : c1 + "\n")
        + "> \n"
        + "> "
        + changeURL
        + "/1/gerrit-server/test.txt@3\n"
        + "> PS1, Line 3: throw new Exception(\"Object has: \" +\n"
        + ">               :             entry.getValue().getClass() +\n"
        + ">              :             \" must be java.util.Date\");\n"
        + "> same here\n"
        + "> \n"
        + (c2 == null ? "" : c2 + "\n")
        + "> \n"
        + "> "
        + changeURL
        + "/1/gerrit-server/readme.txt\n"
        + "> File  \n"
        + "> gerrit-server/readme.txt:\n"
        + (f2 == null ? "" : f2 + "\n")
        + "> \n"
        + "> "
        + changeURL
        + "/1/gerrit-server/readme.txt@3\n"
        + "> PS1, Line 3: E\n"
        + "> Should this be EEE like in other places?\n"
        + (c3 == null ? "" : c3 + "\n");
  }
}
