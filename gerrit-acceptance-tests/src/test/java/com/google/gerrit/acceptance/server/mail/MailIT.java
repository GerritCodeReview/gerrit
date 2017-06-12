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
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.server.mail.receive.MailReceiver;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;
import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import javax.mail.internet.MimeMessage;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoHttpd
@RunWith(ConfigSuite.class)
public class MailIT extends AbstractDaemonTest {
  private static final String RECEIVEEMAIL = "receiveemail";
  private static final String HOST = "localhost";
  private static final String USERNAME = "user@domain.com";
  private static final String PASSWORD = "password";

  @Inject private MailReceiver mailReceiver;

  @Inject private GreenMail greenMail;

  @Rule
  public final GreenMailRule mockPop3Server = new GreenMailRule(ServerSetupTest.SMTP_POP3_IMAP);

  @ConfigSuite.Default
  public static Config pop3Config() {
    Config cfg = new Config();
    cfg.setString(RECEIVEEMAIL, null, "host", HOST);
    cfg.setString(RECEIVEEMAIL, null, "port", "3110");
    cfg.setString(RECEIVEEMAIL, null, "username", USERNAME);
    cfg.setString(RECEIVEEMAIL, null, "password", PASSWORD);
    cfg.setString(RECEIVEEMAIL, null, "protocol", "POP3");
    cfg.setString(RECEIVEEMAIL, null, "fetchInterval", "99");
    return cfg;
  }

  @ConfigSuite.Config
  public static Config imapConfig() {
    Config cfg = new Config();
    cfg.setString(RECEIVEEMAIL, null, "host", HOST);
    cfg.setString(RECEIVEEMAIL, null, "port", "3143");
    cfg.setString(RECEIVEEMAIL, null, "username", USERNAME);
    cfg.setString(RECEIVEEMAIL, null, "password", PASSWORD);
    cfg.setString(RECEIVEEMAIL, null, "protocol", "IMAP");
    cfg.setString(RECEIVEEMAIL, null, "fetchInterval", "99");
    return cfg;
  }

  @Test
  public void testDelete() throws Exception {
    GreenMailUser user = mockPop3Server.setUser(USERNAME, USERNAME, PASSWORD);
    user.deliver(createSimpleMessage());
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(1);
    // Let Gerrit handle emails
    mailReceiver.handleEmails(false);
    // Check that the message is still present
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(1);
    // Mark the message for deletion
    mailReceiver.requestDeletion(mockPop3Server.getReceivedMessages()[0].getMessageID());
    // Let Gerrit handle emails
    mailReceiver.handleEmails(false);
    // Check that the message was deleted
    assertThat(mockPop3Server.getReceivedMessages().length).isEqualTo(0);
  }

  private MimeMessage createSimpleMessage() {
    return GreenMailUtil.createTextEmail(
        USERNAME, "from@localhost.com", "subject", "body", greenMail.getImap().getServerSetup());
  }
}
