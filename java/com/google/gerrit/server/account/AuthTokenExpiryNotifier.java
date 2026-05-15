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
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbImpl;
import com.google.gerrit.server.config.ScheduleConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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

  private final AccountsNoteDbImpl accounts;
  private final AuthTokenAccessor tokenAccessor;
  private final EmailFactories emailFactories;
  private final AuthTokenExpiryNotificationConfig config;
  private final java.util.concurrent.ExecutorService sendEmailExecutor;

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
      AccountsNoteDbImpl accounts,
      AuthTokenAccessor tokenAccessor,
      EmailFactories emailFactories,
      AuthTokenExpiryNotificationConfig config,
      @SendEmailExecutor ExecutorService sendEmailExecutor) {
    this.accounts = accounts;
    this.tokenAccessor = tokenAccessor;
    this.emailFactories = emailFactories;
    this.config = config;
    this.sendEmailExecutor = sendEmailExecutor;
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

    Instant now = Instant.now();
    Instant oneHourFromNow = now.plus(1, ChronoUnit.HOURS);
    Instant oneDayAgo = now.minus(24, ChronoUnit.HOURS);

    try {
      for (AccountState account : accounts.all()) {
        for (AuthToken token : tokenAccessor.getTokens(account.account().id())) {
          if (token.expirationDate().isEmpty()) {
            continue;
          }
          Instant expirationDate = token.expirationDate().get();
          List<Instant> notificationTimes = config.calculateNotificationTimes(expirationDate);

          // Check if token has expired within the last 24 hours
          if (shouldNotifyExpired(expirationDate, now, oneDayAgo)) {
            logger.atInfo().log(
                "Token %s for account %s has expired on %s. Submitting expiration notification.",
                token.id(), account.account().id(), expirationDate);
            @SuppressWarnings("unused")
            var unused =
                sendEmailExecutor.submit(
                    new AsyncSender(emailFactories, account.account(), token, AUTH_TOKEN_EXPIRED));
          } else if (shouldNotifyForToken(notificationTimes, now, oneDayAgo, oneHourFromNow)) {
            // Check if any notification should be sent today (optimized check)
            logger.atInfo().log(
                "Token %s for account %s is expiring on %s. Submitting notification.",
                token.id(), account.account().id(), expirationDate);
            @SuppressWarnings("unused")
            var unused =
                sendEmailExecutor.submit(
                    new AsyncSender(
                        emailFactories, account.account(), token, AUTH_TOKEN_WILL_EXPIRE));
          }
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Failed to read accounts from NoteDB", e);
    }
  }

  /**
   * Determines if a notification should be sent for a token today.
   *
   * <p>This method uses boundary checks to optimize performance when checking notification
   * schedules:
   *
   * <ol>
   *   <li>If the list is empty, no notification is needed
   *   <li>If the earliest notification is still in the future (beyond upperBound), skip entirely
   *   <li>If the latest notification is already past (before lowerBound), all notifications were
   *       missed
   *   <li>Otherwise, iterate through the list to find a notification due today
   * </ol>
   *
   * <p>This avoids iterating through potentially large lists of notification times when we can
   * quickly determine that no notification is due.
   *
   * @param notificationTimes list of notification times in chronological order (earliest first)
   * @param now the current time
   * @param lowerBound the lower bound for notification window (typically now - 24 hours)
   * @param upperBound the upper bound for notification window (typically now + 1 hour)
   * @return true if a notification should be sent today, false otherwise
   */
  static boolean shouldNotifyForToken(
      List<Instant> notificationTimes, Instant now, Instant lowerBound, Instant upperBound) {
    if (notificationTimes.isEmpty()) {
      return false;
    }

    // Quick check: if the first (earliest) notification is still in the future, nothing to send
    if (notificationTimes.get(0).isAfter(upperBound)) {
      return false;
    }

    // Quick check: if the last (most recent) notification is in the past, all are missed
    if (notificationTimes.get(notificationTimes.size() - 1).isBefore(lowerBound)) {
      return false;
    }

    // At least one notification is within the window, find it
    for (Instant notificationTime : notificationTimes) {
      if (notificationTime.isAfter(lowerBound) && notificationTime.isBefore(upperBound)) {
        return true;
      }
    }

    return false;
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
   * @param now the current time
   * @param lowerBound the lower bound for the notification window (typically now - 24 hours)
   * @return true if an expiration notification should be sent, false otherwise
   */
  static boolean shouldNotifyExpired(Instant expirationDate, Instant now, Instant lowerBound) {
    // Token must have expired (at or before now) and within the last 24 hours (at or after
    // lowerBound)
    return !expirationDate.isAfter(now) && !expirationDate.isBefore(lowerBound);
  }

  /**
   * Asynchronous email sender for auth token expiry notifications.
   *
   * <p>This class implements Runnable to be executed in the SendEmailExecutor thread pool. All
   * fields must be thread-safe (immutable or properly synchronized).
   */
  static class AsyncSender implements Runnable {
    private final EmailFactories emailFactories;
    private final Account account;
    private final AuthToken token;
    private final String messageClass;

    AsyncSender(
        EmailFactories emailFactories, Account account, AuthToken token, String messageClass) {
      this.emailFactories = emailFactories;
      this.account = account;
      this.token = token;
      this.messageClass = messageClass;
    }

    @Override
    public void run() {
      try {
        OutgoingEmail outgoingEmail;
        if (AUTH_TOKEN_EXPIRED.equals(messageClass)) {
          outgoingEmail =
              emailFactories.createOutgoingEmail(
                  AUTH_TOKEN_EXPIRED, emailFactories.createAuthTokenExpiredEmail(account, token));
        } else if (AUTH_TOKEN_WILL_EXPIRE.equals(messageClass)) {
          outgoingEmail =
              emailFactories.createOutgoingEmail(
                  AUTH_TOKEN_WILL_EXPIRE,
                  emailFactories.createAuthTokenWillExpireEmail(account, token));
        } else {
          logger.atSevere().log("Unknown message class: %s", messageClass);
          return;
        }
        outgoingEmail.send();
        logger.atFine().log(
            "Sent %s email for token %s of account %s", messageClass, token.id(), account.id());
      } catch (EmailException e) {
        logger.atSevere().withCause(e).log(
            "Failed to send %s email for token %s of account %s",
            messageClass, token.id(), account.id());
      }
    }

    @Override
    public String toString() {
      return "send-email auth-token " + messageClass;
    }
  }
}
