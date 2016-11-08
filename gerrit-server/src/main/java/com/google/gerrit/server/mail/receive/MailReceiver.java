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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/** MailReceiver implements base functionality for receiving emails. */
public abstract class MailReceiver implements LifecycleListener {
  protected EmailSettings mailSettings;
  protected Set<String> pendingDeletion;
  private Timer timer;

  public static class Module extends LifecycleModule {
    private final EmailSettings mailSettings;

    @Inject
    Module(EmailSettings mailSettings) {
      this.mailSettings = mailSettings;
    }

    @Override
    protected void configure() {
      if (mailSettings.protocol == Protocol.NONE) {
        return;
      }
      listener().to(MailReceiver.class);
      switch (mailSettings.protocol) {
        case IMAP:
          bind(MailReceiver.class).to(ImapMailReceiver.class);
          break;
        case POP3:
          bind(MailReceiver.class).to(Pop3MailReceiver.class);
          break;
        case NONE:
        default:
      }
    }
  }

  @Inject
  public MailReceiver(EmailSettings mailSettings) {
    this.mailSettings = mailSettings;
    pendingDeletion = Collections.synchronizedSet(new HashSet<>());
  }

  @Override
  public void start() {
    if (timer == null) {
      timer = new Timer();
    } else {
      timer.cancel();
    }
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        MailReceiver.this.handleEmails();
      }
    }, 0L, mailSettings.fetchInterval);
  }

  @Override
  public void stop() {
    if (timer != null) {
      timer.cancel();
    }
  }

  /**
   * requestDeletion will enqueue an email for deletion and delete it the
   * next time we connect to the email server. This does not guarantee deletion
   * as the Gerrit instance might fail before we connect to the email server.
   * @param messageId
   */
  public void requestDeletion(String messageId) {
    pendingDeletion.add(messageId);
  }

  /**
   * handleEmails will open a connection to the mail server, remove emails
   * where deletion is pending, read new email and close the connection.
   */
  @VisibleForTesting
  public abstract void handleEmails();
}
