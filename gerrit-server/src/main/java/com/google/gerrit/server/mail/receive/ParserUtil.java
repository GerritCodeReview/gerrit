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

import com.google.gerrit.reviewdb.client.Comment;
import java.util.regex.Pattern;

/** Static utils for parsing {@link MailComment}s from a {@link MailMessage} */
public class ParserUtil {
  private static final Pattern SIMPLE_EMAIL_PATTERN =
      Pattern.compile(
          "[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+"
              + "(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})");

  private ParserUtil() {}

  /**
   * Trims the quotation line that email clients add Example: On Sun, Nov 20, 2016 at 10:33 PM,
   * <gerrit@hiesel.it> wrote:
   *
   * @param comment Comment parsed from an email
   * @return Trimmed comment.
   */
  public static String trimQuotationLine(String comment) {
    // Identifying the quotation line is hard, as it can be in any language.
    // We identify this line by it's characteristics: It usually contains a
    // valid email address, some digits for the date in groups of 1-4 in a row
    // as well as some characters.
    StringBuilder b = new StringBuilder();
    for (String line : comment.split("\n")) {
      // Count occurrences of digit groups
      int numConsecutiveDigits = 0;
      int maxConsecutiveDigits = 0;
      int numDigitGroups = 0;
      for (char c : line.toCharArray()) {
        if (c >= '0' && c <= '9') {
          numConsecutiveDigits++;
        } else if (numConsecutiveDigits > 0) {
          maxConsecutiveDigits = Integer.max(maxConsecutiveDigits, numConsecutiveDigits);
          numConsecutiveDigits = 0;
          numDigitGroups++;
        }
      }
      if (numDigitGroups < 4
          || maxConsecutiveDigits > 4
          || !SIMPLE_EMAIL_PATTERN.matcher(line).find()) {
        b.append(line);
      }
    }
    return b.toString().trim();
  }

  /** Check if string is an inline comment url on a patch set or the base */
  public static boolean isCommentUrl(String str, String changeUrl, Comment comment) {
    return str.equals(filePath(changeUrl, comment) + "@" + comment.lineNbr)
        || str.equals(filePath(changeUrl, comment) + "@a" + comment.lineNbr);
  }

  /** Generate the fully qualified filepath */
  public static String filePath(String changeUrl, Comment comment) {
    return changeUrl + "/" + comment.key.patchSetId + "/" + comment.key.filename;
  }
}
