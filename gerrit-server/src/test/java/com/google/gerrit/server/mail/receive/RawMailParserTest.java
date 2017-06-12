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

import com.google.gerrit.server.mail.receive.data.AttachmentMessage;
import com.google.gerrit.server.mail.receive.data.Base64HeaderMessage;
import com.google.gerrit.server.mail.receive.data.HtmlMimeMessage;
import com.google.gerrit.server.mail.receive.data.NonUTF8Message;
import com.google.gerrit.server.mail.receive.data.QuotedPrintableHeaderMessage;
import com.google.gerrit.server.mail.receive.data.RawMailMessage;
import com.google.gerrit.server.mail.receive.data.SimpleTextMessage;
import com.google.gerrit.testutil.GerritBaseTests;
import org.junit.Test;

public class RawMailParserTest extends GerritBaseTests {
  @Test
  public void testParseEmail() throws Exception {
    RawMailMessage[] messages =
        new RawMailMessage[] {
          new SimpleTextMessage(),
          new Base64HeaderMessage(),
          new QuotedPrintableHeaderMessage(),
          new HtmlMimeMessage(),
          new AttachmentMessage(),
          new NonUTF8Message(),
        };
    for (RawMailMessage rawMailMessage : messages) {
      if (rawMailMessage.rawChars() != null) {
        // Assert Character to Mail Parser
        MailMessage parsedMailMessage = RawMailParser.parse(rawMailMessage.rawChars());
        assertThat(parsedMailMessage).isEqualTo(rawMailMessage.expectedMailMessage());
      }
      if (rawMailMessage.raw() != null) {
        // Assert String to Mail Parser
        MailMessage parsedMailMessage = RawMailParser.parse(rawMailMessage.raw());
        assertThat(parsedMailMessage).isEqualTo(rawMailMessage.expectedMailMessage());
      }
    }
  }
}
