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

import static com.google.gerrit.server.mail.EmailFactories.AUTH_TOKEN_EXPIRED;
import static com.google.gerrit.server.mail.EmailFactories.AUTH_TOKEN_WILL_EXPIRE;

import com.google.common.flogger.FluentLogger;
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
 *   <li>Tokens that expired within the last 24 hours receive an expiration notification
 *   <li>Tokens approaching expiration receive countdown notifications based on the schedule
 *   <li>Only one email is sent per token per day (either expired or countdown notification)
 *   <li>Tokens without expiration dates are skipped
 *   <li>Notifications are sent only if they fall within the 24-hour window (with 1-hour buffer)
 *   <li>The task uses boundary checks to optimize performance when processing many tokens
 *   <li><b>Emails are sent asynchronously</b> via the {@code @SendEmailExecutor} thread pool to
 *       prevent blocking and enable parallel sending
 * </ul>
 *
 * <h3>Email Sending Performance</h3>
 *
 * Email sending is performed asynchronously using the {@code @SendEmailExecutor} thread pool:
 *
 * <ul>
 *   <li>Configure thread pool size via {@code sendemail.threadPoolSize} (default: 1)
 *   <li>Increase pool size for parallel email sending in large deployments
 *   <li>Each email task is submitted to the executor and runs independently
 *   <li>Failed emails are logged but don't block other notifications
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
   *   <li>For each token with an expiration date:
   *       <ul>
   *         <li>If expired within last 24 hours: submits expiration notification task to
   *             SendEmailExecutor
   *         <li>Else, calculates notification schedule and submits countdown notification task if
   *             due
   *       </ul>
   * </ol>
   *
   * <p><b>Email Sending:</b> Emails are sent asynchronously via the {@code @SendEmailExecutor}
   * thread pool. This prevents blocking the scheduled task and enables parallel email sending when
   * the pool size is configured > 1. Configure via {@code sendemail.threadPoolSize} (default: 1).
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

          // Check if token has expired within the last 24 hours
          if (shouldNotifyExpired(checkTime, expirationDate)) {
            logger.atInfo().log(
                "Token %s for account %s has expired on %s. Submitting expiration notification.",
                token.id(), account.account().id(), expirationDate);
            emailFactories
                .createOutgoingEmail(
                    AUTH_TOKEN_EXPIRED,
                    emailFactories.createAuthTokenExpiredEmail(account.account(), token))
                .sendAsync();
          } else if (config.shouldBeNotified(checkTime, expirationDate)) {
            // Check if any notification should be sent today (optimized check)
            logger.atInfo().log(
                "Token %s for account %s is expiring on %s. Submitting notification.",
                token.id(), account.account().id(), expirationDate);
            emailFactories
                .createOutgoingEmail(
                    AUTH_TOKEN_WILL_EXPIRE,
                    emailFactories.createAuthTokenWillExpireEmail(account.account(), token))
                .sendAsync();
          }
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Failed to read accounts from NoteDB", e);
    }
  }

  /**
   * Determines if a token has expired within the last 24 hours and should receive an expiration
   * notification.
   *
   * <p>A token is considered recently expired if its expiration date is:
   *
   * <ul>
   *   <li>At or after the lower bound (now - 24 hours)
   *   <li>Before or equal to now
   * </ul>
   *
   * <p>This ensures that:
   *
   * <ul>
   *   <li>Users are notified on the day their token expires
   *   <li>If the task failed to run, the next run will still notify for tokens that expired in the
   *       last 24 hours
   *   <li>Tokens that expired more than 24 hours ago are not notified (user likely already knows)
   *   <li>Tokens that haven't expired yet are not notified as expired
   * </ul>
   *
   * @param expirationDate the token's expiration date
   * @return true if an expiration notification should be sent, false otherwise
   */
  static boolean shouldNotifyExpired(Instant checkTime, Instant expirationDate) {
    // Token must have expired (at or before now) and within the last 24 hours (at or after
    // lowerBound)
    return !expirationDate.isAfter(checkTime)
        && !expirationDate.isBefore(checkTime.minus(1, ChronoUnit.DAYS));
  }
}
