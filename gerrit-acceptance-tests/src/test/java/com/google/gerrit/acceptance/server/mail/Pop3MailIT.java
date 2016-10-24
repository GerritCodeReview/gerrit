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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.server.mail.receive.MailReceiver;
import com.google.inject.Inject;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;

@NoHttpd
public class Pop3MailIT extends AbstractDaemonTest {
  private final static String HOST = "localhost";
  private final static String USERNAME = "user@domain.com";
  private final static String PASSWORD = "password";
  private final static String PROTOCOL = "POP3";

  @Inject
  MailReceiver mailReceiver;

  @Rule
  public final GreenMailRule mockPop3Server = new GreenMailRule(
      ServerSetupTest.POP3);

  @GerritConfigs({
      @GerritConfig(name = "receiveemail.host", value = HOST),
      @GerritConfig(name = "receiveemail.port", value = "3110"),
      @GerritConfig(name = "receiveemail.username", value = USERNAME),
      @GerritConfig(name = "receiveemail.password", value = PASSWORD),
      @GerritConfig(name = "receiveemail.protocol", value = PROTOCOL),
      @GerritConfig(name = "receiveemail.fetchInterval", value = "99"),
  })
  @Test
  public void testDeletePop3() throws Exception {
    GreenMailUser user = mockPop3Server.setUser(USERNAME, USERNAME, PASSWORD);
    user.deliver(createSimpleMessage());
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(1);
    // Let Gerrit handle emails
    mailReceiver.handleEmails();
    // Check that the message is still present
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(1);
    // Mark the message for deletion
    mailReceiver.deleteEmailEventually(
        mockPop3Server.getReceivedMessages()[0].getMessageID());
    // Let Gerrit handle emails
    mailReceiver.handleEmails();
    // Check that the message was deleted
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(0);
  }

  private MimeMessage createSimpleMessage() {
    return GreenMailUtil
        .createTextEmail(USERNAME, "from@localhost.com", "subject",
            "body",
            greenMail.getImap().getServerSetup());
  }
}
