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

package com.google.gerrit.server.cancellation;

import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import java.util.Optional;
import org.apache.commons.lang3.text.WordUtils;

/** Exception to signal that the current request is cancelled and should be aborted. */
public class RequestCancelledException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Checks whether the given exception was caused by {@link RequestCancelledException}. If yes, the
   * {@link RequestCancelledException} is returned. If not, {@link Optional#empty()} is returned.
   */
  public static Optional<RequestCancelledException> getFromCausalChain(Throwable e) {
    return Throwables.getCausalChain(e).stream()
        .filter(RequestCancelledException.class::isInstance)
        .map(RequestCancelledException.class::cast)
        .findFirst();
  }

  private final RequestStateProvider.Reason cancellationReason;
  private final Optional<String> cancellationMessage;

  /**
   * Create a {@code RequestCancelledException}.
   *
   * @param cancellationReason the reason why the request is cancelled
   * @param cancellationMessage an optional message providing details about the cancellation
   */
  public RequestCancelledException(
      RequestStateProvider.Reason cancellationReason, @Nullable String cancellationMessage) {
    super(createMessage(cancellationReason, cancellationMessage));
    this.cancellationReason = cancellationReason;
    this.cancellationMessage = Optional.ofNullable(cancellationMessage);
  }

  private static String createMessage(
      RequestStateProvider.Reason cancellationReason, @Nullable String message) {
    StringBuilder messageBuilder = new StringBuilder();
    messageBuilder.append(String.format("Request cancelled: %s", cancellationReason.name()));
    if (message != null) {
      messageBuilder.append(String.format(" (%s)", message));
    }
    return messageBuilder.toString();
  }

  /** Returns the reason why the request is cancelled. */
  public RequestStateProvider.Reason getCancellationReason() {
    return cancellationReason;
  }

  /** Returns the cancellation reason as a user-readable string. */
  public String formatCancellationReason() {
    return WordUtils.capitalizeFully(cancellationReason.name().replaceAll("_", " "));
  }

  /**
   * Returns a message providing details about the cancellation, or {@link Optional#empty()} if none
   * is available.
   */
  public Optional<String> getCancellationMessage() {
    return cancellationMessage;
  }
}
