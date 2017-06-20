// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import static java.nio.charset.StandardCharsets.UTF_8;

public class QuotedPrintableEncoding {

  private static final int MAX_LINE_LENGTH = 76;

  /**
   * Encodes header values with non-ASCII characters according to the RFC 2047
   * MIME "Encoded Word" format.
   */
  public static String encodeHeader(String value) {
    final StringBuilder r = new StringBuilder();

    r.append("=?UTF-8?Q?");
    for (int i = 0; i < value.length(); i++) {
      final int cp = value.codePointAt(i);
      if (cp == ' ') {
        r.append('_');

      } else if (needsEncodingWithinPhrase(cp)) {
        r.append(codePointToQuotedPrintable(cp));

      } else {
        r.append(Character.toChars(cp));
      }
    }
    r.append("?=");

    return r.toString();
  }

  /**
   * Encodes text content according to RFC 2045 § 6.7 "Quoted Printable" format.
   */
  public static String encode(String value) {
    StringBuilder sb = new StringBuilder();
    int lineLength = 0;
    for (int i = 0; i < value.length(); i++) {
      final int cp = value.codePointAt(i);
      final boolean needsEncoding = cp == '=' || cp < 32 || cp > 126;

      // If the end of the line has been reached OR if there is not enough room
      // to insert the =XX code before the end of the line, insert a soft-break.
      if (lineLength == MAX_LINE_LENGTH - 1
          || needsEncoding && lineLength > MAX_LINE_LENGTH - 4) {
        sb.append("=\n");
        lineLength = 0;
      }

      if (needsEncoding) {
        sb.append(codePointToQuotedPrintable(cp));
        lineLength += 3;
      } else {
        sb.append(Character.toChars(cp));
        lineLength++;
      }
    }
    return sb.toString();
  }

  private static String codePointToQuotedPrintable(int cp) {
    StringBuilder r = new StringBuilder();
    byte[] buf = new String(Character.toChars(cp)).getBytes(UTF_8);
    for (byte b : buf) {
      r.append('=');
      r.append(Integer.toHexString((b >>> 4) & 0x0f).toUpperCase());
      r.append(Integer.toHexString(b & 0x0f).toUpperCase());
    }
    return r.toString();
  }

  public static boolean needsEncoding(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) < ' ' || '~' < value.charAt(i)) {
        return true;
      }
    }
    return false;
  }

  private static boolean needsEncodingWithinPhrase(int cp) {
    switch (cp) {
      case '!':
      case '*':
      case '+':
      case '-':
      case '/':
      case '=':
      case '_':
        return false;
      default:
        if (('a' <= cp && cp <= 'z') || ('A' <= cp && cp <= 'Z') || ('0' <= cp && cp <= '9')) {
          return false;
        }
        return true;
    }
  }
}
