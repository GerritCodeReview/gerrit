// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.server.mail.EmailFactories.AUTH_TOKEN_WILL_EXPIRE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbImpl;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AuthTokenExpiryNotifier implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long FIRST_NOTIFICATION_BEFORE_EXPIRY = 7L; // 7 days

  private final AccountsNoteDbImpl accounts;
  private final AuthTokenAccessor tokenAccessor;
  private final EmailFactories emailFactories;

  public static Module module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        bind(AuthTokenExpiryNotifier.class);
        listener().to(AuthTokenExpiryNotifier.Lifecycle.class);
      }
    };
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final AuthTokenExpiryNotifier notifier;
    private final Optional<Schedule> schedule;

    @Inject
    Lifecycle(WorkQueue queue, AuthTokenExpiryNotifier notifier) {
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
  public AuthTokenExpiryNotifier(
      AccountsNoteDbImpl accounts, AuthTokenAccessor tokenAccessor, EmailFactories emailFactories) {
    this.accounts = accounts;
    this.tokenAccessor = tokenAccessor;
    this.emailFactories = emailFactories;
  }

  @Override
  public void run() {
    Instant now = Instant.now();
    try {
      for (AccountState account : accounts.all()) {
        for (AuthToken token : tokenAccessor.getTokens(account.account().id())) {
          if (token.expirationDate().isEmpty()) {
            continue;
          }
          Instant expirationDate = token.expirationDate().get();
          if (expirationDate.isBefore(now.plus(FIRST_NOTIFICATION_BEFORE_EXPIRY, ChronoUnit.DAYS))
              && expirationDate.isAfter(
                  now.plus(FIRST_NOTIFICATION_BEFORE_EXPIRY - 1, ChronoUnit.DAYS))) {
            logger.atInfo().log(
                "Token %s for account %s is expiring soon.", token.id(), account.account().id());
            emailFactories
                .createOutgoingEmail(
                    AUTH_TOKEN_WILL_EXPIRE,
                    emailFactories.createAuthTokenWillExpireEmail(account.account(), token))
                .send();
          }
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Failed to read accounts from NoteDB", e);
    } catch (EmailException e) {
      logger.atSevere().withCause(e).log("Failed to send token expiry notification email");
    }
  }
}
