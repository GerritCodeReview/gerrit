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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.mail.EmailHeader;
import com.google.gerrit.mail.MailProcessingUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests the presence of required metadata in email headers, text and html. */
public class MailMetadataIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void metadataOnNewChange() throws Exception {
    PushOneCommit.Result newChange = createChange();
    gApi.changes().id(newChange.getChangeId()).addReviewer(user.id().toString());

    List<FakeEmailSender.Message> emails = sender.getMessages();
    assertThat(emails).hasSize(1);
    FakeEmailSender.Message message = emails.get(0);

    String changeURL = "<" + getChangeUrl(newChange.getChange()) + ">";

    Map<String, Object> expectedHeaders = new HashMap<>();
    expectedHeaders.put("Gerrit-PatchSet", "1");
    expectedHeaders.put(
        "Gerrit-Change-Number", String.valueOf(newChange.getChange().getId().get()));
    expectedHeaders.put("Gerrit-MessageType", "newchange");
    expectedHeaders.put("Gerrit-Commit", newChange.getCommit().getId().name());
    expectedHeaders.put("Gerrit-ChangeURL", changeURL);

    assertHeaders(message.headers(), expectedHeaders);

    // Remove metadata that is not present in email
    expectedHeaders.remove("Gerrit-ChangeURL");
    expectedHeaders.remove("Gerrit-Commit");
    assertTextFooter(message.body(), expectedHeaders);
  }

  @Test
  public void metadataOnNewComment() throws Exception {
    PushOneCommit.Result newChange = createChange();
    gApi.changes().id(newChange.getChangeId()).addReviewer(user.id().toString());
    sender.clear();

    // Review change
    ReviewInput input = new ReviewInput();
    input.message = "Test";
    revision(newChange).review(input);
    requestScopeOperations.setApiUser(user.id());
    Collection<ChangeMessageInfo> result =
        gApi.changes().id(newChange.getChangeId()).get().messages;
    assertThat(result).isNotEmpty();

    List<FakeEmailSender.Message> emails = sender.getMessages();
    assertThat(emails).hasSize(1);
    FakeEmailSender.Message message = emails.get(0);

    String changeURL = "<" + getChangeUrl(newChange.getChange()) + ">";
    Map<String, Object> expectedHeaders = new HashMap<>();
    expectedHeaders.put("Gerrit-PatchSet", "1");
    expectedHeaders.put(
        "Gerrit-Change-Number", String.valueOf(newChange.getChange().getId().get()));
    expectedHeaders.put("Gerrit-MessageType", "comment");
    expectedHeaders.put("Gerrit-Commit", newChange.getCommit().getId().name());
    expectedHeaders.put("Gerrit-ChangeURL", changeURL);
    expectedHeaders.put("Gerrit-Comment-Date", Iterables.getLast(result).date);

    assertHeaders(message.headers(), expectedHeaders);

    // Remove metadata that is not present in email
    expectedHeaders.remove("Gerrit-ChangeURL");
    expectedHeaders.remove("Gerrit-Commit");
    assertTextFooter(message.body(), expectedHeaders);
  }

  private static void assertHeaders(Map<String, EmailHeader> have, Map<String, Object> want)
      throws Exception {
    for (Map.Entry<String, Object> entry : want.entrySet()) {
      if (entry.getValue() instanceof String) {
        assertThat(have)
            .containsEntry(
                "X-" + entry.getKey(), new EmailHeader.String((String) entry.getValue()));
      } else if (entry.getValue() instanceof Date) {
        assertThat(have)
            .containsEntry("X-" + entry.getKey(), new EmailHeader.Date((Date) entry.getValue()));
      } else {
        throw new Exception(
            "Object has unsupported type: "
                + entry.getValue().getClass().getName()
                + " must be java.util.Date or java.lang.String for key "
                + entry.getKey());
      }
    }
  }

  private static void assertTextFooter(String body, Map<String, Object> want) throws Exception {
    for (Map.Entry<String, Object> entry : want.entrySet()) {
      if (entry.getValue() instanceof String) {
        assertThat(body).contains(entry.getKey() + ": " + entry.getValue());
      } else if (entry.getValue() instanceof Timestamp) {
        assertThat(body)
            .contains(
                entry.getKey()
                    + ": "
                    + MailProcessingUtil.rfcDateformatter.format(
                        ZonedDateTime.ofInstant(
                            ((Timestamp) entry.getValue()).toInstant(), ZoneId.of("UTC"))));
      } else {
        throw new Exception(
            "Object has unsupported type: "
                + entry.getValue().getClass().getName()
                + " must be java.util.Date or java.lang.String for key "
                + entry.getKey());
      }
    }
  }

  private String getChangeUrl(ChangeData changeData) {
    return canonicalWebUrl.get()
        + "c/"
        + changeData.project().get()
        + "/+/"
        + changeData.getId().get();
  }
}
