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

import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.gerrit.reviewdb.client.Comment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Provides functionality for parsing the HTML part of a {@link MailMessage}. */
public class HtmlParser {

  private HtmlParser() {}
  /**
   * Parses comments from html email.
   *
   * <p>This parser goes though all html elements in the email and checks for matching patterns. It
   * keeps track of the last file and comments it encountered to know in which context a parsed
   * comment belongs. It uses the href attributes of <a> tags to identify comments sent out by
   * Gerrit as these are generally more reliable then the text captions.
   *
   * @param email {@link MailMessage} as received from the email service
   * @param comments A specific set of comments as sent out in the original notification email.
   *     Comments are expected to be in the same order as they were sent out to in the email.
   * @param changeUrl Canonical change URL that points to the change on this Gerrit instance
   *     Example: https://go-review.googlesource.com/#/c/91570
   * @return List of MailComments parsed from the html part of the email
   */
  public static List<MailComment> parse(
      MailMessage email, Collection<Comment> comments, String changeUrl) {
    List<MailComment> parsedComments = new ArrayList<>();
    Document d = Jsoup.parse(email.htmlContent());
    PeekingIterator<Comment> iter = Iterators.peekingIterator(comments.iterator());

    String lastEncounteredFileName = null;
    Comment lastEncounteredComment = null;
    for (Element e : d.body().getAllElements()) {
      String elementName = e.tagName();
      boolean isInBlockQuote =
          e.parents().stream().filter(p -> p.tagName().equals("blockquote")).findAny().isPresent();

      if (elementName.equals("a")) {
        String href = e.attr("href");
        // Check if there is still a next comment that could be contained in
        // this <a> tag
        if (!iter.hasNext()) {
          continue;
        }
        Comment perspectiveComment = iter.peek();
        if (href.equals(ParserUtil.filePath(changeUrl, perspectiveComment))) {
          if (lastEncounteredFileName == null
              || !lastEncounteredFileName.equals(perspectiveComment.key.filename)) {
            // Not a file-level comment, but users could have typed a comment
            // right after this file annotation to create a new file-level
            // comment. If this file has a file-level comment, we have already
            // set lastEncounteredComment to that file-level comment when we
            // encountered the file link and should not reset it now.
            lastEncounteredFileName = perspectiveComment.key.filename;
            lastEncounteredComment = null;
          } else if (perspectiveComment.lineNbr == 0) {
            // This was originally a file-level comment
            lastEncounteredComment = perspectiveComment;
            iter.next();
          }
        } else if (ParserUtil.isCommentUrl(href, changeUrl, perspectiveComment)) {
          // This is a regular inline comment
          lastEncounteredComment = perspectiveComment;
          iter.next();
        }
      } else if (!isInBlockQuote
          && elementName.equals("div")
          && !e.className().startsWith("gmail")) {
        // This is a comment typed by the user
        String content = e.ownText().trim();
        if (!Strings.isNullOrEmpty(content)) {
          if (lastEncounteredComment == null && lastEncounteredFileName == null) {
            // Remove quotation line, email signature and
            // "Sent from my xyz device"
            content = ParserUtil.trimQuotationLine(content);
            // TODO(hiesel) Add more sanitizer
            if (!Strings.isNullOrEmpty(content)) {
              parsedComments.add(
                  new MailComment(content, null, null, MailComment.CommentType.CHANGE_MESSAGE));
            }
          } else if (lastEncounteredComment == null) {
            parsedComments.add(
                new MailComment(
                    content, lastEncounteredFileName, null, MailComment.CommentType.FILE_COMMENT));
          } else {
            parsedComments.add(
                new MailComment(
                    content, null, lastEncounteredComment, MailComment.CommentType.INLINE_COMMENT));
          }
        }
      }
    }
    return parsedComments;
  }
}
