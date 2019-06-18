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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestResource;

public abstract class RetryingRestModifyView<R extends RestResource, I, O>
    implements RestModifyView<R, I> {
  private final RetryHelper retryHelper;

  protected RetryingRestModifyView(RetryHelper retryHelper) {
    this.retryHelper = retryHelper;
  }

  @Override
  public final O apply(R resource, I input) throws Exception {
    RetryHelper.Options retryOptions =
        RetryHelper.options()
            .caller(getClass())
            .retryWithTrace(t -> !(t instanceof RestApiException))
            .build();
    return retryHelper.execute(
        (updateFactory) -> applyImpl(updateFactory, resource, input), retryOptions);
  }

  protected abstract O applyImpl(BatchUpdate.Factory updateFactory, R resource, I input)
      throws Exception;
}
