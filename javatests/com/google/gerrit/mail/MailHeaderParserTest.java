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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.Test;

public class MailHeaderParserTest {
  @Test
  public void parseMetadataFromHeader() {
    // This tests if the metadata parser is able to parse metadata from the
    // email headers of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(Instant.now());
    b.subject("");

    b.addAdditionalHeader(MailHeader.CHANGE_NUMBER.fieldWithDelimiter() + "123");
    b.addAdditionalHeader(MailHeader.PATCH_SET.fieldWithDelimiter() + "1");
    b.addAdditionalHeader(MailHeader.MESSAGE_TYPE.fieldWithDelimiter() + "comment");
    b.addAdditionalHeader(
        MailHeader.COMMENT_DATE.fieldWithDelimiter() + "Tue, 25 Oct 2016 02:11:35 -0700");

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MailHeaderParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeNumber).isEqualTo(123);
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.toInstant())
        .isEqualTo(
            LocalDateTime.of(2016, Month.OCTOBER, 25, 9, 11, 35)
                .atOffset(ZoneOffset.UTC)
                .toInstant());
  }

  @Test
  public void parseMetadataFromText() {
    // This tests if the metadata parser is able to parse metadata from the
    // the text body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(Instant.now());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(MailHeader.CHANGE_NUMBER.withDelimiter()).append("123\r\n");
    stringBuilder.append("> ").append(MailHeader.PATCH_SET.withDelimiter()).append("1\n");
    stringBuilder.append(MailHeader.MESSAGE_TYPE.withDelimiter()).append("comment\n");
    stringBuilder
        .append(MailHeader.COMMENT_DATE.withDelimiter())
        .append("Tue, 25 Oct 2016 02:11:35 -0700\r\n");
    b.textContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MailHeaderParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeNumber).isEqualTo(123);
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.toInstant())
        .isEqualTo(
            LocalDateTime.of(2016, Month.OCTOBER, 25, 9, 11, 35)
                .atOffset(ZoneOffset.UTC)
                .toInstant());
  }

  @Test
  public void parseMetadataFromHTML() {
    // This tests if the metadata parser is able to parse metadata from the
    // the HTML body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(Instant.now());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder
        .append("<div id\"someid\">")
        .append(MailHeader.CHANGE_NUMBER.withDelimiter())
        .append("123</div>");
    stringBuilder.append("<div>").append(MailHeader.PATCH_SET.withDelimiter()).append("1</div>");
    stringBuilder
        .append("<div>")
        .append(MailHeader.MESSAGE_TYPE.withDelimiter())
        .append("comment</div>");
    stringBuilder
        .append("<div>")
        .append(MailHeader.COMMENT_DATE.withDelimiter())
        .append("Tue, 25 Oct 2016 02:11:35 -0700")
        .append("</div>");
    b.htmlContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MailHeaderParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeNumber).isEqualTo(123);
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.toInstant())
        .isEqualTo(
            LocalDateTime.of(2016, Month.OCTOBER, 25, 9, 11, 35)
                .atOffset(ZoneOffset.UTC)
                .toInstant());
  }
}
