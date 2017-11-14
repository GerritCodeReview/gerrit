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
package com.google.gerrit.mail.data;

import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.MailMessage;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import org.junit.Ignore;

/** Tests that non-UTF8 encodings are handled correctly. */
@Ignore
public class NonUTF8Message extends RawMailMessage {
  private static String textContent = "Some Text";
  private static String raw =
      ""
          + "Date: Tue, 25 Oct 2016 02:11:35 -0700\n"
          + "Message-ID: <001a114da7ae26e2eb053fe0c29c@google.com>\n"
          + "Subject: =?UTF-8?B?8J+YmyB0ZXN0?=\n"
          + "From: \"Jonathan Nieder (Gerrit)\" <noreply-gerritcodereview-"
          + "CtTy0igsBrnvL7dKoWEIEg@google.com>\n"
          + "To: ekempin <ekempin@google.com>\n"
          + "Content-Type: text/plain; charset=UTF-8; format=flowed; delsp=yes\n"
          + "\n"
          + textContent;

  @Override
  public String raw() {
    return null;
  }

  @Override
  public int[] rawChars() {
    int[] arr = new int[raw.length()];
    int i = 0;
    for (char c : raw.toCharArray()) {
      arr[i++] = c;
    }
    return arr;
  }

  @Override
  public MailMessage expectedMailMessage() {
    MailMessage.Builder expect = MailMessage.builder();
    expect
        .id("<001a114da7ae26e2eb053fe0c29c@google.com>")
        .from(
            new Address(
                "Jonathan Nieder (Gerrit)",
                "noreply-gerritcodereview-CtTy0igsBrnvL7dKoWEIEg@google.com"))
        .addTo(new Address("ekempin", "ekempin@google.com"))
        .textContent(textContent)
        .subject("\uD83D\uDE1B test")
        .dateReceived(
            LocalDateTime.of(2016, Month.OCTOBER, 25, 9, 11, 35)
                .atOffset(ZoneOffset.UTC)
                .toInstant());
    return expect.build();
  }
}
