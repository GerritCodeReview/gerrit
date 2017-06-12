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

import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gerrit.server.mail.Encryption;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.imap.IMAPClient;
import org.apache.commons.net.imap.IMAPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImapMailReceiver extends MailReceiver {
  private static final Logger log = LoggerFactory.getLogger(ImapMailReceiver.class);
  private static final String INBOX_FOLDER = "INBOX";
  private static final int OPERATION_TIMEOUT = 30 * 1000; // 30s (in ms)

  @Inject
  ImapMailReceiver(EmailSettings mailSettings, MailProcessor mailProcessor, WorkQueue workQueue) {
    super(mailSettings, mailProcessor, workQueue);
  }

  /**
   * Opens a connection to the mail server, removes emails where deletion is
   * pending, reads new email and closes the connection.
   *
   * @param async determines if processing messages should happen asynchronous.
   */
  @Override
  public synchronized void handleEmails(boolean async) {
    IMAPClient imap;
    if (mailSettings.encryption != Encryption.NONE) {
      imap = new IMAPSClient(mailSettings.encryption.name(), false);
    } else {
      imap = new IMAPClient();
    }
    if (mailSettings.port > 0) {
      imap.setDefaultPort(mailSettings.port);
    }
    imap.setDefaultTimeout(OPERATION_TIMEOUT);
    try {
      imap.connect(mailSettings.host);
      try {
        if (!imap.login(mailSettings.username, mailSettings.password)) {
          log.error("Could not login to IMAP server");
          return;
        }
        try {
          if (!imap.select(INBOX_FOLDER)) {
            log.error("Could not select IMAP folder " + INBOX_FOLDER);
            return;
          }
          // Fetch just the internal dates first to know how many messages we
          // should fetch.
          if (!imap.fetch("1:*", "(INTERNALDATE)")) {
            log.error("IMAP fetch failed. Will retry in next fetch cycle.");
            return;
          }
          // Format of reply is one line per email and one line to indicate
          // that the fetch was successful.
          // Example:
          // * 1 FETCH (INTERNALDATE "Mon, 24 Oct 2016 16:53:22 +0200 (CEST)")
          // * 2 FETCH (INTERNALDATE "Mon, 24 Oct 2016 16:53:22 +0200 (CEST)")
          // AAAC OK FETCH completed.
          int numMessages = imap.getReplyStrings().length - 1;
          log.info("Fetched " + numMessages + " messages via IMAP");
          if (numMessages == 0) {
            return;
          }
          // Fetch the full version of all emails
          List<MailMessage> mailMessages = new ArrayList<>(numMessages);
          for (int i = 1; i <= numMessages; i++) {
            if (imap.fetch(i + ":" + i, "(BODY.PEEK[])")) {
              // Obtain full reply
              String[] rawMessage = imap.getReplyStrings();
              if (rawMessage.length < 2) {
                continue;
              }
              // First and last line are IMAP status codes. We have already
              // checked, that the fetch returned true (OK), so we safely ignore
              // those two lines.
              StringBuilder b = new StringBuilder(2 * (rawMessage.length - 2));
              for (int j = 1; j < rawMessage.length - 1; j++) {
                if (j > 1) {
                  b.append("\n");
                }
                b.append(rawMessage[j]);
              }
              try {
                MailMessage mailMessage = RawMailParser.parse(b.toString());
                if (pendingDeletion.contains(mailMessage.id())) {
                  // Mark message as deleted
                  if (imap.store(i + ":" + i, "+FLAGS", "(\\Deleted)")) {
                    pendingDeletion.remove(mailMessage.id());
                  } else {
                    log.error("Could not mark mail message as deleted: " + mailMessage.id());
                  }
                } else {
                  mailMessages.add(mailMessage);
                }
              } catch (MailParsingException e) {
                log.error("Exception while parsing email after IMAP fetch", e);
              }
            } else {
              log.error("IMAP fetch failed. Will retry in next fetch cycle.");
            }
          }
          // Permanently delete emails marked for deletion
          if (!imap.expunge()) {
            log.error("Could not expunge IMAP emails");
          }
          dispatchMailProcessor(mailMessages, async);
        } finally {
          imap.logout();
        }
      } finally {
        imap.disconnect();
      }
    } catch (IOException e) {
      log.error("Error while talking to IMAP server", e);
      return;
    }
  }
}
