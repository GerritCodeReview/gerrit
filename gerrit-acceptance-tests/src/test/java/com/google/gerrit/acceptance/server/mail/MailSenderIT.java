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
import com.google.gerrit.server.mail.send.EmailHeader;
import java.util.Map;
import org.junit.Test;

public class MailSenderIT extends AbstractMailIT {

  @Test
  @GerritConfig(name="sendemail.replyToAddress", value = "custom@gerritcodereview.com")
  @GerritConfig(name="receiveemail.protocol", value = "POP3")
  public void outgoingMailHasCustomReplyToHeader() throws Exception {
    createChangeWithReview(admin, user);
    // Check that the custom address was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    Map<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headers.get("Reply-To")).isInstanceOf(EmailHeader.String.class);
    assertThat(((EmailHeader.String) headers.get("Reply-To")).getString())
        .isEqualTo("custom@gerritcodereview.com");
  }

  @Test
  public void outgoingMailHasUserEmailInReplyToHeader() throws Exception {
    createChangeWithReview(admin, user);
    // Check that the user's email was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    Map<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headers.get("Reply-To")).isInstanceOf(EmailHeader.String.class);
    assertThat(((EmailHeader.String) headers.get("Reply-To")).getString())
        .isEqualTo(user.email);
  }
}
