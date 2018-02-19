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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.mail.MailHeader;
import com.google.gerrit.server.mail.MailUtil;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parse metadata from inbound email */
public class MailHeaderParser {
  private static final Logger log = LoggerFactory.getLogger(MailHeaderParser.class);

  public static MailMetadata parse(MailMessage m) {
    MailMetadata metadata = new MailMetadata();
    // Find author
    metadata.author = m.from().getEmail();

    // Check email headers for X-Gerrit-<Name>
    for (String header : m.additionalHeaders()) {
      if (header.startsWith(MailHeader.CHANGE_NUMBER.fieldWithDelimiter())) {
        String num = header.substring(MailHeader.CHANGE_NUMBER.fieldWithDelimiter().length());
        metadata.changeNumber = Ints.tryParse(num);
      } else if (header.startsWith(MailHeader.PATCH_SET.fieldWithDelimiter())) {
        String ps = header.substring(MailHeader.PATCH_SET.fieldWithDelimiter().length());
        metadata.patchSet = Ints.tryParse(ps);
      } else if (header.startsWith(MailHeader.COMMENT_DATE.fieldWithDelimiter())) {
        String ts = header.substring(MailHeader.COMMENT_DATE.fieldWithDelimiter().length()).trim();
        try {
          metadata.timestamp = Timestamp.from(MailUtil.rfcDateformatter.parse(ts, Instant::from));
        } catch (DateTimeParseException e) {
          log.error("Mail: Error while parsing timestamp from header of message " + m.id(), e);
        }
      } else if (header.startsWith(MailHeader.MESSAGE_TYPE.fieldWithDelimiter())) {
        metadata.messageType =
            header.substring(MailHeader.MESSAGE_TYPE.fieldWithDelimiter().length());
      }
    }
    if (metadata.hasRequiredFields()) {
      return metadata;
    }

    // If the required fields were not yet found, continue to parse the text
    if (!Strings.isNullOrEmpty(m.textContent())) {
      Iterable<String> lines = Splitter.on('\n').split(m.textContent().replace("\r\n", "\n"));
      extractFooters(lines, metadata, m);
      if (metadata.hasRequiredFields()) {
        return metadata;
      }
    }

    // If the required fields were not yet found, continue to parse the HTML
    // HTML footer are contained inside a <div> tag
    if (!Strings.isNullOrEmpty(m.htmlContent())) {
      Iterable<String> lines = Splitter.on("</div>").split(m.htmlContent().replace("\r\n", "\n"));
      extractFooters(lines, metadata, m);
      if (metadata.hasRequiredFields()) {
        return metadata;
      }
    }

    return metadata;
  }

  private static void extractFooters(Iterable<String> lines, MailMetadata metadata, MailMessage m) {
    for (String line : lines) {
      if (metadata.changeNumber == null && line.contains(MailHeader.CHANGE_NUMBER.getName())) {
        metadata.changeNumber =
            Ints.tryParse(extractFooter(MailHeader.CHANGE_NUMBER.withDelimiter(), line));
      } else if (metadata.patchSet == null && line.contains(MailHeader.PATCH_SET.getName())) {
        metadata.patchSet =
            Ints.tryParse(extractFooter(MailHeader.PATCH_SET.withDelimiter(), line));
      } else if (metadata.timestamp == null && line.contains(MailHeader.COMMENT_DATE.getName())) {
        String ts = extractFooter(MailHeader.COMMENT_DATE.withDelimiter(), line);
        try {
          metadata.timestamp = Timestamp.from(MailUtil.rfcDateformatter.parse(ts, Instant::from));
        } catch (DateTimeParseException e) {
          log.error("Mail: Error while parsing timestamp from footer of message " + m.id(), e);
        }
      } else if (metadata.messageType == null && line.contains(MailHeader.MESSAGE_TYPE.getName())) {
        metadata.messageType = extractFooter(MailHeader.MESSAGE_TYPE.withDelimiter(), line);
      }
    }
  }

  private static String extractFooter(String key, String line) {
    return line.substring(line.indexOf(key) + key.length(), line.length()).trim();
  }
}
