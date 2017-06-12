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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.mail.Address;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.junit.Ignore;

@Ignore
public class AbstractParserTest {
  protected static final String changeURL = "https://gerrit-review.googlesource.com/#/changes/123";

  protected static void assertChangeMessage(String message, MailComment comment) {
    assertThat(comment.fileName).isNull();
    assertThat(comment.message).isEqualTo(message);
    assertThat(comment.inReplyTo).isNull();
    assertThat(comment.type).isEqualTo(MailComment.CommentType.CHANGE_MESSAGE);
  }

  protected static void assertInlineComment(
      String message, MailComment comment, Comment inReplyTo) {
    assertThat(comment.fileName).isNull();
    assertThat(comment.message).isEqualTo(message);
    assertThat(comment.inReplyTo).isEqualTo(inReplyTo);
    assertThat(comment.type).isEqualTo(MailComment.CommentType.INLINE_COMMENT);
  }

  protected static void assertFileComment(String message, MailComment comment, String file) {
    assertThat(comment.fileName).isEqualTo(file);
    assertThat(comment.message).isEqualTo(message);
    assertThat(comment.inReplyTo).isNull();
    assertThat(comment.type).isEqualTo(MailComment.CommentType.FILE_COMMENT);
  }

  protected static Comment newComment(String uuid, String file, String message, int line) {
    Comment c =
        new Comment(
            new Comment.Key(uuid, file, 1),
            new Account.Id(0),
            new Timestamp(0l),
            (short) 0,
            message,
            "");
    c.lineNbr = line;
    return c;
  }

  /** Returns a MailMessage.Builder with all required fields populated. */
  protected static MailMessage.Builder newMailMessageBuilder() {
    MailMessage.Builder b = MailMessage.builder();
    b.id("id");
    b.from(new Address("Foo Bar", "foo@bar.com"));
    b.dateReceived(new DateTime());
    b.subject("");
    return b;
  }

  /** Returns a List of default comments for testing. */
  protected static List<Comment> defaultComments() {
    List<Comment> comments = new ArrayList<>();
    comments.add(newComment("c1", "gerrit-server/test.txt", "comment", 0));
    comments.add(newComment("c2", "gerrit-server/test.txt", "comment", 2));
    comments.add(newComment("c3", "gerrit-server/test.txt", "comment", 3));
    comments.add(newComment("c4", "gerrit-server/readme.txt", "comment", 3));
    return comments;
  }
}
