// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.client.RefStorage;
import com.google.gerrit.extensions.restapi.DefaultInput;

/** Input for converting a project's reference storage format. */
public class RefStorageInput {
  /**
   * Target ref storage format ({@link RefStorage#FILES} or {@link RefStorage#REFTABLE}). Bound as
   * the default input, so the request body may also be a bare JSON string such as {@code
   * "REFTABLE"}.
   */
  @DefaultInput public String refStorage;

  /**
   * Whether to keep the previous ref storage as a backup. Defaults to {@code true}. When {@code
   * true}, the superseded ref storage is retained (converting from files renames it with a {@code
   * .old} suffix; converting from reftable leaves the {@code reftable} directory in place). When
   * {@code false}, it is deleted.
   */
  public Boolean backup;
}
