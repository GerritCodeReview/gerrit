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

package com.google.gerrit.server.mail.receive.data;

import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.receive.MailMessage;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Provides a raw message payload and a parsed {@code MailMessage} to check that mime parts that are
 * neither text/plain, nor * text/html are dropped.
 */
public class AttachmentMessage extends RawMailMessage {
  private static String raw =
      "MIME-Version: 1.0\n"
          + "Date: Tue, 25 Oct 2016 02:11:35 -0700\n"
          + "Message-ID: <CAM7sg=3meaAVUxW3KXeJEVs8sv_ADw1BnvpcHHiYVR2TQQi__w"
          + "@mail.gmail.com>\n"
          + "Subject: Test Subject\n"
          + "From: Patrick Hiesel <hiesel@google.com>\n"
          + "To: Patrick Hiesel <hiesel@google.com>\n"
          + "Content-Type: multipart/mixed; boundary=001a114e019a56962d054062708f\n"
          + "\n"
          + "--001a114e019a56962d054062708f\n"
          + "Content-Type: multipart/alternative; boundary=001a114e019a5696250540"
          + "62708d\n"
          + "\n"
          + "--001a114e019a569625054062708d\n"
          + "Content-Type: text/plain; charset=UTF-8\n"
          + "\n"
          + "Contains unwanted attachment"
          + "\n"
          + "--001a114e019a569625054062708d\n"
          + "Content-Type: text/html; charset=UTF-8\n"
          + "\n"
          + "<div dir=\"ltr\">Contains unwanted attachment</div>"
          + "\n"
          + "--001a114e019a569625054062708d--\n"
          + "--001a114e019a56962d054062708f\n"
          + "Content-Type: text/plain; charset=US-ASCII; name=\"test.txt\"\n"
          + "Content-Disposition: attachment; filename=\"test.txt\"\n"
          + "Content-Transfer-Encoding: base64\n"
          + "X-Attachment-Id: f_iv264bt50\n"
          + "\n"
          + "VEVTVAo=\n"
          + "--001a114e019a56962d054062708f--";

  @Override
  public String raw() {
    return raw;
  }

  @Override
  public int[] rawChars() {
    return null;
  }

  @Override
  public MailMessage expectedMailMessage() {
    System.out.println("\uD83D\uDE1B test");
    MailMessage.Builder expect = MailMessage.builder();
    expect
        .id("<CAM7sg=3meaAVUxW3KXeJEVs8sv_ADw1BnvpcHHiYVR2TQQi__w" + "@mail.gmail.com>")
        .from(new Address("Patrick Hiesel", "hiesel@google.com"))
        .addTo(new Address("Patrick Hiesel", "hiesel@google.com"))
        .textContent("Contains unwanted attachment")
        .htmlContent("<div dir=\"ltr\">Contains unwanted attachment</div>")
        .subject("Test Subject")
        .addAdditionalHeader("MIME-Version: 1.0")
        .dateReceived(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC));
    return expect.build();
  }
}
