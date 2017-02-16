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
import static com.google.gerrit.server.mail.MetadataName.toFooterWithDelimiter;
import static com.google.gerrit.server.mail.MetadataName.toHeaderWithDelimiter;

import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.MetadataName;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class MetadataParserTest {
  @Test
  public void parseMetadataFromHeader() {
    // This tests if the metadata parser is able to parse metadata from the
    // email headers of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    b.addAdditionalHeader(toHeaderWithDelimiter(MetadataName.CHANGE_ID) + "cid");
    b.addAdditionalHeader(toHeaderWithDelimiter(MetadataName.PATCH_SET) + "1");
    b.addAdditionalHeader(toHeaderWithDelimiter(MetadataName.MESSAGE_TYPE) + "comment");
    b.addAdditionalHeader(
        toHeaderWithDelimiter(MetadataName.TIMESTAMP) + "Tue, 25 Oct 2016 02:11:35 -0700");

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime())
        .isEqualTo(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }

  @Test
  public void parseMetadataFromText() {
    // This tests if the metadata parser is able to parse metadata from the
    // the text body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(toFooterWithDelimiter(MetadataName.CHANGE_ID) + "cid\r\n");
    stringBuilder.append("> " + toFooterWithDelimiter(MetadataName.PATCH_SET) + "1\n");
    stringBuilder.append(toFooterWithDelimiter(MetadataName.MESSAGE_TYPE) + "comment\n");
    stringBuilder.append(
        toFooterWithDelimiter(MetadataName.TIMESTAMP) + "Tue, 25 Oct 2016 02:11:35 -0700\r\n");
    b.textContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime())
        .isEqualTo(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }

  @Test
  public void parseMetadataFromHTML() {
    // This tests if the metadata parser is able to parse metadata from the
    // the HTML body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(
        "<div id\"someid\">" + toFooterWithDelimiter(MetadataName.CHANGE_ID) + "cid</div>");
    stringBuilder.append("<div>" + toFooterWithDelimiter(MetadataName.PATCH_SET) + "1</div>");
    stringBuilder.append(
        "<div>" + toFooterWithDelimiter(MetadataName.MESSAGE_TYPE) + "comment</div>");
    stringBuilder.append(
        "<div>"
            + toFooterWithDelimiter(MetadataName.TIMESTAMP)
            + "Tue, 25 Oct 2016 02:11:35 -0700"
            + "</div>");
    b.htmlContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime())
        .isEqualTo(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }
}
