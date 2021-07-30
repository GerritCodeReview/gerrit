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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.config.ConfigUtil;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** {@link RequestStateProvider} that checks whether a client provided deadline is exceeded. */
public class DeadlineChecker implements RequestStateProvider {
  /**
   * Formatter to format a timeout as {@code timeout=<TIMEOUT><TIME_UNIT>}.
   *
   * <p>If the timeout is 1 minute or greater, minutes is used as a time unit. Otherwise
   * milliseconds is just as a time unit.
   */
  public static Function<Long, String> TIMEOUT_FORMATTER =
      timeout -> {
        String formattedTimeout = MILLISECONDS.convert(timeout, NANOSECONDS) + "ms";
        long timeoutInMinutes = MINUTES.convert(timeout, NANOSECONDS);
        if (timeoutInMinutes > 0) {
          formattedTimeout = timeoutInMinutes + "m";
        }
        return String.format("timeout=%s", formattedTimeout);
      };

  /**
   * Timeout in nanoseconds after which the request should be aborted.
   *
   * <p>{@code 0} means that no timeout should be applied.
   */
  private final long timeout;

  /**
   * The deadline in nanoseconds after which a request should be aborted.
   *
   * <p>deadline = start + timeout
   *
   * <p>{@link Optional#empty()} if no timeout was set.
   */
  private final Optional<Long> deadline;

  /**
   * Creates a {@code ClientProvidedDeadlineChecker}.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param clientProvidedTimeoutValue the timeout value that the client provided, must represent a
   *     numerical time unit (e.g. "5m"), if no time unit is specified milliseconds are assumed, may
   *     be {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  public DeadlineChecker(@Nullable String clientProvidedTimeoutValue)
      throws InvalidDeadlineException {
    this(System.nanoTime(), clientProvidedTimeoutValue);
  }

  /**
   * Creates a {@code ClientProvidedDeadlineChecker}.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param start the start time of the request in nanoseconds
   * @param clientProvidedTimeoutValue the timeout value that the client provided, must represent a
   *     numerical time unit (e.g. "5m"), if no time unit is specified milliseconds are assumed, may
   *     be {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  public DeadlineChecker(long start, @Nullable String clientProvidedTimeoutValue)
      throws InvalidDeadlineException {
    this.timeout = parseTimeout(clientProvidedTimeoutValue);
    this.deadline = timeout > 0 ? Optional.of(start + timeout) : Optional.empty();
  }

  @Override
  public void checkIfCancelled(OnCancelled onCancelled) {
    long now = System.nanoTime();
    if (deadline.isPresent() && now > deadline.get()) {
      onCancelled.onCancel(
          Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED, TIMEOUT_FORMATTER.apply(timeout));
    }
  }

  /**
   * Parses the given timeout value.
   *
   * @param timeoutValue the timeout that should be parsed, must represent a numerical time unit
   *     (e.g. "5m"), if no time unit is specified minutes are assumed, may be {@code null}
   * @return the parsed timeout in nanoseconds, {@code 0} if no timeout should be applied
   * @throws InvalidDeadlineException thrown if the provided deadline value cannot be parsed, e.g.
   *     because it uses a bad time unit
   */
  private static long parseTimeout(@Nullable String timeoutValue) throws InvalidDeadlineException {
    if (Strings.isNullOrEmpty(timeoutValue)) {
      return 0;
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
      return parsedTimeout;
    } catch (IllegalArgumentException e) {
      throw new InvalidDeadlineException(e.getMessage(), e);
    }
  }
}
