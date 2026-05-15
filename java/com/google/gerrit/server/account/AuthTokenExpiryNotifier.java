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
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Scheduled task that sends notification emails to users when their authentication tokens are
 * approaching expiration.
 *
 * <p>This notifier runs daily at midnight (00:00) and checks all user authentication tokens with
 * expiration dates. Based on the configured notification schedule, it sends reminder emails at
 * regular intervals before tokens expire.
 *
 * <h3>Configuration</h3>
 *
 * The notification schedule is controlled by two configuration parameters:
 *
 * <ul>
 *   <li>{@code auth.tokenExpiryNotificationStartDays} - Days before expiration to send the first
 *       notification (default: 21)
 *   <li>{@code auth.tokenExpiryNotificationIntervalDays} - Days between subsequent notifications
 *       (default: 7)
 * </ul>
 *
 * <h3>Example</h3>
 *
 * With default settings (startDays=21, intervalDays=7), a token expiring in 30 days will receive
 * notifications at:
 *
 * <ul>
 *   <li>21 days before expiration
 *   <li>14 days before expiration
 *   <li>7 days before expiration
 * </ul>
 *
 * <h3>Disabling Notifications</h3>
 *
 * Set either parameter to 0 or a negative value to disable the feature entirely.
 *
 * <h3>Behavior</h3>
 *
 * <ul>
 *   <li>Only one email is sent per token per day
 *   <li>Tokens without expiration dates are skipped
 *   <li>Notifications are sent only if they fall within the 24-hour window (with 1-hour buffer)
 *   <li>The task uses boundary checks to optimize performance when processing many tokens
 * </ul>
 */
@Singleton
public class AuthTokenExpiryNotifier implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final long START_HOURS = 0L;
  private static final long START_MIN = 0L;
  private static final Schedule SCHEDULE =
      ScheduleConfig.Schedule.createOrFail(
          TimeUnit.DAYS.toMillis(1), String.format("%02d:%02d", START_HOURS, START_MIN));

  private final Accounts accounts;
  private final AuthTokenAccessor tokenAccessor;
  private final EmailFactories emailFactories;
  private final AuthTokenExpiryNotificationConfig config;

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

    @Inject
    Lifecycle(WorkQueue queue, AuthTokenExpiryNotifier notifier) {
      this.queue = queue;
      this.notifier = notifier;
    }

    @Override
    public void start() {
      queue.scheduleAtFixedRate(notifier, SCHEDULE);
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  @Inject
  public AuthTokenExpiryNotifier(
      Accounts accounts,
      AuthTokenAccessor tokenAccessor,
      EmailFactories emailFactories,
      AuthTokenExpiryNotificationConfig config) {
    this.accounts = accounts;
    this.tokenAccessor = tokenAccessor;
    this.emailFactories = emailFactories;
    this.config = config;
  }

  /**
   * Executes the daily token expiry notification check.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Checks if notifications are enabled via configuration
   *   <li>Iterates through all user accounts and their authentication tokens
   *   <li>Calculates the notification schedule for each expiring token
   *   <li>Sends an email if a notification is due today (within the 24-hour window)
   * </ol>
   *
   * @throws RuntimeException if accounts cannot be read from NoteDB
   */
  @Override
  public void run() {
    if (!config.isEnabled()) {
      logger.atFine().log("Auth token expiry notifications are disabled.");
      return;
    }

    Instant checkTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

    try {
      for (AccountState account : accounts.all()) {
        for (AuthToken token : tokenAccessor.getTokens(account.account().id())) {
          if (token.expirationDate().isEmpty()) {
            continue;
          }
          Instant expirationDate = token.expirationDate().get();

          // Check if any notification should be sent today (optimized check)
          if (config.shouldBeNotified(checkTime, expirationDate)) {
            logger.atInfo().log(
                "Token %s for account %s is expiring on %s. Sending notification.",
                token.id(), account.account().id(), expirationDate);
            try {
              emailFactories
                  .createOutgoingEmail(
                      AUTH_TOKEN_WILL_EXPIRE,
                      emailFactories.createAuthTokenWillExpireEmail(account.account(), token))
                  .send();
            } catch (EmailException e) {
              logger.atSevere().withCause(e).log(
                  "Failed to send token expiry notification email for token %s of account %s",
                  token.id(), account.account().id());
            }
          }
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Failed to read accounts from NoteDB", e);
    }
  }
}
