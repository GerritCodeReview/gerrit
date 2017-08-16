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
   * @param resource resource to modify.
   * @param input input after parsing from request.
   * @return result to return to the client. Use {@link BinaryResult} to avoid automatic conversion
   *     to JSON.
   * @throws AuthException the client is not permitted to access this view.
   * @throws BadRequestException the request was incorrectly specified and cannot be handled by this
   *     view.
   * @throws ResourceConflictException the resource state does not permit this view to make the
   *     changes at this time.
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Object apply(R resource, I input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception;
}
