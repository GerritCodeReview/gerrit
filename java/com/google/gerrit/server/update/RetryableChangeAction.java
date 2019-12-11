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
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A change action that is executed with retrying.
 *
 * <p>Instances of this class are created via {@link RetryHelper#changeUpdate(String,
 * ChangeAction)}.
 *
 * <p>In contrast to normal {@link RetryableAction.Action}s that are called via {@link
 * RetryableAction} {@link ChangeAction}s get a {@link BatchUpdate.Factory} provided.
 *
 * <p>In addition when a change action is called any exception that is not an unchecked exception
 * and neither {@link UpdateException} nor {@link RestApiException} get wrapped into an {@link
 * UpdateException}.
 */
public class RetryableChangeAction<T> extends RetryableAction<T> {
  @FunctionalInterface
  public interface ChangeAction<T> {
    T call(BatchUpdate.Factory batchUpdateFactory) throws Exception;
  }

  RetryableChangeAction(
      RetryHelper retryHelper,
      BatchUpdate.Factory updateFactory,
      String actionName,
      ChangeAction<T> changeAction) {
    super(
        retryHelper, ActionType.CHANGE_UPDATE, actionName, () -> changeAction.call(updateFactory));
  }

  @Override
  public RetryableChangeAction<T> retryOn(Predicate<Throwable> exceptionPredicate) {
    super.retryOn(exceptionPredicate);
    return this;
  }

  @Override
  public RetryableChangeAction<T> retryWithTrace(Predicate<Throwable> exceptionPredicate) {
    super.retryWithTrace(exceptionPredicate);
    return this;
  }

  @Override
  public RetryableChangeAction<T> onAutoTrace(Consumer<String> traceIdConsumer) {
    super.onAutoTrace(traceIdConsumer);
    return this;
  }

  @Override
  public RetryableChangeAction<T> listener(RetryListener retryListener) {
    super.listener(retryListener);
    return this;
  }

  @Override
  public RetryableChangeAction<T> defaultTimeoutMultiplier(int multiplier) {
    super.defaultTimeoutMultiplier(multiplier);
    return this;
  }

  @Override
  public T call() throws UpdateException, RestApiException {
    try {
      return super.call();
    } catch (Throwable t) {
      Throwables.throwIfUnchecked(t);
      Throwables.throwIfInstanceOf(t, UpdateException.class);
      Throwables.throwIfInstanceOf(t, RestApiException.class);
      throw new UpdateException(t);
    }
  }
}
