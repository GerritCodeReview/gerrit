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
 * Optional interface for {@link RestCollection}.
 * <p>
 * Collections that implement this interface can accept a {@code PUT} directly
 * on the collection itself when no id was given in the path.
 */
public interface AcceptsPut<P extends RestResource> {
  /**
   * Handle creation of a child resource.
   *
   * @param parent parent collection handle.
   * @return a view to perform the creation. The id of the newly created
   *         resource should be determined from the input body.
   * @throws RestApiException the view cannot be constructed.
   */
  <I> RestModifyView<P, I> create(P parent) throws RestApiException;
}
