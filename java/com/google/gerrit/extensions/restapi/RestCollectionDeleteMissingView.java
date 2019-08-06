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
 * RestView that supports accepting input and deleting a resource that is missing.
 *
 * <p>The RestDeleteMissingView solely exists to support a special case for creating a change edit
 * by deleting a path in the non-existing change edit. This interface should not be used for new
 * REST API's.
 *
 * <p>The input must be supplied as JSON as the body of the HTTP request. Delete views can be
 * invoked by the HTTP method {@code DELETE}.
 *
 * <p>The RestDeleteMissingView is only invoked when the parse method of the {@code RestCollection}
 * throws {@link ResourceNotFoundException}, and hence the resource doesn't exist yet.
 *
 * @param <P> type of the parent resource
 * @param <C> type of the child resource that id deleted
 * @param <I> type of input the JSON parser will parse the input into.
 */
public interface RestCollectionDeleteMissingView<P extends RestResource, C extends RestResource, I>
    extends RestCollectionView<P, C, I> {

  /**
   * Process the view operation by deleting the resource.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestCollectionDeleteMissingViews this is usually {@code 204 No Content} because a resource is
   * deleted, but other 2XX or 3XX status codes are also possible (e.g. {@code 200 OK}, {@code 302
   * Found} for a redirect).
   *
   * <p>The returned response usually does not have any value (status code {@code 204 No Content}).
   * If a value in the returned response is set it is automatically converted to JSON unless it is a
   * {@link BinaryResult}.
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param parentResource parent resource of the resource that should be deleted
   * @param id the ID of the child resource that should be deleted
   * @param input input after parsing from request
   * @return response to return to the client
   * @throws RestApiException if the resource creation is rejected
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Response<?> apply(P parentResource, IdString id, I input) throws Exception;
}
