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
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailSettings;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An abstract implementation of base functionality for receiving emails. */
public abstract class MailReceiver implements LifecycleListener {
  private static final Logger log =
      LoggerFactory.getLogger(MailReceiver.class.getName());

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
      if (mailSettings.protocol == Protocol.IMAP) {
        listener().to(MailReceiver.class);
        bind(MailReceiver.class).to(ImapMailReceiver.class);
      } else if (mailSettings.protocol == Protocol.POP3) {
        listener().to(MailReceiver.class);
        bind(MailReceiver.class).to(Pop3MailReceiver.class);
      }
    }
  }

  @Inject
  MailReceiver(EmailSettings mailSettings, MailProcessor mailProcessor,
      WorkQueue workQueue) {
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
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        MailReceiver.this.handleEmails(true);
      }
    }, 0l, mailSettings.fetchInterval);
  }

  @Override
  public void stop() {
    if (timer != null) {
      timer.cancel();
    }
  }

  /**
   * Enqueues an email for deletion and deletes it the next time we connect to the email server.
   *
   * <p>This does not guarantee deletion as the Gerrit instance might fail before we connect to the
   * email server.
   * @param messageId of the message to delete
   */
  public void requestDeletion(String messageId) {
    pendingDeletion.add(messageId);
  }

  /**
   * Opens a connection to the mail server, removes emails where deletion is pending, read new email
   * and close the connection.
   *
   * @param async determines if processing messages should happen asynchronous.
   */
  @VisibleForTesting
  public abstract void handleEmails(boolean async);

  protected void dispatchMailProcessor(List<MailMessage> messages, boolean async) {
    for (MailMessage m : messages) {
      if (async) {
        Callable<?> task = () -> {
          try {
            mailProcessor.process(m);
            requestDeletion(m.id());
          } catch (OrmException e) {
            log.error("Mail: Can't process message " + m.id() + " . Won't delete.", e);
          }
          return null;
        };
        workQueue.getDefaultQueue().submit(task);
      } else {
        // Synchronous processing is used only in tests.
        try {
          mailProcessor.process(m);
          requestDeletion(m.id());
        } catch (OrmException e) {
          log.error("Mail: Can't process messages. Won't delete.", e);
        }
      }
    }
  }
}
