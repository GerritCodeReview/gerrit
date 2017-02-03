// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.mail.MailUtil;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.gerrit.server.mail.receive.MailProcessor;
import com.google.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class ListMailFilterIT extends AbstractMailIT {
  @Inject private MailProcessor mailProcessor;

  @Test
  @GerritConfig(name = "receiveemail.listfilter.mode", value = "OFF")
  public void listFilterOff() throws Exception {
    ChangeInfo changeInfo = createChangeAndReplyByEmail();
    // Check that the comments from the email have been persisted
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeInfo.id).get().messages;
    assertThat(messages).hasSize(3);
  }

  @Test
  @GerritConfig(name = "receiveemail.listfilter.mode", value = "WHITELIST")
  @GerritConfig(
    name = "receiveemail.listfilter.patterns",
    values = {".+ser@example\\.com", "a@b\\.com"}
  )
  public void listFilterWhitelistDoesNotFilterListedUser() throws Exception {
    ChangeInfo changeInfo = createChangeAndReplyByEmail();
    // Check that the comments from the email have been persisted
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeInfo.id).get().messages;
    assertThat(messages).hasSize(3);
  }

  @Test
  @GerritConfig(name = "receiveemail.listfilter.mode", value = "WHITELIST")
  @GerritConfig(
    name = "receiveemail.listfilter.patterns",
    values = {".+@gerritcodereview\\.com", "a@b\\.com"}
  )
  public void listFilterWhitelistFiltersNotListedUser() throws Exception {
    ChangeInfo changeInfo = createChangeAndReplyByEmail();
    // Check that the comments from the email have NOT been persisted
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeInfo.id).get().messages;
    assertThat(messages).hasSize(2);
  }

  @Test
  @GerritConfig(name = "receiveemail.listfilter.mode", value = "BLACKLIST")
  @GerritConfig(
    name = "receiveemail.listfilter.patterns",
    values = {".+@gerritcodereview\\.com", "a@b\\.com"}
  )
  public void listFilterBlacklistDoesNotFilterNotListedUser() throws Exception {
    ChangeInfo changeInfo = createChangeAndReplyByEmail();
    // Check that the comments from the email have been persisted
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeInfo.id).get().messages;
    assertThat(messages).hasSize(3);
  }

  @Test
  @GerritConfig(name = "receiveemail.listfilter.mode", value = "BLACKLIST")
  @GerritConfig(
    name = "receiveemail.listfilter.patterns",
    values = {".+@example\\.com", "a@b\\.com"}
  )
  public void listFilterBlacklistFiltersListedUser() throws Exception {
    ChangeInfo changeInfo = createChangeAndReplyByEmail();
    // Check that the comments from the email have been persisted
    Collection<ChangeMessageInfo> messages = gApi.changes().id(changeInfo.id).get().messages;
    assertThat(messages).hasSize(2);
  }

  private ChangeInfo createChangeAndReplyByEmail() throws Exception {
    String changeId = createChangeWithReview();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    List<CommentInfo> comments = gApi.changes().id(changeId).current().commentsAsList();
    String ts =
        MailUtil.rfcDateformatter.format(
            ZonedDateTime.ofInstant(comments.get(0).updated.toInstant(), ZoneId.of("UTC")));

    // Build Message
    MailMessage.Builder b = messageBuilderWithDefaultFields();
    String txt =
        newPlaintextBody(
            canonicalWebUrl.get() + "#/c/" + changeInfo._number + "/1",
            "Test Message",
            null,
            null,
            null);
    b.textContent(txt + textFooterForChange(changeId, ts));

    mailProcessor.process(b.build());
    return changeInfo;
  }
}
