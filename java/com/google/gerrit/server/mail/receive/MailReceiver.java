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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Future;

/** MailReceiver implements base functionality for receiving emails. */
public abstract class MailReceiver implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected EmailSettings mailSettings;
  protected Set<String> pendingDeletion;
  private MailProcessor mailProcessor;
  private WorkQueue workQueue;
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

  MailReceiver(EmailSettings mailSettings, MailProcessor mailProcessor, WorkQueue workQueue) {
    this.mailSettings = mailSettings;
    this.mailProcessor = mailProcessor;
    this.workQueue = workQueue;
    pendingDeletion = Collections.synchronizedSet(new HashSet<>());
  }

  @Override
  public void start() {
    if (timer == null) {
      timer = new Timer();
    } else {
      timer.cancel();
    }
    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              MailReceiver.this.handleEmails(true);
            } catch (MailTransferException | IOException e) {
              logger.atSevere().withCause(e).log("Error while fetching emails");
            }
          }
        },
        0L,
        mailSettings.fetchInterval);
  }

  @Override
  public void stop() {
    if (timer != null) {
      timer.cancel();
    }
  }

  /**
   * requestDeletion will enqueue an email for deletion and delete it the next time we connect to
   * the email server. This does not guarantee deletion as the Gerrit instance might fail before we
   * connect to the email server.
   *
   * @param messageId
   */
  public void requestDeletion(String messageId) {
    pendingDeletion.add(messageId);
  }

  /**
   * handleEmails will open a connection to the mail server, remove emails where deletion is
   * pending, read new email and close the connection.
   *
   * @param async determines if processing messages should happen asynchronously
   * @throws MailTransferException in case of a known transport failure
   * @throws IOException in case of a low-level transport failure
   */
  @VisibleForTesting
  public abstract void handleEmails(boolean async) throws MailTransferException, IOException;

  protected void dispatchMailProcessor(List<MailMessage> messages, boolean async) {
    for (MailMessage m : messages) {
      if (async) {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            workQueue
                .getDefaultQueue()
                .submit(
                    () -> {
                      try {
                        mailProcessor.process(m);
                        requestDeletion(m.id());
                      } catch (RestApiException | UpdateException e) {
                        logger.atSevere().withCause(e).log(
                            "Mail: Can't process message %s . Won't delete.", m.id());
                      }
                    });
      } else {
        // Synchronous processing is used only in tests.
        try {
          mailProcessor.process(m);
          requestDeletion(m.id());
        } catch (RestApiException | UpdateException e) {
          logger.atSevere().withCause(e).log("Mail: Can't process messages. Won't delete.");
        }
      }
    }
  }
}
