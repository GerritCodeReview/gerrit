// Copyright (C) 2014 The Android Open Source Project
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
 * Optional interface for {@link RestCollection}.
 *
 * <p>This interface is used for 2 purposes:
 *
 * <ul>
 *   <li>to support {@code DELETE} directly on the collection itself
 *   <li>to support {@code DELETE} on a non-existing member of the collection (in order to create
 *       that member)
 * </ul>
 *
 * <p>This interface is not supported for root collections.
 */
public interface AcceptsDelete<P extends RestResource> {
  /**
   * Handle
   *
   * <ul>
   *   <li>{@code DELETE} directly on the collection itself (in this case id is {@code null})
   *   <li>{@code DELETE} on a non-existing member of the collection (in this case id is not {@code
   *       null})
   * </ul>
   *
   * @param parent the collection
   * @param id id of the non-existing collection member for which the {@code DELETE} request is
   *     done, {@code null} if the {@code DELETE} request is done on the collection itself
   * @return a view to handle the {@code DELETE} request
   * @throws RestApiException the view cannot be constructed
   */
  RestModifyView<P, ?> delete(P parent, IdString id) throws RestApiException;
}
