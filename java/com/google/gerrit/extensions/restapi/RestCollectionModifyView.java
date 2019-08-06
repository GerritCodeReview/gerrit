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
 * RestView on a RestCollection that supports accepting input.
 *
 * <p>The input must be supplied as JSON as the body of the HTTP request. RestCollectionModifyViews
 * can be invoked by the HTTP methods {@code POST} and {@code DELETE} ({@code DELETE} is only
 * supported on child collections).
 *
 * @param <P> type of the parent resource
 * @param <C> type of the child resource
 * @param <I> type of input the JSON parser will parse the input into.
 */
public interface RestCollectionModifyView<P extends RestResource, C extends RestResource, I>
    extends RestCollectionView<P, C, I> {

  /**
   * Process the modification on the collection resource.
   *
   * <p>The value of the returned response is automatically converted to JSON unless it is a {@link
   * BinaryResult}.
   *
   * <p>The returned response defines the status code that is returned to the client. For
   * RestCollectionModifyViews this is usually {@code 200 OK}, but other 2XX or 3XX status codes are
   * also possible (e.g. {@code 201 Created} if a resource was created, {@code 202 Accepted} if a
   * background task was scheduled, {@code 204 No Content} if no content is returned, {@code 302
   * Found} for a redirect).
   *
   * <p>Throwing a subclass of {@link RestApiException} results in a 4XX response to the client. For
   * any other exception the client will get a {@code 500 Internal Server Error} response.
   *
   * @param parentResource the collection resource on which the modification is done
   * @return response to return to the client
   * @throws Exception the implementation of the view failed. The exception will be logged and HTTP
   *     500 Internal Server Error will be returned to the client.
   */
  Response<?> apply(P parentResource, I input) throws Exception;
}
