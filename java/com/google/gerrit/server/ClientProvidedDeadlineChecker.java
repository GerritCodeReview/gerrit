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

import com.google.common.primitives.Longs;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.config.ConfigUtil;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** {@link RequestStateProvider} that checks whether a client provided deadline is exceeded. */
public class ClientProvidedDeadlineChecker implements RequestStateProvider {
  private final long timeout;
  private final Optional<Long> deadline;

  /**
   * Creates a {@code ClientProvidedDeadlineChecker}.
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
  public ClientProvidedDeadlineChecker(long start, @Nullable String clientProvidedDeadlineValue)
      throws InvalidDeadlineException {
    // If no time unit was specified, assume milliseconds.
    clientProvidedDeadlineValue =
        clientProvidedDeadlineValue != null && Longs.tryParse(clientProvidedDeadlineValue) != null
            ? clientProvidedDeadlineValue + "ms"
            : clientProvidedDeadlineValue;

    try {
      this.timeout =
          clientProvidedDeadlineValue != null
              ? ConfigUtil.getTimeUnit(
                  clientProvidedDeadlineValue, /* defaultValue= */ 0, TimeUnit.NANOSECONDS)
              : 0;
      this.deadline = timeout > 0 ? Optional.of(start + timeout) : Optional.empty();
    } catch (IllegalArgumentException e) {
      throw new InvalidDeadlineException(e.getMessage(), e);
    }
  }

  @Override
  public void checkIfCancelled(OnCancelled onCancelled) {
    long now = System.nanoTime();
    if (deadline.isPresent() && now > deadline.get()) {
      onCancelled.onCancel(
          Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED, String.format("timeout=%s", formatTimeout()));
    }
  }

  private String formatTimeout() {
    long timeoutInMinutes = TimeUnit.MINUTES.convert(timeout, TimeUnit.NANOSECONDS);
    if (timeoutInMinutes > 0) {
      return timeoutInMinutes + "m";
    }
    return TimeUnit.MILLISECONDS.convert(timeout, TimeUnit.NANOSECONDS) + "ms";
  }
}
