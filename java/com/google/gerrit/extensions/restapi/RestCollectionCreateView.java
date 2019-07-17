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
public interface RestCollectionCreateView<P extends RestResource, C extends RestResource, I>
    extends RestCollectionView<P, C, I> {

  /**
   * Process the view operation by creating the resource.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestCollectionCreateViews this is usually {code 201 Created} because a resource is created, but
   * other 2XX or 3XX status codes are also possible (e.g. {@link Response.Redirect} can be returned
   * for {@code 302 Found}).
   *
   * <p>The value of the returned response is automatically converted to JSON unless it is a {@link
   * BinaryResult}.
   *
   * <p>Further properties like caching behavior (see {@link CacheControl}) can be optionally set on
   * the returned response.
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param parentResource parent resource of the resource that should be created
   * @param id the ID of the child resource that should be created
   * @param input input after parsing from request.
   * @return response to return to the client
   * @throws RestApiException if the resource creation is rejected
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Response<?> apply(P parentResource, IdString id, I input) throws Exception;
}
