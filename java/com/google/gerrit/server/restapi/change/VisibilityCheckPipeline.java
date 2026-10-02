// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Checks reviewer candidates concurrently and returns the visible ones in candidate order.
 *
 * <p>At most {@code limit + 2} checks are started up front. Whenever a check finds a candidate to
 * be not visible, the next unchecked candidate is started, so a single slow check does not hold
 * back the rest of the window. Results are returned in candidate order, so the ranking of the
 * candidates is preserved. Processing stops once {@code limit} visible candidates have been
 * returned or the overall timeout has been reached.
 *
 * <p>{@link #get()} must only be called from a single thread.
 */
class VisibilityCheckPipeline {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Decides whether a candidate should be suggested. May block, e.g. on RPCs. */
  @FunctionalInterface
  interface CandidateCheck {
    boolean isVisible(Account.Id id) throws Exception;
  }

  private record CandidateResult(Account.Id id, boolean visible) {}

  private final ImmutableList<Account.Id> candidates;
  private final CandidateCheck check;
  private final Executor executor;
  private final int limit;
  private final Duration timeout;
  private final List<CompletableFuture<CandidateResult>> futures;

  // Guarded by this.
  private int nextCandidateIndex = 0;
  private volatile boolean stopped = false;

  // Only accessed by the thread calling get().
  private boolean started = false;
  private int currentIndex = 0;
  private int visibleCount = 0;
  private long deadlineNanos;

  VisibilityCheckPipeline(
      List<Account.Id> candidates,
      CandidateCheck check,
      Executor executor,
      int limit,
      Duration timeout) {
    this.candidates = ImmutableList.copyOf(candidates);
    this.check = check;
    this.executor = executor;
    this.limit = limit;
    this.timeout = timeout;
    this.futures = new ArrayList<>(Collections.nCopies(this.candidates.size(), null));
  }

  private void start() {
    if (started || stopped) {
      return;
    }
    started = true;
    deadlineNanos = System.nanoTime() + timeout.toNanos();
    if (limit <= 0) {
      return;
    }
    int initialBatchSize = Math.min(limit + 2, candidates.size());
    for (int i = 0; i < initialBatchSize; i++) {
      startCandidate(i);
    }
  }

  /**
   * Returns the next visible candidate in candidate order, or {@code null} if there are no more
   * visible candidates, the limit has been reached, or the timeout has expired.
   */
  @Nullable
  Account.Id get() throws InterruptedException {
    start();
    while (visibleCount < limit && currentIndex < candidates.size()) {
      long remainingNanos = deadlineNanos - System.nanoTime();
      if (remainingNanos <= 0) {
        logger.atFine().log(
            "Visibility check pipeline reached overall timeout of %s; giving up", timeout);
        stop();
        return null;
      }

      CompletableFuture<CandidateResult> future;
      synchronized (this) {
        if (stopped) {
          return null;
        }
        startCandidate(currentIndex);
        future = futures.get(currentIndex);
      }

      CandidateResult result;
      try {
        result = future.get(remainingNanos, TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        logger.atFine().log(
            "Visibility check pipeline reached overall timeout of %s while waiting for candidate"
                + " %s; giving up",
            timeout, candidates.get(currentIndex));
        stop();
        return null;
      } catch (ExecutionException e) {
        logger.atWarning().withCause(e).log(
            "Failed visibility check execution for candidate %s; skipping",
            candidates.get(currentIndex));
        result = new CandidateResult(candidates.get(currentIndex), false);
      } catch (CancellationException e) {
        return null;
      }

      currentIndex++;

      if (result.visible()) {
        visibleCount++;
        if (visibleCount >= limit) {
          logger.atFine().log("Skip results because the limit (%s) has been reached.", limit);
          stop();
        }
        return result.id();
      }
    }
    return null;
  }

  private synchronized void startCandidate(int index) {
    if (stopped || index >= candidates.size() || futures.get(index) != null) {
      return;
    }
    // Reserve the slot before submitting the check. With a direct executor the check (and the
    // startNext() it may trigger) runs synchronously inside execute(), so the slot and
    // nextCandidateIndex must already be updated to avoid checking the same candidate twice.
    CompletableFuture<CandidateResult> future = new CompletableFuture<>();
    futures.set(index, future);
    if (index >= nextCandidateIndex) {
      nextCandidateIndex = index + 1;
    }

    Account.Id id = candidates.get(index);
    try {
      executor.execute(
          () -> {
            CandidateResult result = runCheck(id);
            future.complete(result);
            if (!result.visible()) {
              startNext();
            }
          });
    } catch (RejectedExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Could not schedule visibility check for candidate %s; skipping", id);
      future.complete(new CandidateResult(id, false));
    }
  }

  private CandidateResult runCheck(Account.Id id) {
    if (stopped) {
      return new CandidateResult(id, false);
    }
    try {
      return new CandidateResult(id, check.isVisible(id));
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("Failed visibility check for candidate %s; skipping", id);
      return new CandidateResult(id, false);
    }
  }

  private synchronized void startNext() {
    if (stopped || nextCandidateIndex >= candidates.size()) {
      return;
    }
    startCandidate(nextCandidateIndex);
  }

  /**
   * Stops the pipeline. No new checks are started, and checks that have not started running yet are
   * skipped. Checks that are already running are not interrupted.
   */
  synchronized void stop() {
    stopped = true;
    for (CompletableFuture<CandidateResult> f : futures) {
      if (f != null && !f.isDone()) {
        f.cancel(false);
      }
    }
  }
}
