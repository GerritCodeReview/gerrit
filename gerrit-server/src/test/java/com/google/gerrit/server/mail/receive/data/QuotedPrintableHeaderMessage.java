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
 * Provides a raw message payload and a parsed {@code MailMessage} to test parsing a quoted
 * printable encoded subject.
 */
public class QuotedPrintableHeaderMessage extends RawMailMessage {
  private static String textContent = "Some Text";
  private static String raw =
      ""
          + "Date: Tue, 25 Oct 2016 02:11:35 -0700\n"
          + "Message-ID: <001a114da7ae26e2eb053fe0c29c@google.com>\n"
          + "Subject: =?UTF-8?Q?=C3=A2me vulgaire?=\n"
          + "From: \"Jonathan Nieder (Gerrit)\" <noreply-gerritcodereview-"
          + "CtTy0igsBrnvL7dKoWEIEg@google.com>\n"
          + "To: ekempin <ekempin@google.com>\n"
          + "Content-Type: text/plain; charset=UTF-8; format=flowed; delsp=yes\n"
          + "\n"
          + textContent;

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
        .id("<001a114da7ae26e2eb053fe0c29c@google.com>")
        .from(
            new Address(
                "Jonathan Nieder (Gerrit)",
                "noreply-gerritcodereview-CtTy0igsBrnvL7dKoWEIEg@google.com"))
        .addTo(new Address("ekempin", "ekempin@google.com"))
        .textContent(textContent)
        .subject("âme vulgaire")
        .dateReceived(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC));
    return expect.build();
  }
}
