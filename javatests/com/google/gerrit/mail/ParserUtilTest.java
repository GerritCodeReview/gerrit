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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import java.time.Instant;
import org.junit.Test;

public class ParserUtilTest {
  private static final String CHANGE_URL = "https://gerrit-review.googlesource.com/c/project/+/123";

  private static HumanComment newComment(String uuid, String file, int patchSetId, int line) {
    HumanComment c =
        new HumanComment(
            new Comment.Key(uuid, file, patchSetId),
            Account.id(0),
            Instant.EPOCH,
            (short) 0,
            "comment",
            "",
            false);
    c.lineNbr = line;
    return c;
  }

  @Test
  public void isCommentUrlMatchesInlineCommentPermalink() {
    HumanComment comment = newComment("270c2451_11be09b9", "gerrit-server/test.txt", 1, 382);

    // The UUID-based permalink CommentChangeEmailDecoratorImpl/UrlFormatter actually emit,
    // with and without the "?usp=email" query parameter Gerrit's mail sender appends.
    assertThat(
            ParserUtil.isCommentUrl(
                CHANGE_URL + "/comment/270c2451_11be09b9?usp=email", CHANGE_URL, comment))
        .isTrue();
    assertThat(
            ParserUtil.isCommentUrl(CHANGE_URL + "/comment/270c2451_11be09b9", CHANGE_URL, comment))
        .isTrue();

    // A different comment's permalink must not match.
    assertThat(
            ParserUtil.isCommentUrl(
                CHANGE_URL + "/comment/some_other_uuid?usp=email", CHANGE_URL, comment))
        .isFalse();
  }

  @Test
  public void isCommentUrlStillMatchesLegacyFormat() {
    HumanComment comment = newComment("uuid1", "gerrit-server/test.txt", 1, 3);

    assertThat(
            ParserUtil.isCommentUrl(
                CHANGE_URL + "/1/gerrit-server/test.txt@3", CHANGE_URL, comment))
        .isTrue();
  }

  @Test
  public void trimQuotationLineOnMessageWithoutQuoatationLine() throws Exception {
    assertThat(ParserUtil.trimQuotation("One line")).isEqualTo("One line");
    assertThat(ParserUtil.trimQuotation("Two\nlines")).isEqualTo("Two\nlines");
    assertThat(ParserUtil.trimQuotation("Thr\nee\nlines")).isEqualTo("Thr\nee\nlines");
  }

  @Test
  public void trimQuotationLineOnMixedMessages() throws Exception {
    assertThat(
            ParserUtil.trimQuotation(
                "One line\n"
                    + "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit)\n"
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("One line");
    assertThat(
            ParserUtil.trimQuotation(
                "One line\n"
                    + "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit) "
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("One line");
  }

  @Test
  public void trimQuotationLineOnMessagesContainingQuoationLine() throws Exception {
    assertThat(
            ParserUtil.trimQuotation(
                "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit)\n"
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("");
    assertThat(
            ParserUtil.trimQuotation(
                "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit) "
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("");
  }
}
