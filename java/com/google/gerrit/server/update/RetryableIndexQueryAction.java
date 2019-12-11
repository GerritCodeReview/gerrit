// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.github.rholder.retry.RetryListener;
import com.google.common.base.Throwables;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.InternalQuery;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An action to query an index that is executed with retrying.
 *
 * <p>Instances of this class are created via {@link RetryHelper#accountIndexQuery(String,
 * IndexQueryAction)} and {@link RetryHelper#changeIndexQuery(String, IndexQueryAction)}.
 *
 * <p>In contrast to normal {@link RetryableAction.Action}s that are called via {@link
 * RetryableAction} {@link IndexQueryAction}s get a {@link InternalQuery} provided.
 *
 * <p>In addition when an index query action is called any exception that is not an unchecked
 * exception gets wrapped into an {@link StorageException}.
 */
public class RetryableIndexQueryAction<Q extends InternalQuery<?, Q>, T>
    extends RetryableAction<T> {
  @FunctionalInterface
  public interface IndexQueryAction<T, Q> {
    T call(Q internalQuery) throws Exception;
  }

  RetryableIndexQueryAction(
      RetryHelper retryHelper,
      Q internalQuery,
      String actionName,
      IndexQueryAction<T, Q> indexQuery) {
    super(retryHelper, ActionType.INDEX_QUERY, actionName, () -> indexQuery.call(internalQuery));
  }

  @Override
  public RetryableIndexQueryAction<Q, T> retryOn(Predicate<Throwable> exceptionPredicate) {
    super.retryOn(exceptionPredicate);
    return this;
  }

  @Override
  public RetryableIndexQueryAction<Q, T> retryWithTrace(Predicate<Throwable> exceptionPredicate) {
    super.retryWithTrace(exceptionPredicate);
    return this;
  }

  @Override
  public RetryableIndexQueryAction<Q, T> onAutoTrace(Consumer<String> traceIdConsumer) {
    super.onAutoTrace(traceIdConsumer);
    return this;
  }

  @Override
  public RetryableIndexQueryAction<Q, T> listener(RetryListener retryListener) {
    super.listener(retryListener);
    return this;
  }

  @Override
  public RetryableIndexQueryAction<Q, T> defaultTimeoutMultiplier(int multiplier) {
    super.defaultTimeoutMultiplier(multiplier);
    return this;
  }

  @Override
  public T call() {
    try {
      return super.call();
    } catch (Throwable t) {
      Throwables.throwIfUnchecked(t);
      throw new StorageException(t);
    }
  }
}
