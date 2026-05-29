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
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.ExceptionHook.Status;
import com.google.gerrit.server.ExceptionHookImpl;
import com.google.gerrit.server.auth.AuthException;
import java.util.Optional;

/** Static utilities for API implementations. */
public class ApiUtil {
  /**
   * Convert an exception encountered during API execution to a {@link RestApiException}.
   *
   * @param msg message to be used in the case where a new {@code RestApiException} is wrapped
   *     around {@code e}.
   * @param e exception being handled.
   * @return {@code e} if it is already a {@code RestApiException}, otherwise a new {@code
   *     RestApiException} wrapped around {@code e}.
   * @throws RuntimeException if {@code e} is a runtime exception, it is rethrown as-is.
   */
  public static RestApiException asRestApiException(String msg, Exception e)
      throws RuntimeException {
    e = mapExceptionUsingDefaultExceptionHook(e);
    Throwables.throwIfUnchecked(e);
    return e instanceof RestApiException ? (RestApiException) e : RestApiException.wrap(msg, e);
  }

  private static Exception mapExceptionUsingDefaultExceptionHook(Exception originalException) {
    ExceptionHookImpl impl = new ExceptionHookImpl();
    Optional<Status> status = impl.getStatus(originalException);
    if (status.isPresent()) {
      if (status.get().statusCode() == 400) {
        return new BadRequestException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 403) {
        return new AuthException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 404) {
        return new ResourceNotFoundException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 405) {
        return new MethodNotAllowedException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 409) {
        return new ResourceConflictException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 412) {
        return new PreconditionFailedException(originalException.getMessage(), originalException);
      } else if (status.get().statusCode() == 422) {
        return new UnprocessableEntityException(originalException.getMessage(), originalException);
      }
    }
    return originalException;
  }

  private ApiUtil() {}
}
