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

import static com.google.gerrit.server.mail.MetadataName.toFooterWithDelimiter;
import static com.google.gerrit.server.mail.MetadataName.toHeaderWithDelimiter;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.mail.MailUtil;
import com.google.gerrit.server.mail.MetadataName;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parse metadata from inbound email */
public class MetadataParser {
  private static final Logger log = LoggerFactory.getLogger(MailProcessor.class);

  public static MailMetadata parse(MailMessage m) {
    MailMetadata metadata = new MailMetadata();
    // Find author
    metadata.author = m.from().getEmail();

    // Check email headers for X-Gerrit-<Name>
    for (String header : m.additionalHeaders()) {
      if (header.startsWith(toHeaderWithDelimiter(MetadataName.CHANGE_ID))) {
        metadata.changeId =
            header.substring(toHeaderWithDelimiter(MetadataName.CHANGE_ID).length());
      } else if (header.startsWith(toHeaderWithDelimiter(MetadataName.PATCH_SET))) {
        String ps = header.substring(toHeaderWithDelimiter(MetadataName.PATCH_SET).length());
        metadata.patchSet = Ints.tryParse(ps);
      } else if (header.startsWith(toHeaderWithDelimiter(MetadataName.TIMESTAMP))) {
        String ts = header.substring(toHeaderWithDelimiter(MetadataName.TIMESTAMP).length()).trim();
        try {
          metadata.timestamp = Timestamp.from(MailUtil.rfcDateformatter.parse(ts, Instant::from));
        } catch (DateTimeParseException e) {
          log.error("Mail: Error while parsing timestamp from header of message " + m.id(), e);
        }
      } else if (header.startsWith(toHeaderWithDelimiter(MetadataName.MESSAGE_TYPE))) {
        metadata.messageType =
            header.substring(toHeaderWithDelimiter(MetadataName.MESSAGE_TYPE).length());
      }
    }
    if (metadata.hasRequiredFields()) {
      return metadata;
    }

    // If the required fields were not yet found, continue to parse the text
    if (!Strings.isNullOrEmpty(m.textContent())) {
      String[] lines = m.textContent().replace("\r\n", "\n").split("\n");
      extractFooters(lines, metadata, m);
      if (metadata.hasRequiredFields()) {
        return metadata;
      }
    }

    // If the required fields were not yet found, continue to parse the HTML
    // HTML footer are contained inside a <div> tag
    if (!Strings.isNullOrEmpty(m.htmlContent())) {
      String[] lines = m.htmlContent().replace("\r\n", "\n").split("</div>");
      extractFooters(lines, metadata, m);
      if (metadata.hasRequiredFields()) {
        return metadata;
      }
    }

    return metadata;
  }

  private static void extractFooters(String[] lines, MailMetadata metadata, MailMessage m) {
    for (String line : lines) {
      if (metadata.changeId == null && line.contains(MetadataName.CHANGE_ID)) {
        metadata.changeId = extractFooter(toFooterWithDelimiter(MetadataName.CHANGE_ID), line);
      } else if (metadata.patchSet == null && line.contains(MetadataName.PATCH_SET)) {
        metadata.patchSet =
            Ints.tryParse(extractFooter(toFooterWithDelimiter(MetadataName.PATCH_SET), line));
      } else if (metadata.timestamp == null && line.contains(MetadataName.TIMESTAMP)) {
        String ts = extractFooter(toFooterWithDelimiter(MetadataName.TIMESTAMP), line);
        try {
          metadata.timestamp = Timestamp.from(MailUtil.rfcDateformatter.parse(ts, Instant::from));
        } catch (DateTimeParseException e) {
          log.error("Mail: Error while parsing timestamp from footer of message " + m.id(), e);
        }
      } else if (metadata.messageType == null && line.contains(MetadataName.MESSAGE_TYPE)) {
        metadata.messageType =
            extractFooter(toFooterWithDelimiter(MetadataName.MESSAGE_TYPE), line);
      }
    }
  }

  private static String extractFooter(String key, String line) {
    return line.substring(line.indexOf(key) + key.length(), line.length()).trim();
  }
}
