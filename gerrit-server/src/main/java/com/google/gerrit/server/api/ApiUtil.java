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

package com.google.gerrit.server.api;

import com.google.common.base.Throwables;
import com.google.gerrit.extensions.restapi.RestApiException;

/** Static utilities for API implementations. */
public class ApiUtil {
  /** Throw {@code e} if it is a {@link RestApiException} or if it is unchecked. */
  public static void throwIfPossible(Exception e) throws RestApiException {
    Throwables.throwIfInstanceOf(e, RestApiException.class);
    Throwables.throwIfUnchecked(e);
  }

  /**
   * Throw {@code e} directly if it is a {@link RestApiException} or if it is unchecked; otherwise,
   * wrap in a {@link RestApiException} with the given {@code msg}.
   */
  public static void throwRestApiException(String msg, Exception e) throws RestApiException {
    throwIfPossible(e);
    throw new RestApiException(msg, e);
  }

  private ApiUtil() {}
}
