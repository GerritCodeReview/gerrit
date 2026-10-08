// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.mail.EmailFactories.SSH_KEY_EXPIRED;
import static com.google.gerrit.server.mail.EmailFactories.SSH_KEY_WILL_EXPIRE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Sends emails to the owner of an SSH key when the key will expire soon and after it expired.
 *
 * <p>The notifier runs once a day. It notifies about the keys that expire in 6 to 7 days and about
 * the keys that expired during the last 24 hours, so that every key is notified only once for each
 * of the two events.
 */
@Singleton
public class SshKeyExpiryNotifier implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long FIRST_NOTIFICATION_BEFORE_EXPIRY = 7L; // 7 days

  private final Accounts accounts;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final EmailFactories emailFactories;

  public static Module module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        bind(SshKeyExpiryNotifier.class);
        listener().to(SshKeyExpiryNotifier.Lifecycle.class);
      }
    };
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final SshKeyExpiryNotifier notifier;
    private final Optional<Schedule> schedule;

    @Inject
    Lifecycle(WorkQueue queue, SshKeyExpiryNotifier notifier) {
      this.queue = queue;
      this.notifier = notifier;
      schedule = ScheduleConfig.Schedule.create(TimeUnit.DAYS.toMillis(1), "00:00");
    }

    @Override
    public void start() {
      if (schedule.isPresent()) {
        queue.scheduleAtFixedRate(notifier, schedule.get());
      }
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  @Inject
  public SshKeyExpiryNotifier(
      Accounts accounts,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      EmailFactories emailFactories) {
    this.accounts = accounts;
    this.authorizedKeys = authorizedKeys;
    this.emailFactories = emailFactories;
  }

  @Override
  public void run() {
    Instant now = Instant.now();
    try {
      for (AccountState account : accounts.all()) {
        try {
          for (AccountSshKey key : authorizedKeys.getKeys(account.account().id())) {
            if (!key.valid() || key.expirationDate().isEmpty()) {
              continue;
            }
            Instant expiration = key.expirationDate().get();
            if (isInWindow(expiration, now.minus(1, ChronoUnit.DAYS), now)) {
              send(
                  SSH_KEY_EXPIRED,
                  emailFactories.createSshKeyExpiredEmail(account.account(), key),
                  account,
                  key);
            } else if (isInWindow(
                expiration,
                now.plus(FIRST_NOTIFICATION_BEFORE_EXPIRY - 1, ChronoUnit.DAYS),
                now.plus(FIRST_NOTIFICATION_BEFORE_EXPIRY, ChronoUnit.DAYS))) {
              send(
                  SSH_KEY_WILL_EXPIRE,
                  emailFactories.createSshKeyWillExpireEmail(account.account(), key),
                  account,
                  key);
            }
          }
        } catch (IOException | ConfigInvalidException e) {
          logger.atSevere().withCause(e).log(
              "Failed to read the SSH keys of account %s", account.account().id());
        }
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to read accounts to notify about SSH keys");
    }
  }

  /** Whether {@code instant} is after {@code start} and before {@code end}. */
  private static boolean isInWindow(Instant instant, Instant start, Instant end) {
    return instant.isAfter(start) && instant.isBefore(end);
  }

  private void send(
      String messageClass, EmailDecorator decorator, AccountState account, AccountSshKey key) {
    logger.atInfo().log(
        "Sending %s notification for SSH key %d of account %s.",
        messageClass, key.seq(), account.account().id());
    try {
      emailFactories.createOutgoingEmail(messageClass, decorator).send();
    } catch (EmailException e) {
      logger.atSevere().withCause(e).log(
          "Failed to send %s notification email for SSH key %d of account %s",
          messageClass, key.seq(), account.account().id());
    }
  }
}
