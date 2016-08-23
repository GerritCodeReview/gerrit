// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.ServletScopes;

import java.util.concurrent.Callable;

/** Propagator for Guice's built-in servlet scope. */
public class GuiceRequestScopePropagator extends RequestScopePropagator {

  @Inject
  GuiceRequestScopePropagator(
      ThreadLocalRequestContext local,
      Provider<RequestScopedReviewDbProvider> dbProviderProvider) {
    super(ServletScopes.REQUEST, local, dbProviderProvider);
  }

  /**
   * @see RequestScopePropagator#wrap(Callable)
   */
  @Override
  protected <T> Callable<T> wrapImpl(Callable<T> callable) {
    return ServletScopes.transferRequest(callable);
  }
}
