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

package com.google.gerrit.server.mail.receive;

import com.google.common.primitives.Ints;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gerrit.server.mail.Encryption;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3SClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An implementation of {@link MailReceiver} for POP3. */
@Singleton
public class Pop3MailReceiver extends MailReceiver {
  private static final Logger log = LoggerFactory.getLogger(Pop3MailReceiver.class);

  @Inject
  Pop3MailReceiver(EmailSettings mailSettings, MailProcessor mailProcessor, WorkQueue workQueue) {
    super(mailSettings, mailProcessor, workQueue);
  }

  /**
   * Opens a connection to the mail server, removes emails where deletion is pending, read new email
   * and close the connection.
   *
   * @param async determines if processing messages should happen asynchronous
   * @throws IOException in case of a low-level transport failure
   * @throws MailTransferException in case of a known transport failure
   * @throws MailParsingException in case of a message that could not be parsed
   */
  @Override
  public synchronized void handleEmails(boolean async)
      throws IOException, MailTransferException, MailParsingException {
    POP3Client pop3;
    if (mailSettings.encryption != Encryption.NONE) {
      pop3 = new POP3SClient(mailSettings.encryption.name());
    } else {
      pop3 = new POP3Client();
    }
    if (mailSettings.port > 0) {
      pop3.setDefaultPort(mailSettings.port);
    }
    pop3.connect(mailSettings.host);
    try {
      if (!pop3.login(mailSettings.username, mailSettings.password)) {
        throw new MailTransferException(
            "Could not login to POP3 email server. Check username and password");
      }
      POP3MessageInfo[] messages = pop3.listMessages();
      if (messages == null) {
        throw new MailTransferException("Could not retrieve message list via POP3");
      }
      log.info("Received " + messages.length + " messages via POP3");
      // Fetch messages
      List<MailMessage> mailMessages = new ArrayList<>();
      for (POP3MessageInfo msginfo : messages) {
        if (msginfo == null) {
          // Message was deleted
          continue;
        }
        Reader reader = pop3.retrieveMessage(msginfo.number);
        if (reader == null) {
          throw new MailTransferException(
              "Could not retrieve POP3 message header for message " + msginfo.identifier);
        }
        MailMessage mailMessage = RawMailParser.parse(fetchMessage(reader));
        // Delete messages where deletion is pending. This requires
        // knowing the integer message ID of the email. We therefore parse
        // the message first and extract the Message-ID specified in RFC
        // 822 and delete the message if deletion is pending.
        if (pendingDeletion.contains(mailMessage.id())) {
          if (pop3.deleteMessage(msginfo.number)) {
            pendingDeletion.remove(mailMessage.id());
          } else {
            throw new MailTransferException("Could not delete message " + msginfo.number);
          }
          continue;
        }
        // Process message further
        mailMessages.add(mailMessage);
      }
      dispatchMailProcessor(mailMessages, async);
    } finally {
      pop3.logout();
      pop3.disconnect();
    }
  }

  private final int[] fetchMessage(Reader reader) throws IOException {
    try (BufferedReader bufferedReader =
        reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader)) {
      List<Integer> character = new ArrayList<>();
      int ch;
      while ((ch = bufferedReader.read()) != -1) {
        character.add(ch);
      }
      return Ints.toArray(character);
    }
  }
}
