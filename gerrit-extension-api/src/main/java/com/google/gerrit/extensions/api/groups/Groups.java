// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.api.groups;

import com.google.gerrit.extensions.restapi.RestApiException;

public interface Groups {
  /**
   * Look up a group by ID.
   * <p>
   * <strong>Note:</strong> This method eagerly reads the group. Methods that
   * mutate the group do not necessarily re-read the group. Therefore, calling a
   * getter method on an instance after calling a mutation method on that same
   * instance is not guaranteed to reflect the mutation. It is not recommended
   * to store references to {@code groupApi} instances.
   *
   * @param id any identifier supported by the REST API, including group name or
   *     UUID.
   * @return API for accessing the group.
   * @throws RestApiException if an error occurred.
   */
  GroupApi id(String id) throws RestApiException;
}
