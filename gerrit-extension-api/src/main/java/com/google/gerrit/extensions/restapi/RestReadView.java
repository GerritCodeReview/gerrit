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
 * RestView to read a resource without modification.
 *
 * @param <R> type of resource the view reads.
 */
public interface RestReadView<R extends RestResource> extends RestView<R> {
  /**
   * Process the view operation by reading from the resource.
   *
   * @param resource resource to read.
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
  Object apply(R resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception;
}
