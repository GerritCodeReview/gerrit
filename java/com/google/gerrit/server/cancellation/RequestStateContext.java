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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Context that allows to register {@link RequestStateProvider}s.
 *
 * <p>The registered {@link RequestStateProvider}s are stored in {@link ThreadLocal} so that they
 * can be accessed during the request execution (via {@link #getRequestStateProviders()}.
 *
 * <p>On {@link #close()} the {@link RequestStateProvider}s that have been registered by this {@code
 * RequestStateContext} instance are removed from {@link ThreadLocal}.
 *
 * <p>Nesting {@code RequestStateContext}s is possible.
 */
public class RequestStateContext implements AutoCloseable {
  /** The {@link RequestStateProvider}s that have been registered for the thread. */
  private static final ThreadLocal<Set<RequestStateProvider>> threadLocalRequestStateProviders =
      new ThreadLocal<>();

  /**
   * Aborts the current request by throwing a {@link RequestCancelledException} if any of the
   * registered {@link RequestStateProvider}s reports the request as cancelled.
   *
   * @throws RequestCancelledException thrown if the current request is cancelled and should be
   *     aborted
   */
  public static void abortIfCancelled() throws RequestCancelledException {
    getRequestStateProviders()
        .forEach(
            requestStateProvider ->
                requestStateProvider.checkIfCancelled(
                    (reason, message) -> {
                      throw new RequestCancelledException(reason, message);
                    }));
  }

  /** Returns the {@link RequestStateProvider}s that have been registered for the thread. */
  @VisibleForTesting
  static ImmutableSet<RequestStateProvider> getRequestStateProviders() {
    if (threadLocalRequestStateProviders.get() == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(threadLocalRequestStateProviders.get());
  }

  /** Opens a {@code RequestStateContext}. */
  public static RequestStateContext open() {
    return new RequestStateContext();
  }

  /**
   * The {@link RequestStateProvider}s that have been registered by this {@code
   * RequestStateContext}.
   */
  private Set<RequestStateProvider> requestStateProviders = new HashSet<>();

  private RequestStateContext() {}

  /**
   * Registers a {@link RequestStateProvider}.
   *
   * @param requestStateProvider the {@link RequestStateProvider} that should be registered
   * @return the {@code RequestStateContext} instance for chaining calls
   */
  public RequestStateContext addRequestStateProvider(RequestStateProvider requestStateProvider) {
    if (threadLocalRequestStateProviders.get() == null) {
      threadLocalRequestStateProviders.set(new HashSet<>());
    }
    if (threadLocalRequestStateProviders.get().add(requestStateProvider)) {
      requestStateProviders.add(requestStateProvider);
    }
    return this;
  }

  /**
   * Closes this {@code RequestStateContext}.
   *
   * <p>Ensures that all {@link RequestStateProvider}s that have been registered by this {@code
   * RequestStateContext} instance are removed from {@link #threadLocalRequestStateProviders}.
   *
   * <p>If no {@link RequestStateProvider}s remain in {@link #threadLocalRequestStateProviders},
   * {@link #threadLocalRequestStateProviders} is unset.
   */
  @Override
  public void close() {
    if (threadLocalRequestStateProviders.get() != null) {
      requestStateProviders.forEach(
          requestStateProvider ->
              threadLocalRequestStateProviders.get().remove(requestStateProvider));
      if (threadLocalRequestStateProviders.get().isEmpty()) {
        threadLocalRequestStateProviders.remove();
      }
    }
  }
}
