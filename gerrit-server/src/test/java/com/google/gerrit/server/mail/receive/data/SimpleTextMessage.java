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
 * Provides a raw message payload and a parsed {@code MailMessage} to test parsing a simple text
 * message with different headers.
 */
public class SimpleTextMessage extends RawMailMessage {
  private static String textContent =
      ""
          + "Jonathan Nieder has posted comments on this change. (  \n"
          + "https://gerrit-review.googlesource.com/90018 )\n"
          + "\n"
          + "Change subject: (Re)enable voting buttons for merged changes\n"
          + "...........................................................\n"
          + "\n"
          + "\n"
          + "Patch Set 2:\n"
          + "\n"
          + "This is producing NPEs server-side and 500s for the client.   \n"
          + "when I try to load this change:\n"
          + "\n"
          + "  Error in GET /changes/90018/detail?O=10004\n"
          + "  com.google.gwtorm.OrmException: java.lang.NullPointerException\n"
          + "\tat com.google.gerrit.change.ChangeJson.format(ChangeJson.java:303)\n"
          + "\tat com.google.gerrit.change.ChangeJson.format(ChangeJson.java:285)\n"
          + "\tat com.google.gerrit.change.ChangeJson.format(ChangeJson.java:263)\n"
          + "\tat com.google.gerrit.change.GetChange.apply(GetChange.java:50)\n"
          + "\tat com.google.gerrit.change.GetDetail.apply(GetDetail.java:51)\n"
          + "\tat com.google.gerrit.change.GetDetail.apply(GetDetail.java:26)\n"
          + "\tat  \n"
          + "com.google.gerrit.RestApiServlet.service(RestApiServlet.java:367)\n"
          + "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:717)\n"
          + "[...]\n"
          + "  Caused by: java.lang.NullPointerException\n"
          + "\tat  \n"
          + "com.google.gerrit.ChangeJson.setLabelScores(ChangeJson.java:670)\n"
          + "\tat  \n"
          + "com.google.gerrit.ChangeJson.labelsFor(ChangeJson.java:845)\n"
          + "\tat  \n"
          + "com.google.gerrit.change.ChangeJson.labelsFor(ChangeJson.java:598)\n"
          + "\tat  \n"
          + "com.google.gerrit.change.ChangeJson.toChange(ChangeJson.java:499)\n"
          + "\tat com.google.gerrit.change.ChangeJson.format(ChangeJson.java:294)\n"
          + "\t... 105 more\n"
          + "-- \n"
          + "To view, visit https://gerrit-review.googlesource.com/90018\n"
          + "To unsubscribe, visit https://gerrit-review.googlesource.com\n"
          + "\n"
          + "Gerrit-MessageType: comment\n"
          + "Gerrit-Change-Id: Iba501e00bee77be3bd0ced72f88fd04ba0accaed\n"
          + "Gerrit-PatchSet: 2\n"
          + "Gerrit-Project: gerrit\n"
          + "Gerrit-Branch: master\n"
          + "Gerrit-Owner: ekempin <ekempin@google.com>\n"
          + "Gerrit-Reviewer: Dave Borowitz <dborowitz@google.com>\n"
          + "Gerrit-Reviewer: Edwin Kempin <ekempin@google.com>\n"
          + "Gerrit-Reviewer: GerritForge CI <gerritforge@gmail.com>\n"
          + "Gerrit-Reviewer: Jonathan Nieder <jrn@google.com>\n"
          + "Gerrit-Reviewer: Patrick Hiesel <hiesel@google.com>\n"
          + "Gerrit-Reviewer: ekempin <ekempin@google.com>\n"
          + "Gerrit-HasComments: No";

  private static String raw =
      ""
          + "Authentication-Results: mx.google.com; dkim=pass header.i="
          + "@google.com;\n"
          + "Date: Tue, 25 Oct 2016 02:11:35 -0700\n"
          + "In-Reply-To: <gerrit.1477487889000.Iba501e00bee77be3bd0ced"
          + "72f88fd04ba0accaed@gerrit-review.googlesource.com>\n"
          + "References: <gerrit.1477487889000.Iba501e00bee77be3bd0ced72f8"
          + "8fd04ba0accaed@gerrit-review.googlesource.com>\n"
          + "Message-ID: <001a114da7ae26e2eb053fe0c29c@google.com>\n"
          + "Subject: Change in gerrit[master]: (Re)enable voting buttons for "
          + "merged changes\n"
          + "From: \"Jonathan Nieder (Gerrit)\" <noreply-gerritcodereview-CtTy0"
          + "igsBrnvL7dKoWEIEg@google.com>\n"
          + "To: ekempin <ekempin@google.com>\n"
          + "Cc: Dave Borowitz <dborowitz@google.com>, Jonathan Nieder "
          + "<jrn@google.com>, Patrick Hiesel <hiesel@google.com>\n"
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
    MailMessage.Builder expect = MailMessage.builder();
    expect
        .id("<001a114da7ae26e2eb053fe0c29c@google.com>")
        .from(
            new Address(
                "Jonathan Nieder (Gerrit)",
                "noreply-gerritcodereview-CtTy0igsBrnvL7dKoWEIEg@google.com"))
        .addTo(new Address("ekempin", "ekempin@google.com"))
        .addCc(new Address("Dave Borowitz", "dborowitz@google.com"))
        .addCc(new Address("Jonathan Nieder", "jrn@google.com"))
        .addCc(new Address("Patrick Hiesel", "hiesel@google.com"))
        .textContent(textContent)
        .subject("Change in gerrit[master]: (Re)enable voting" + " buttons for merged changes")
        .dateReceived(new DateTime(2016, 10, 25, 9, 11, 35, 0, DateTimeZone.UTC))
        .addAdditionalHeader(
            "Authentication-Results: mx.google.com; " + "dkim=pass header.i=@google.com;")
        .addAdditionalHeader(
            "In-Reply-To: <gerrit.1477487889000.Iba501e00bee"
                + "77be3bd0ced72f88fd04ba0accaed@gerrit-review.googlesource.com>")
        .addAdditionalHeader(
            "References: <gerrit.1477487889000.Iba501e00bee"
                + "77be3bd0ced72f88fd04ba0accaed@gerrit-review.googlesource.com>");
    return expect.build();
  }
}
