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
 * Provides a raw message payload and a parsed {@code MailMessage} to test parsing mime/alternative
 * (text + html) content.
 */
public class HtmlMimeMessage extends RawMailMessage {
  private static String textContent = "Simple test";

  // htmlContent is encoded in quoted-printable
  private static String htmlContent =
      "<div dir=3D\"ltr\">Test <span style"
          + "=3D\"background-color:rgb(255,255,0)\">Messa=\n"
          + "ge</span> in <u>HTML=C2=A0</u><a href=3D\"https://en.wikipedia.org/"
          + "wiki/%C3%=\n9Cmlaut_(band)\" class=3D\"gmail-mw-redirect\" title=3D\""
          + "=C3=9Cmlaut (band)\" st=\nyle=3D\"text-decoration:none;color:rgb(11,"
          + "0,128);background-image:none;backg=\nround-position:initial;background"
          + "-size:initial;background-repeat:initial;ba=\nckground-origin:initial;"
          + "background-clip:initial;font-family:sans-serif;font=\n"
          + "-size:14px\">=C3=9C</a></div>";

  private static String unencodedHtmlContent =
      ""
          + "<div dir=\"ltr\">Test <span style=\"background-color:rgb(255,255,0)\">"
          + "Message</span> in <u>HTML </u><a href=\"https://en.wikipedia.org/wiki/"
          + "%C3%9Cmlaut_(band)\" class=\"gmail-mw-redirect\" title=\"Ümlaut "
          + "(band)\" style=\"text-decoration:none;color:rgb(11,0,128);"
          + "background-image:none;background-position:initial;background-size:"
          + "initial;background-repeat:initial;background-origin:initial;background"
          + "-clip:initial;font-family:sans-serif;font-size:14px\">Ü</a></div>";

  private static String raw =
      ""
          + "MIME-Version: 1.0\n"
          + "Date: Tue, 25 Oct 2016 02:11:35 -0700\n"
          + "Message-ID: <001a114cd8be55b4ab053face5cd@google.com>\n"
          + "Subject: Change in gerrit[master]: Implement receiver class structure "
          + "and bindings\n"
          + "From: \"ekempin (Gerrit)\" <noreply-gerritcodereview-qUgXfQecoDLHwp0Ml"
          + "dAzig@google.com>\n"
          + "To: Patrick Hiesel <hiesel@google.com>\n"
          + "Cc: ekempin <ekempin@google.com>\n"
          + "Content-Type: multipart/alternative; boundary=001a114cd8b"
          + "e55b486053face5ca\n"
          + "\n"
          + "--001a114cd8be55b486053face5ca\n"
          + "Content-Type: text/plain; charset=UTF-8; format=flowed; delsp=yes\n"
          + "\n"
          + textContent
          + "\n"
          + "--001a114cd8be55b486053face5ca\n"
          + "Content-Type: text/html; charset=UTF-8\n"
          + "Content-Transfer-Encoding: quoted-printable\n"
          + "\n"
          + htmlContent
          + "\n"
          + "--001a114cd8be55b486053face5ca--";

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
    MailMessage.Builder expect = MailMessage.builder();
    expect
        .id("<001a114cd8be55b4ab053face5cd@google.com>")
        .from(
            new Address(
                "ekempin (Gerrit)", "noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com"))
        .addCc(new Address("ekempin", "ekempin@google.com"))
        .addTo(new Address("Patrick Hiesel", "hiesel@google.com"))
        .textContent(textContent)
        .htmlContent(unencodedHtmlContent)
        .subject("Change in gerrit[master]: Implement " + "receiver class structure and bindings")
        .addAdditionalHeader("MIME-Version: 1.0")
        .dateReceived(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC));
    return expect.build();
  }
}
