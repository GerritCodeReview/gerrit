// Copyright (C) 2018 The Android Open Source Project
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
 * RestView that supports accepting input and creating a resource.
 *
 * <p>The input must be supplied as JSON as the body of the HTTP request. Create views can be
 * invoked by the HTTP methods {@code PUT} and {@code POST}.
 *
 * <p>The RestCreateView is only invoked when the parse method of the {@code RestCollection} throws
 * {@link ResourceNotFoundException}, and hence the resource doesn't exist yet.
 *
 * @param <P> type of the parent resource
 * @param <C> type of the child resource that is created
 * @param <I> type of input the JSON parser will parse the input into.
 */
public interface RestCreateView<P extends RestResource, C extends RestResource, I>
    extends RestView<C> {

  /**
   * Process the view operation by creating the resource.
   *
   * @param parentResource parent resource of the resource that should be created
   * @param input input after parsing from request.
   * @return result to return to the client. Use {@link BinaryResult} to avoid automatic conversion
   *     to JSON.
   * @throws RestApiException if the resource creation is rejected
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Object apply(P parentResource, IdString id, I input) throws Exception;
}
