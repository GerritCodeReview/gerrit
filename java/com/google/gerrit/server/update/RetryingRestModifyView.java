// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Throwables;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RetryingRestModifyView<R extends RestResource, I, O>
    implements RestModifyView<R, I> {
  private final RetryHelper retryHelper;

  protected RetryingRestModifyView(RetryHelper retryHelper) {
    this.retryHelper = retryHelper;
  }

  @Override
  public final Response<O> apply(R resource, I input) throws RestApiException {
    AtomicReference<String> traceId = new AtomicReference<>(null);
    try {
      RetryHelper.Options retryOptions =
          RetryHelper.options()
              .caller(getClass())
              .retryWithTrace(t -> !(t instanceof RestApiException))
              .onAutoTrace(traceId::set)
              .build();
      return retryHelper
          .execute(
              ActionType.REST_REQUEST,
              () -> applyImpl(resource, input),
              retryOptions,
              t -> {
                if (t instanceof UpdateException) {
                  t = t.getCause();
                }
                return t instanceof LockFailureException;
              })
          .traceId(traceId.get());
    } catch (Exception e) {
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      return Response.<O>internalServerError(e).traceId(traceId.get());
    }
  }

  protected abstract Response<O> applyImpl(R resource, I input) throws Exception;
}
