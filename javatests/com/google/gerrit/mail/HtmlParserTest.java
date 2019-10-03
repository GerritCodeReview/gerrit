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

package com.google.gerrit.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Comment;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Abstract parser test for HTML messages. Payload will be added through concrete implementations.
 */
@Ignore
public abstract class HtmlParserTest extends AbstractParserTest {
  @Test
  public void simpleChangeMessage() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(newHtmlBody("Looks good to me", null, null, null, null, null, null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, "");

    assertThat(parsedComments).hasSize(1);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
  }

  @Test
  public void changeMessageWithLink() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(
        newHtmlBody(
            "Did you consider this: "
                + "<a href=\"http://gerritcodereview.com\">http://gerritcodereview.com</a>",
            null,
            null,
            null,
            null,
            null,
            null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, "");

    assertThat(parsedComments).hasSize(1);
    assertChangeMessage(
        "Did you consider this: http://gerritcodereview.com", parsedComments.get(0));
  }

  @Test
  public void simpleInlineComments() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(
        newHtmlBody(
            "Looks good to me",
            "I have a comment on this.&nbsp;",
            null,
            "Also have a comment here.",
            null,
            null,
            null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertInlineComment("I have a comment on this.", parsedComments.get(1), comments.get(1));
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(4));
  }

  @Test
  public void simpleInlineCommentsWithLink() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(
        newHtmlBody(
            "Looks good to me",
            "How about [1]? This would help IMHO.</div><div>[1] "
                + "<a href=\"http://gerritcodereview.com\">http://gerritcodereview.com</a>",
            null,
            "Also have a comment here.",
            null,
            null,
            null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertInlineComment(
        "How about [1]? This would help IMHO.\n\n[1] http://gerritcodereview.com",
        parsedComments.get(1),
        comments.get(1));
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(4));
  }

  @Test
  public void simpleFileComment() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(
        newHtmlBody(
            "Looks good to me",
            null,
            null,
            "Also have a comment here.",
            "This is a nice file",
            null,
            null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage("Looks good to me", parsedComments.get(0));
    assertFileComment("This is a nice file", parsedComments.get(1), comments.get(1).key.filename);
    assertInlineComment("Also have a comment here.", parsedComments.get(2), comments.get(4));
  }

  @Test
  public void noComments() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(newHtmlBody(null, null, null, null, null, null, null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).isEmpty();
  }

  @Test
  public void noChangeMessage() {
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(
        newHtmlBody(
            null, null, null, "Also have a comment here.", "This is a nice file", null, null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).hasSize(2);
    assertFileComment("This is a nice file", parsedComments.get(0), comments.get(1).key.filename);
    assertInlineComment("Also have a comment here.", parsedComments.get(1), comments.get(4));
  }

  @Test
  public void commentsSpanningMultipleBlocks() {
    String htmlMessage =
        "This is a very long test comment. <div><br></div><div>Now this is a new paragraph yay.</div>";
    String txtMessage = "This is a very long test comment.\n\nNow this is a new paragraph yay.";
    MailMessage.Builder b = newMailMessageBuilder();
    b.htmlContent(newHtmlBody(htmlMessage, null, null, htmlMessage, htmlMessage, null, null));

    List<Comment> comments = defaultComments();
    List<MailComment> parsedComments = HtmlParser.parse(b.build(), comments, CHANGE_URL);

    assertThat(parsedComments).hasSize(3);
    assertChangeMessage(txtMessage, parsedComments.get(0));
    assertFileComment(txtMessage, parsedComments.get(1), comments.get(1).key.filename);
    assertInlineComment(txtMessage, parsedComments.get(2), comments.get(4));
  }

  /**
   * Create an html message body with the specified comments.
   *
   * @param changeMessage
   * @param c1 Comment in reply to first comment.
   * @param c2 Comment in reply to second comment.
   * @param c3 Comment in reply to third comment.
   * @param f1 Comment on file one.
   * @param f2 Comment on file two.
   * @param fc1 Comment in reply to a comment on file 1.
   * @return A string with all inline comments and the original quoted email.
   */
  protected abstract String newHtmlBody(
      String changeMessage, String c1, String c2, String c3, String f1, String f2, String fc1);
}
