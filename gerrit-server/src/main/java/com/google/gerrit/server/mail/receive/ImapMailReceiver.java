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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class ImapMailReceiver extends MailReceiver {
  private final boolean useIdle;

  @Inject
  public ImapMailReceiver(EmailSettings mailSettings,
      @GerritServerConfig Config cfg) {
    super(mailSettings);
    useIdle = cfg.getBoolean("receiveemail", null, "useImapIdle", false);
  }

  /**
   * deleteEmailEventually will enqueue an email for deletion and delete it the
   * next time we connect to the email server. This does not guarantee deletion
   * as the Gerrit instance might fail before we connect to the email server.
   * @param messageId
   */
  public void deleteEmailEventually(String messageId) {
    if (useIdle) {
      // TODO(hiesel) Handle emailFolder.idle() for IMAPv4 PUSH
    } else {
      pendingDeletion.add(messageId);
    }
  }

  /**
   * handleEmails will open a connection to the mail server, remove emails that
   * where deletion is pending, read new email and close the connection.
   */
  protected synchronized void handleEmails() {
    // TODO(hiesel) Implement.
  }
}
