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

package com.google.gerrit.acceptance.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.server.mail.receive.MailReceiver;
import com.google.inject.Inject;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.Rule;
import org.junit.Test;

import javax.mail.internet.MimeMessage;

public class ReceiveMailIT extends AbstractDaemonTest {

  @Inject
  MailReceiver mailReceiver;

  @Rule
  public final GreenMailRule greenMail = new GreenMailRule(
      ServerSetupTest.ALL);

  @GerritConfigs({
      @GerritConfig(name = "receiveemail.host", value = "localhost"),
      @GerritConfig(name = "receiveemail.port", value = "3110"),
      @GerritConfig(name = "receiveemail.username", value = "login-id"),
      @GerritConfig(name = "receiveemail.password", value = "password"),
      @GerritConfig(name = "receiveemail.protocol", value = "POP3"),
      @GerritConfig(name = "receiveemail.fetchInterval", value = "2"),
  })
  @Test
  public void testReceiveAndDeletePop3() throws Exception {
    GreenMailUser user = greenMail
        .setUser("to@localhost.com", "login-id", "password");
    user.deliver(createMimeMessage());

    assertThat(greenMail.getReceivedMessages().length).isEqualTo(1);

    // Try to delete
    mailReceiver.deleteEmailEventually(
        greenMail.getReceivedMessages()[0].getMessageID());
    Thread.sleep(3000);

    assertThat(greenMail.getReceivedMessages().length).isEqualTo(0);
  }

  private MimeMessage createMimeMessage() {
    return GreenMailUtil
        .createTextEmail("to@localhost.com", "from@localhost.com", "subject",
            "body", greenMail.getImap().getServerSetup());

  }
}
