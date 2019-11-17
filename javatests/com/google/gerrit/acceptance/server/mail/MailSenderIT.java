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
import com.google.gerrit.mail.EmailHeader;
import java.net.URI;
import java.util.Map;
import org.junit.Test;

public class MailSenderIT extends AbstractMailIT {

  @Test
  @GerritConfig(name = "sendemail.replyToAddress", value = "custom@gerritcodereview.com")
  @GerritConfig(name = "receiveemail.protocol", value = "POP3")
  public void outgoingMailHasCustomReplyToHeader() throws Exception {
    createChangeWithReview(user);
    // Check that the custom address was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    Map<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headerString(headers, "Reply-To")).isEqualTo("custom@gerritcodereview.com");
  }

  @Test
  public void outgoingMailHasUserEmailInReplyToHeader() throws Exception {
    createChangeWithReview(user);
    // Check that the user's email was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    Map<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headerString(headers, "Reply-To")).contains(user.email());
  }

  @Test
  public void outgoingMailHasListHeaders() throws Exception {
    String changeId = createChangeWithReview(user);
    // Check that the mail has the expected headers
    assertThat(sender.getMessages()).hasSize(1);
    Map<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    String hostname = URI.create(canonicalWebUrl.get()).getHost();
    String listId = String.format("<gerrit-%s.%s>", project.get(), hostname);
    String unsubscribeLink = String.format("<%ssettings>", canonicalWebUrl.get());
    String threadId =
        String.format(
            "<gerrit.%s.%s@%s>",
            gApi.changes().id(changeId).get().created.getTime(), changeId, hostname);
    assertThat(headerString(headers, "List-Id")).isEqualTo(listId);
    assertThat(headerString(headers, "List-Unsubscribe")).isEqualTo(unsubscribeLink);
    assertThat(headerString(headers, "In-Reply-To")).isEqualTo(threadId);
  }

  private String headerString(Map<String, EmailHeader> headers, String name) {
    EmailHeader header = headers.get(name);
    assertThat(header).isInstanceOf(EmailHeader.String.class);
    return ((EmailHeader.String) header).getString();
  }
}
