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
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashSet;
import java.util.Optional;
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
 *
 * <p>Currently there is no logic to automatically copy the {@link RequestStateContext} to
 * background threads, but implementing this may be considered in the future. This means that by
 * default we only support cancellation of the main thread, but not of background threads. That's
 * fine as all significant work is being done in the main thread.
 *
 * <p>{@link com.google.gerrit.server.util.RequestContext} is also a context that is available for
 * the time of the request, but it is not suitable to manage registrations of {@link
 * RequestStateProvider}s. Hence {@link RequestStateProvider} registrations are managed by a
 * separate context, which is this class, {@link RequestStateContext}:
 *
 * <ul>
 *   <li>{@link com.google.gerrit.server.util.RequestContext} is an interface that has many
 *       implementations and hence cannot manage a {@link ThreadLocal} state.
 *   <li>{@link com.google.gerrit.server.util.RequestContext} is not an {@link AutoCloseable} and
 *       hence cannot cleanup any {@link ThreadLocal} state on close (turning it into an {@link
 *       AutoCloseable} would require a large refactoring).
 *   <li>Despite the name {@link com.google.gerrit.server.util.RequestContext} is not only used for
 *       requests scopes but also for other scopes that are not a request (e.g. plugin invocations,
 *       email sending, manual scopes).
 *   <li>{@link com.google.gerrit.server.util.RequestContext} is not copied to background and should
 *       not be, but for {@link RequestStateContext} we may consider doing this in the future.
 * </ul>
 */
public class RequestStateContext implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The {@link RequestStateProvider}s that have been registered for the thread. */
  private static final ThreadLocal<Set<RequestStateProvider>> threadLocalRequestStateProviders =
      new ThreadLocal<>();

  private static final ThreadLocal<PerformanceSummaryProvider> performanceSummaryProvider =
      new ThreadLocal<>();

  /** Whether currently a non-cancellable operation is being performed. */
  private static final ThreadLocal<Boolean> inNonCancellableOperation = new ThreadLocal<>();

  /**
   * Aborts the current request by throwing a {@link RequestCancelledException} if any of the
   * registered {@link RequestStateProvider}s reports the request as cancelled.
   *
   * <p>If an atomic operation is currently being performed, request cancellations are ignored and
   * the request doesn't get aborted.
   *
   * @throws RequestCancelledException thrown if the current request is cancelled and should be
   *     aborted
   * @see #startNonCancellableOperation()
   */
  public static void abortIfCancelled() throws RequestCancelledException {
    if (inNonCancellableOperation.get() != null && inNonCancellableOperation.get()) {
      // Do not cancel the request while an atomic operation is being performed.
      return;
    }

    getRequestStateProviders()
        .forEach(
            requestStateProvider ->
                requestStateProvider.checkIfCancelled(
                    (reason, message) -> {
                      logPerformanceSummary();
                      throw new RequestCancelledException(reason, message);
                    }));
  }

  /**
   * Starts a non-cancellable operation.
   *
   * <p>If the request was cancelled while the non-cancellable operation was running, it gets
   * aborted on close of the returned {@link AutoCloseable}.
   *
   * @return {@link AutoCloseable} that finishes the non-cancellable operation on close.
   */
  public static NonCancellableOperationContext startNonCancellableOperation() {
    if (inNonCancellableOperation.get() != null && inNonCancellableOperation.get()) {
      // atomic operation is already in progress
      return () -> {};
    }

    inNonCancellableOperation.set(true);
    return () -> {
      inNonCancellableOperation.remove();
      abortIfCancelled();
    };
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

  private static void logPerformanceSummary() {
    if (performanceSummaryProvider.get() == null) {
      return;
    }

    Optional<String> performanceSummary = performanceSummaryProvider.get().getPerformanceSummary();
    if (performanceSummary.isPresent()) {
      logger.atWarning().log(
          "Performance Summary for cancelled request:\n\n%s", performanceSummary.get());
    }
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
  @CanIgnoreReturnValue
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
   * Sets a provider from which a performance summary can be retrieved for the purpose of logging.
   *
   * @param performanceSummaryProvider the {@link PerformanceSummaryProvider} that should be set
   * @return the {@code RequestStateContext} instance for chaining calls
   */
  @CanIgnoreReturnValue
  public RequestStateContext setPerformanceSummaryProvider(
      PerformanceSummaryProvider performanceSummaryProvider) {
    RequestStateContext.performanceSummaryProvider.set(performanceSummaryProvider);
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

  /**
   * Context for running a non-cancellable operation.
   *
   * <p>While open, the current request cannot be cancelled.
   */
  public interface NonCancellableOperationContext extends AutoCloseable {
    @Override
    void close();
  }
}
