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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Longs;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** {@link RequestStateProvider} that checks whether a client provided deadline is exceeded. */
public class DeadlineChecker implements RequestStateProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static String SECTION_DEADLINE = "deadline";

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

  public interface Factory {
    DeadlineChecker create(RequestInfo requestInfo, @Nullable String clientProvidedTimeoutValue)
        throws InvalidDeadlineException;

    DeadlineChecker create(
        long start, RequestInfo requestInfo, @Nullable String clientProvidedTimeoutValue)
        throws InvalidDeadlineException;
  }

  private final CancellationMetrics cancellationsMetrics;
  private final RequestInfo requestInfo;
  private final RequestStateProvider.Reason cancellationReason;

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
   * Matching server side deadlines that have been configured as as advisory.
   *
   * <p>If any of these deadlines is exceeded the request is not be aborted. Instead the {@code
   * cancellation/advisory_deadline_count} metric is incremented and a log is written.
   */
  private final ImmutableList<ServerDeadline> advisoryDeadlines;

  /**
   * Creates a {@code ClientProvidedDeadlineChecker}.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param requestInfo the request that was received from a user
   * @param clientProvidedTimeoutValue the timeout value that the client provided, must represent a
   *     numerical time unit (e.g. "5m"), if no time unit is specified milliseconds are assumed, may
   *     be {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  @AssistedInject
  DeadlineChecker(
      @GerritServerConfig Config serverConfig,
      CancellationMetrics cancellationsMetrics,
      @Assisted RequestInfo requestInfo,
      @Assisted @Nullable String clientProvidedTimeoutValue)
      throws InvalidDeadlineException {
    this(
        serverConfig,
        cancellationsMetrics,
        System.nanoTime(),
        requestInfo,
        clientProvidedTimeoutValue);
  }

  /**
   * Creates a {@code ClientProvidedDeadlineChecker}.
   *
   * <p>No deadline is enforced if the client provided deadline value is {@code null} or {@code 0}.
   *
   * @param start the start time of the request in nanoseconds
   * @param requestInfo the request that was received from a user
   * @param clientProvidedTimeoutValue the timeout value that the client provided, must represent a
   *     numerical time unit (e.g. "5m"), if no time unit is specified milliseconds are assumed, may
   *     be {@code null}
   * @throws InvalidDeadlineException thrown if the client provided deadline value cannot be parsed,
   *     e.g. because it uses a bad time unit
   */
  @AssistedInject
  DeadlineChecker(
      @GerritServerConfig Config serverConfig,
      CancellationMetrics cancellationsMetrics,
      @Assisted long start,
      @Assisted RequestInfo requestInfo,
      @Assisted @Nullable String clientProvidedTimeoutValue)
      throws InvalidDeadlineException {
    this.cancellationsMetrics = cancellationsMetrics;
    this.requestInfo = requestInfo;

    ImmutableList<RequestConfig> deadlineConfigs =
        RequestConfig.parseConfigs(serverConfig, SECTION_DEADLINE);
    advisoryDeadlines = getAdvisoryDeadlines(deadlineConfigs, requestInfo);
    Optional<ServerDeadline> serverSideDeadline =
        getServerSideDeadline(deadlineConfigs, requestInfo);
    Optional<Long> clientedProvidedTimeout = parseTimeout(clientProvidedTimeoutValue);
    if (serverSideDeadline.isPresent()) {
      if (clientedProvidedTimeout.isPresent()) {
        logger.atFine().log(
            "client provided deadline (timeout=%sms) overrides server deadline %s (timeout=%sms)",
            TimeUnit.MILLISECONDS.convert(clientedProvidedTimeout.get(), TimeUnit.NANOSECONDS),
            serverSideDeadline.get().id(),
            TimeUnit.MILLISECONDS.convert(
                serverSideDeadline.get().timeout(), TimeUnit.NANOSECONDS));
      } else {
        logger.atFine().log(
            "applying server deadline %s (timeout = %sms)",
            serverSideDeadline.get().id(),
            TimeUnit.MILLISECONDS.convert(
                serverSideDeadline.get().timeout(), TimeUnit.NANOSECONDS));
      }
    }
    this.cancellationReason =
        clientedProvidedTimeout.isPresent()
            ? RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED
            : RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED;
    this.timeout =
        clientedProvidedTimeout.orElse(serverSideDeadline.map(ServerDeadline::timeout).orElse(0L));
    this.deadline = timeout > 0 ? Optional.of(start + timeout) : Optional.empty();
  }

  private Optional<ServerDeadline> getServerSideDeadline(
      ImmutableList<RequestConfig> deadlineConfigs, RequestInfo requestInfo) {
    return deadlineConfigs.stream()
        .filter(deadlineConfig -> deadlineConfig.matches(requestInfo))
        .map(ServerDeadline::readFrom)
        .filter(ServerDeadline::hasTimeout)
        .filter(deadline -> !deadline.isAdvisory())
        // let the stricter deadline (the lower deadline) take precedence
        .sorted(comparing(ServerDeadline::timeout))
        .findFirst();
  }

  private ImmutableList<ServerDeadline> getAdvisoryDeadlines(
      ImmutableList<RequestConfig> deadlineConfigs, RequestInfo requestInfo) {
    return deadlineConfigs.stream()
        .filter(deadlineConfig -> deadlineConfig.matches(requestInfo))
        .map(ServerDeadline::readFrom)
        .filter(ServerDeadline::hasTimeout)
        .filter(ServerDeadline::isAdvisory)
        .collect(toImmutableList());
  }

  @Override
  public void checkIfCancelled(OnCancelled onCancelled) {
    long now = System.nanoTime();

    advisoryDeadlines.forEach(
        advisoryDeadline -> {
          if (now > advisoryDeadline.timeout()) {
            logger.atWarning().log(
                "advisory deadline %s exceeded (%s)",
                advisoryDeadline.id(), TIMEOUT_FORMATTER.apply(advisoryDeadline.timeout()));
            cancellationsMetrics.countAdvisoryDeadline(requestInfo, advisoryDeadline.id());
          }
        });

    if (deadline.isPresent() && now > deadline.get()) {
      onCancelled.onCancel(cancellationReason, TIMEOUT_FORMATTER.apply(timeout));
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
  private static Optional<Long> parseTimeout(@Nullable String timeoutValue)
      throws InvalidDeadlineException {
    if (Strings.isNullOrEmpty(timeoutValue)) {
      return Optional.empty();
    }

    if ("0".equals(timeoutValue)) {
      return Optional.of(0L);
    }

    // If no time unit was specified, assume milliseconds.
    if (Longs.tryParse(timeoutValue) != null) {
      throw new InvalidDeadlineException(String.format("Missing time unit: %s", timeoutValue));
    }

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

  @AutoValue
  abstract static class ServerDeadline {
    abstract String id();

    abstract long timeout();

    abstract boolean isAdvisory();

    boolean hasTimeout() {
      return timeout() > 0;
    }

    static ServerDeadline readFrom(RequestConfig requestConfig) {
      String timeoutValue =
          requestConfig.cfg().getString(requestConfig.section(), requestConfig.id(), "timeout");
      boolean isAdvisory =
          requestConfig
              .cfg()
              .getBoolean(
                  requestConfig.section(),
                  requestConfig.id(),
                  "isAdvisory",
                  /* defaultValue= */ false);
      try {
        Optional<Long> timeout = parseTimeout(timeoutValue);
        return new AutoValue_DeadlineChecker_ServerDeadline(
            requestConfig.id(), timeout.orElse(0L), isAdvisory);
      } catch (InvalidDeadlineException e) {
        logger.atWarning().log(
            "Ignoring invalid deadline configuration %s.%s.timeout: %s",
            requestConfig.section(), requestConfig.id(), e.getMessage());
        return new AutoValue_DeadlineChecker_ServerDeadline(requestConfig.id(), 0, isAdvisory);
      }
    }
  }
}
