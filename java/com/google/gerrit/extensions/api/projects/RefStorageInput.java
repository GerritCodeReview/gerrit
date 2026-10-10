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

/** Input for converting a project's reference storage format. */
public class RefStorageInput {
  /**
   * Target ref storage format ({@link RefStorage#REF_DIRECTORY} or {@link RefStorage#REFTABLE}).
   */
  public RefStorage refStorage;

  /**
   * Whether to keep the previous ref storage as a backup. Defaults to {@code true}. When {@code
   * false}, the superseded ref storage is deleted instead of being renamed with a {@code .old}
   * suffix.
   */
  public Boolean backup;
}
