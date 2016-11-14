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

import static com.google.gerrit.server.mail.MetadataName.toFooterWithDel;
import static com.google.gerrit.server.mail.MetadataName.toHeaderWithDel;
import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.MetadataName;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

public class MetadataParserTest {
  @Test
  public void testParseMetadataFromHeader() {
    // This tests if the metadata parser is able to parse metadata from the
    // email headers of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    b.addAdditionalHeader(toHeaderWithDel(MetadataName.CHANGE_ID) + "cid");
    b.addAdditionalHeader(toHeaderWithDel(MetadataName.PATCH_SET) + "1");
    b.addAdditionalHeader(
        toHeaderWithDel(MetadataName.MESSAGE_TYPE) +"comment");
    b.addAdditionalHeader(toHeaderWithDel(MetadataName.TIMESTAMP) +
        "Tue, 25 Oct 2016 02:11:35 -0700");

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime()).isEqualTo(
        new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }

  @Test
  public void testParseMetadataFromText() {
    // This tests if the metadata parser is able to parse metadata from the
    // the text body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(
        toFooterWithDel(MetadataName.CHANGE_ID) + "cid" + "\n");
    stringBuilder.append(toFooterWithDel(MetadataName.PATCH_SET) + "1" + "\n");
    stringBuilder.append(
        toFooterWithDel(MetadataName.MESSAGE_TYPE) + "comment" + "\n");
    stringBuilder.append(toFooterWithDel(MetadataName.TIMESTAMP) +
        "Tue, 25 Oct 2016 02:11:35 -0700" + "\n");
    b.textContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime()).isEqualTo(
        new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }

  @Test
  public void testParseMetadataFromHTML() {
    // This tests if the metadata parser is able to parse metadata from the
    // the HTML body of the message.
    MailMessage.Builder b = MailMessage.builder();
    b.id("");
    b.dateReceived(new DateTime());
    b.subject("");

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("<p>" + toFooterWithDel(MetadataName.CHANGE_ID) +
        "cid" + "</p>");
    stringBuilder.append("<p>" + toFooterWithDel(MetadataName.PATCH_SET) +
        "1" + "</p>");
    stringBuilder.append("<p>" + toFooterWithDel(MetadataName.MESSAGE_TYPE) +
        "comment" + "</p>");
    stringBuilder.append("<p>" + toFooterWithDel(MetadataName.TIMESTAMP) +
        "Tue, 25 Oct 2016 02:11:35 -0700" + "</p>");
    b.htmlContent(stringBuilder.toString());

    Address author = new Address("Diffy", "test@gerritcodereview.com");
    b.from(author);

    MailMetadata meta = MetadataParser.parse(b.build());
    assertThat(meta.author).isEqualTo(author.getEmail());
    assertThat(meta.changeId).isEqualTo("cid");
    assertThat(meta.patchSet).isEqualTo(1);
    assertThat(meta.messageType).isEqualTo("comment");
    assertThat(meta.timestamp.getTime()).isEqualTo(
        new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC).getMillis());
  }
}
