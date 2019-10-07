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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Comment;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class ParserUtil {
  private static final Pattern SIMPLE_EMAIL_PATTERN =
      Pattern.compile(
          "[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+"
              + "(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})");

  private ParserUtil() {}

  /**
   * Trims the quotation that email clients add Example: On Sun, Nov 20, 2016 at 10:33 PM,
   * <gerrit@gerritcodereview.com> wrote:
   *
   * @param comment Comment parsed from an email.
   * @return Trimmed comment.
   */
  public static String trimQuotation(String comment) {
    StringJoiner j = new StringJoiner("\n");
    List<String> lines = Splitter.on('\n').splitToList(comment);
    for (int i = 0; i < lines.size() - 2; i++) {
      j.add(lines.get(i));
    }

    // Check if the last line contains the full quotation pattern (date + email)
    String lastLine = lines.get(lines.size() - 1);
    if (containsQuotationPattern(lastLine)) {
      if (lines.size() > 1) {
        j.add(lines.get(lines.size() - 2));
      }
      return j.toString().trim();
    }

    // Check if the second last line + the last line contain the full quotation pattern. This is
    // necessary, as the quotation line can be split across the last two lines if it gets too long.
    if (lines.size() > 1) {
      String lastLines = lines.get(lines.size() - 2) + lastLine;
      if (containsQuotationPattern(lastLines)) {
        return j.toString().trim();
      }
    }

    // Add the last two lines
    if (lines.size() > 1) {
      j.add(lines.get(lines.size() - 2));
    }
    j.add(lines.get(lines.size() - 1));

    return j.toString().trim();
  }

  /** Check if string is an inline comment url on a patch set or the base */
  public static boolean isCommentUrl(String str, String changeUrl, Comment comment) {
    int lineNbr = comment.range == null ? comment.lineNbr : comment.range.startLine;
    return str.equals(filePath(changeUrl, comment) + "@" + lineNbr)
        || str.equals(filePath(changeUrl, comment) + "@a" + lineNbr);
  }

  /** Generate the fully qualified filepath */
  public static String filePath(String changeUrl, Comment comment) {
    return changeUrl + "/" + comment.key.patchSetId + "/" + comment.key.filename;
  }

  /**
   * When parsing mail content, we need to append comments prematurely since we are parsing
   * block-by-block and never know what comes next. This can result in a comment being parsed as two
   * comments when it spans multiple blocks. This method takes care of merging those blocks or
   * adding a new comment to the list of appropriate.
   */
  public static void appendOrAddNewComment(MailComment comment, List<MailComment> comments) {
    if (comments.isEmpty()) {
      comments.add(comment);
      return;
    }
    MailComment lastComment = Iterables.getLast(comments);

    if (comment.isSameCommentPath(lastComment)) {
      // Merge the two comments. Links should just be appended, while regular text that came from
      // different <div> elements should be separated by a paragraph.
      lastComment.message += (comment.isLink ? " " : "\n\n") + comment.message;
      return;
    }

    comments.add(comment);
  }

  private static boolean containsQuotationPattern(String s) {
    // Identifying the quotation line is hard, as it can be in any language.
    // We identify this line by it's characteristics: It usually contains a
    // valid email address, some digits for the date in groups of 1-4 in a row
    // as well as some characters.

    // Count occurrences of digit groups
    int numConsecutiveDigits = 0;
    int maxConsecutiveDigits = 0;
    int numDigitGroups = 0;
    for (char c : s.toCharArray()) {
      if (c >= '0' && c <= '9') {
        numConsecutiveDigits++;
      } else if (numConsecutiveDigits > 0) {
        maxConsecutiveDigits = Integer.max(maxConsecutiveDigits, numConsecutiveDigits);
        numConsecutiveDigits = 0;
        numDigitGroups++;
      }
    }
    if (numDigitGroups < 4 || maxConsecutiveDigits > 4) {
      return false;
    }

    // Check if the string contains an email address
    return SIMPLE_EMAIL_PATTERN.matcher(s).find();
  }
}
