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

import org.joda.time.DateTime;

/**
 * RawMailParser parses raw email content received through POP3 or IMAP into
 * an internal {@link MailMessage}.
 *
 * @see <a href="https://apache.googlesource.com/commons-email/+/EMAIL_1_4/src/main/java/org/apache/commons/mail/util/MimeMessageParser.java#175">
 *   Apache Mail Parser for a reference implementation.</a>
 */
public class RawMailParser {
  private enum MessagePart {
    HEADER, BODY
  }

  public static MailMessage parse(String raw) throws MailParsingException {
    // TODO(hiesel) Provide comprehensive and fail-safe header parser.
    MailMessage.Builder b = MailMessage.builder();
    MessagePart messagePart = MessagePart.HEADER;
    String[] lines = raw.split("\r\n");
    for (String line : lines) {
      if (messagePart == MessagePart.HEADER && line.equals("")) {
        messagePart = MessagePart.BODY;
      }
      if (messagePart == MessagePart.HEADER) {
        String key = line.split(": ")[0];
        String value = line.split(": ")[1];
        if (key.equals("Message-ID")) {
          b.id(value);
        }
      }
    }
    // TODO(hiesel) Provide complete implementation.
    b.textContent("")
        .htmlContent("")
        .subject("")
        .dateReceived(new DateTime())
        .from("");
    return b.build();
  }

  public static MailMessage parse(int[] chars) throws MailParsingException {
    // TODO(hiesel) Parse header first and check encoding. Handle body encoding properly
    StringBuilder b = new StringBuilder(chars.length);
    for (int c : chars) {
      b.append((char) c);
    }
    return parse(b.toString());
  }
}
