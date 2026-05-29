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

package com.google.gerrit.extensions.restapi;

/**
 * RestView that supports accepting input and changing a resource.
 *
 * <p>The input must be supplied as JSON as the body of the HTTP request. Modify views can be
 * invoked by any HTTP method that is not {@code GET}, which includes {@code POST}, {@code PUT},
 * {@code DELETE}.
 *
 * @param <R> type of the resource the view modifies.
 * @param <I> type of input the JSON parser will parse the input into.
 */
public interface RestModifyView<R extends RestResource, I> extends RestView<R> {
  /**
   * Process the view operation by altering the resource.
   *
   * <p>The value of the returned response is automatically converted to JSON unless it is a {@link
   * BinaryResult}.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestModifyViews this is usually {@code 200 OK}, but other 2XX or 3XX status codes are also
   * possible (e.g. {@code 202 Accepted} if a background task was scheduled, {@code 204 No Content}
   * if no content is returned, {@code 302 Found} for a redirect).
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param resource resource to modify
   * @param input input after parsing from request
   * @return response to return to the client
   * @throws AuthException the caller is not permitted to access this view.
   * @throws BadRequestException the request was incorrectly specified and cannot be handled by this
   *     view.
   * @throws ResourceConflictException the resource state does not permit this view to make the
   *     changes at this time.
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Response<?> apply(R resource, I input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception;
}
