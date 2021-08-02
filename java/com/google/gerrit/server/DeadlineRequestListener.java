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

import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Request listener that enforces server side deadlines that have been configured in {@code
 * gerrit.config}.
 *
 * <p>Server side deadlines can be configured by request type, request URI pattern, caller and/or
 * project pattern.
 *
 * <p>Server side deadlines are ignored if a {@link DeadlineChecker} for a client provided deadline
 * has already been registered. This means client provided deadlines override any server side
 * deadline, but for this to work the {@link DeadlineChecker} for the client provided deadline must
 * be registered in a {@link RequestStateContext} before invoking this request listener.
 */
public class DeadlineRequestListener implements RequestListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static String SECTION_DEADLINE = "deadline";

  private final ImmutableList<RequestConfig> deadlineConfigs;

  private RequestStateContext requestStateContext;

  @Inject
  DeadlineRequestListener(@GerritServerConfig Config cfg) {
    this.deadlineConfigs = RequestConfig.parseConfigs(cfg, SECTION_DEADLINE);
  }

  @Override
  public void onRequest(RequestInfo requestInfo) {
    Optional<Deadline> deadline =
        deadlineConfigs.stream()
            .filter(deadlineConfig -> deadlineConfig.matches(requestInfo))
            .map(Deadline::readFrom)
            .filter(Deadline::hasTimeout)
            // let the stricter deadline (the lower deadline) take precedence
            .sorted(comparing(Deadline::timeoutNanos))
            .findFirst();

    if (deadline.isPresent()) {
      if (DeadlineChecker.isClientProvidedDeadlineSet()) {
        logger.atFine().log(
            "ignoring server deadline %s (timeout=%sms) since a client-provided deadline was"
                + " already set",
            deadline.get().id(),
            TimeUnit.MILLISECONDS.convert(deadline.get().timeoutNanos(), TimeUnit.NANOSECONDS));
        return;
      }

      logger.atFine().log(
          "applying deadline %s (timeout = %sms)",
          deadline.get().id(),
          TimeUnit.MILLISECONDS.convert(deadline.get().timeoutNanos(), TimeUnit.NANOSECONDS));
      requestStateContext =
          RequestStateContext.open()
              .addRequestStateProvider(
                  DeadlineChecker.createForServerDeadline(deadline.get().timeoutNanos()));
    }
  }

  @Override
  public void close() {
    if (requestStateContext != null) {
      requestStateContext.close();
      requestStateContext = null;
    }
  }

  @AutoValue
  abstract static class Deadline {
    abstract String id();

    abstract long timeoutNanos();

    boolean hasTimeout() {
      return timeoutNanos() > 0;
    }

    static Deadline readFrom(RequestConfig requestConfig) {
      String timeoutValue =
          requestConfig.cfg().getString(requestConfig.section(), requestConfig.id(), "timeout");
      try {
        Optional<Long> timeoutNanos = DeadlineChecker.parseTimeout(timeoutValue);
        return new AutoValue_DeadlineRequestListener_Deadline(
            requestConfig.id(), timeoutNanos.orElse(0L));
      } catch (InvalidDeadlineException e) {
        logger.atWarning().log(
            "Ignoring invalid deadline configuration %s.%s.timeout: %s",
            requestConfig.section(), requestConfig.id(), e.getMessage());
        return new AutoValue_DeadlineRequestListener_Deadline(requestConfig.id(), 0);
      }
    }
  }
}
