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

import com.google.gerrit.mail.data.AttachmentMessage;
import com.google.gerrit.mail.data.Base64HeaderMessage;
import com.google.gerrit.mail.data.HtmlMimeMessage;
import com.google.gerrit.mail.data.NonUTF8Message;
import com.google.gerrit.mail.data.QuotedPrintableHeaderMessage;
import com.google.gerrit.mail.data.RawMailMessage;
import com.google.gerrit.mail.data.SimpleTextMessage;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class RawMailParserTest extends GerritBaseTests {
  @Test
  public void parseEmail() throws Exception {
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
        assertMail(parsedMailMessage, rawMailMessage.expectedMailMessage());
      }
      if (rawMailMessage.raw() != null) {
        // Assert String to Mail Parser
        MailMessage parsedMailMessage = RawMailParser.parse(rawMailMessage.raw());
        assertMail(parsedMailMessage, rawMailMessage.expectedMailMessage());
      }
    }
  }

  /**
   * This method makes it easier to debug failing tests by checking each property individual instead
   * of calling equals as it will immediately reveal the property that diverges between the two
   * objects.
   *
   * @param have MailMessage retrieved from the parser
   * @param want MailMessage that would be expected
   */
  private void assertMail(MailMessage have, MailMessage want) {
    assertThat(have.id()).isEqualTo(want.id());
    assertThat(have.to()).isEqualTo(want.to());
    assertThat(have.from()).isEqualTo(want.from());
    assertThat(have.cc()).isEqualTo(want.cc());
    assertThat(have.dateReceived()).isEqualTo(want.dateReceived());
    assertThat(have.additionalHeaders()).isEqualTo(want.additionalHeaders());
    assertThat(have.subject()).isEqualTo(want.subject());
    assertThat(have.textContent()).isEqualTo(want.textContent());
    assertThat(have.htmlContent()).isEqualTo(want.htmlContent());
  }
}
