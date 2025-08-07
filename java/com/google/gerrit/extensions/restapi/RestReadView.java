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

import javax.servlet.http.HttpServletRequest;

/**
 * RestView to read a resource without modification.
 *
 * <p>RestReadViews are invoked by the HTTP GET method.
 *
 * @param <R> type of resource the view reads.
 */
public interface RestReadView<R extends RestResource> extends RestView<R> {
  /**
   * Process the view operation by reading from the resource.
   *
   * <p>The value of the returned response is automatically converted to JSON unless it is a {@link
   * BinaryResult}.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestReadViews this is usually {@code 200 OK}, but other 2XX or 3XX status codes are also
   * possible (e.g. {@link Response.Redirect} can be returned for {@code 302 Found}).
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param resource resource to read
   * @return response to return to the client
   * @throws AuthException the caller is not permitted to access this view.
   * @throws BadRequestException the request was incorrectly specified and cannot be handled by this
   *     view.
   * @throws ResourceConflictException the resource state does not permit this view to make the
   *     changes at this time.
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Response<?> apply(R resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception;

  /**
   * Process the view operation by reading from the resource.
   *
   * <p>The value of the returned response is automatically converted to JSON unless it is a {@link
   * BinaryResult}.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestReadViews this is usually {@code 200 OK}, but other 2XX or 3XX status codes are also
   * possible (e.g. {@link Response.Redirect} can be returned for {@code 302 Found}).
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param req original request that has been processed by all the applicable Filters
   * @param resource resource to read
   * @return response to return to the client
   * @throws AuthException the caller is not permitted to access this view.
   * @throws BadRequestException the request was incorrectly specified and cannot be handled by this
   *     view.
   * @throws ResourceConflictException the resource state does not permit this view to make the
   *     changes at this time.
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  default Response<?> apply(HttpServletRequest req, R resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    return apply(resource);
  }
}
