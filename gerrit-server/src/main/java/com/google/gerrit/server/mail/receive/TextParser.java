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

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.gerrit.reviewdb.client.Comment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Provides parsing functionality for plaintext email. */
public class TextParser {

  private TextParser() {}
  /**
   * Parses comments from plaintext email.
   *
   * @param email {@link MailMessage} as received from the email service
   * @param comments list of {@link Comment}s previously persisted on the change that caused the
   *     original notification email to be sent out. Ordering must be the same as in the outbound
   *     email
   * @param changeUrl Canonical change url that points to the change on this Gerrit instance.
   *     Example: https://go-review.googlesource.com/#/c/91570
   * @return list of MailComments parsed from the plaintext part of the email
   */
  public static List<MailComment> parse(
      MailMessage email, Collection<Comment> comments, String changeUrl) {
    String body = email.textContent();
    // Replace CR-LF by \n
    body = body.replace("\r\n", "\n");

    List<MailComment> parsedComments = new ArrayList<>();

    // Some email clients (like GMail) use >> for enquoting text when there are
    // inline comments that the users typed. These will then be enquoted by a
    // single >. We sanitize this by unifying it into >. Inline comments typed
    // by the user will not be enquoted.
    //
    // Example:
    // Some comment
    // >> Quoted Text
    // >> Quoted Text
    // > A comment typed in the email directly
    String singleQuotePattern = "\n> ";
    String doubleQuotePattern = "\n>> ";
    if (countOccurrences(body, doubleQuotePattern) > countOccurrences(body, singleQuotePattern)) {
      body = body.replace(doubleQuotePattern, singleQuotePattern);
    }

    PeekingIterator<Comment> iter = Iterators.peekingIterator(comments.iterator());

    String[] lines = body.split("\n");
    MailComment currentComment = null;
    String lastEncounteredFileName = null;
    Comment lastEncounteredComment = null;
    for (String line : lines) {
      if (line.startsWith("> ")) {
        line = line.substring("> ".length()).trim();
        // This is not a comment, try to advance the file/comment pointers and
        // add previous comment to list if applicable
        if (currentComment != null) {
          parsedComments.add(currentComment);
          currentComment = null;
        }

        if (!iter.hasNext()) {
          continue;
        }
        Comment perspectiveComment = iter.peek();
        if (line.equals(ParserUtil.filePath(changeUrl, perspectiveComment))) {
          if (lastEncounteredFileName == null
              || !lastEncounteredFileName.equals(perspectiveComment.key.filename)) {
            // This is the annotation of a file
            lastEncounteredFileName = perspectiveComment.key.filename;
            lastEncounteredComment = null;
          } else if (perspectiveComment.lineNbr == 0) {
            // This was originally a file-level comment
            lastEncounteredComment = perspectiveComment;
            iter.next();
          }
        } else if (ParserUtil.isCommentUrl(line, changeUrl, perspectiveComment)) {
          lastEncounteredComment = perspectiveComment;
          iter.next();
        }
      } else {
        // This is a comment. Try to append to previous comment if applicable or
        // create a new comment.
        if (currentComment == null) {
          // Start new comment
          currentComment = new MailComment();
          currentComment.message = line;
          if (lastEncounteredComment == null) {
            if (lastEncounteredFileName == null) {
              // Change message
              currentComment.type = MailComment.CommentType.CHANGE_MESSAGE;
            } else {
              // File comment not sent in reply to another comment
              currentComment.type = MailComment.CommentType.FILE_COMMENT;
              currentComment.fileName = lastEncounteredFileName;
            }
          } else {
            // Comment sent in reply to another comment
            currentComment.inReplyTo = lastEncounteredComment;
            currentComment.type = MailComment.CommentType.INLINE_COMMENT;
          }
        } else {
          // Attach to previous comment
          currentComment.message += "\n" + line;
        }
      }
    }
    // There is no need to attach the currentComment after this loop as all
    // emails have footers and other enquoted text after the last comment
    // appeared and the last comment will have already been added to the list
    // at this point.

    return parsedComments;
  }

  /** Counts the occurrences of pattern in s */
  private static int countOccurrences(String s, String pattern) {
    return (s.length() - s.replace(pattern, "").length()) / pattern.length();
  }
}
