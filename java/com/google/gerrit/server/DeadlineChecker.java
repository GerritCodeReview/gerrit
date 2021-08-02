// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.config.ConfigUtil;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** {@link RequestStateProvider} that checks whether a client provided deadline is exceeded. */
public class DeadlineChecker implements RequestStateProvider {
  /**
   * Creates a {@code DeadlineChecker} for a client-provided deadline.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param clientProvidedDeadlineValue the deadline value that the client provided, must represent
   *     a numerical time unit (e.g. "5m"), if no time unit is specified minutes are assumed, may be
   *     {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  public static DeadlineChecker createForClientProvidedDeadline(
      @Nullable String clientProvidedDeadlineValue) throws InvalidDeadlineException {
    return createForClientProvidedDeadline(System.nanoTime(), clientProvidedDeadlineValue);
  }

  /**
   * Creates a {@code DeadlineChecker} for a client-provided deadline.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param start the start time of the request in nanoseconds
   * @param clientProvidedDeadlineValue the deadline value that the client provided, must represent
   *     a numerical time unit (e.g. "5m"), if no time unit is specified minutes are assumed, may be
   *     {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  public static DeadlineChecker createForClientProvidedDeadline(
      long start, @Nullable String clientProvidedDeadlineValue) throws InvalidDeadlineException {
    return new DeadlineChecker(
        RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED,
        start,
        clientProvidedDeadlineValue);
  }

  /**
   * Creates a {@code DeadlineChecker} for a deadline that is configured on server side.
   *
   * <p>No deadline is enforced if the provided deadline is {@code 0}.
   *
   * @param timeoutNanos the timeout in nanoseconds
   */
  public static DeadlineChecker createForServerDeadline(long timeoutNanos) {
    return new DeadlineChecker(
        RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, System.nanoTime(), timeoutNanos);
  }

  /**
   * Parses the given timeout value.
   *
   * @param timeoutValue the timeout that should be parsed, must represent a numerical time unit
   *     (e.g. "5m"), if no time unit is specified minutes are assumed, may be {@code null}
   * @return the parsed timeout in nanoseconds, {@code 0} if no timeout should be applied, {@link
   *     Optional#empty()} if no timeout is set (the provided timeout value is {@code null} or
   *     empty)
   * @throws InvalidDeadlineException thrown if the provided deadline value cannot be parsed, e.g.
   *     because it uses a bad time unit
   */
  public static Optional<Long> parseTimeout(String timeoutValue) throws InvalidDeadlineException {
    if (Strings.isNullOrEmpty(timeoutValue)) {
      return Optional.empty();
    }

    // If no time unit was specified, assume milliseconds.
    timeoutValue =
        timeoutValue != null && Longs.tryParse(timeoutValue) != null
            ? timeoutValue + "ms"
            : timeoutValue;

    try {
      long parsedTimeout =
          ConfigUtil.getTimeUnit(timeoutValue, /* defaultValue= */ -1, TimeUnit.NANOSECONDS);
      if (parsedTimeout == -1) {
        throw new InvalidDeadlineException(String.format("Invalid value: %s", timeoutValue));
      }
      return Optional.of(parsedTimeout);
    } catch (IllegalArgumentException e) {
      throw new InvalidDeadlineException(e.getMessage(), e);
    }
  }

  /**
   * Checks whether a {@link DeadlineChecker} for a client provided deadline has already been
   * registered.
   */
  public static boolean isClientProvidedDeadlineSet() {
    return RequestStateContext.getRequestStateProviders().stream()
        .filter(DeadlineChecker.class::isInstance)
        .map(DeadlineChecker.class::cast)
        .filter(
            deadlineChecker ->
                RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED.equals(
                    deadlineChecker.cancellationReason))
        .filter(deadlineChecker -> deadlineChecker.timeout.isPresent())
        .findAny()
        .isPresent();
  }

  private final RequestStateProvider.Reason cancellationReason;
  private final Optional<Long> timeout;
  private final Optional<Long> deadline;

  private DeadlineChecker(
      RequestStateProvider.Reason cancellationReason, long start, @Nullable String timeoutValue)
      throws InvalidDeadlineException {
    this.cancellationReason = cancellationReason;
    this.timeout = parseTimeout(timeoutValue);
    this.deadline = timeout.filter(t -> t > 0).map(t -> start + t);
  }

  private DeadlineChecker(
      RequestStateProvider.Reason cancellationReason, long start, long timeoutNanos) {
    this.cancellationReason = cancellationReason;
    this.timeout = Optional.of(timeoutNanos);
    this.deadline = timeout.filter(t -> t > 0).map(t -> start + t);
  }

  @Override
  public void checkIfCancelled(OnCancelled onCancelled) {
    long now = System.nanoTime();
    if (deadline.isPresent() && now > deadline.get()) {
      onCancelled.onCancel(cancellationReason, String.format("timeout=%s", formatTimeout()));
    }
  }

  private String formatTimeout() {
    long timeoutInMinutes = TimeUnit.MINUTES.convert(timeout.get(), TimeUnit.NANOSECONDS);
    if (timeoutInMinutes > 0) {
      return timeoutInMinutes + "m";
    }
    return TimeUnit.MILLISECONDS.convert(timeout.get(), TimeUnit.NANOSECONDS) + "ms";
  }
}
