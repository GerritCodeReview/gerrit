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
 * <p>Collections that implement this interface can accept a {@code DELETE} directly on the
 * collection itself.
 */
public interface AcceptsDelete<P extends RestResource> {
  /**
   * Handle deletion of a child resource by DELETE on the collection.
   *
   * @param parent parent collection handle.
   * @param id id of the resource being created (optional).
   * @return a view to perform the deletion.
   * @throws RestApiException the view cannot be constructed.
   */
  <I> RestModifyView<P, I> delete(P parent, IdString id) throws RestApiException;
}
