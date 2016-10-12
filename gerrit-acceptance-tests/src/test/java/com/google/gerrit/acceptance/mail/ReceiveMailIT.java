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

import com.google.gerrit.acceptance.AbstractDaemonTest;

import com.icegreen.greenmail.junit.GreenMailRule;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import org.junit.Rule;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class ReceiveMailIT extends AbstractDaemonTest {

  @Rule
  public final GreenMailRule greenMail = new GreenMailRule(
      ServerSetupTest.SMTP_POP3_IMAP);

  @Test
  public void testReceive() throws MessagingException {
    GreenMailUser user = greenMail
        .setUser("to@localhost.com", "login-id", "password");
    user.deliver(
        createMimeMessage()); // You can either create a more complex message...
    GreenMailUtil.sendTextEmailTest("to@localhost.com", "from@localhost.com",
        "subject", "body"); // ...or use the default messages

    // assertEquals(2, greenMail.getReceivedMessages().length); // // --- Place your POP3 or IMAP retrieve code here
  }

  private MimeMessage createMimeMessage() {
    return GreenMailUtil
        .createTextEmail("to@localhost.com", "from@localhost.com", "subject",
            "body", greenMail.getImap().getServerSetup());

  }
}
