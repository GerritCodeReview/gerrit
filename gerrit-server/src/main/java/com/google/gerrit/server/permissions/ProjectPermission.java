// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import com.google.gerrit.common.data.Permission;
import java.util.Locale;
import java.util.Optional;

public enum ProjectPermission {
  /**
   * Can access at least one reference or change within the repository.
   *
   * <p>Checking this permission instead of {@link #READ} may require filtering to hide specific
   * references or changes, which can be expensive.
   */
  ACCESS,

  /**
   * Can read all references in the repository.
   *
   * <p>This is a stronger form of {@link #ACCESS} where no filtering is required.
   */
  READ(Permission.READ),

  /**
   * Can create at least one reference in the project.
   *
   * <p>This project level permission only validates the user may create some type of reference
   * within the project. The exact reference name must be checked at creation:
   *
   * <pre>permissionBackend
   *    .user(user)
   *    .project(proj)
   *    .ref(ref)
   *    .check(RefPermission.CREATE);
   * </pre>
   */
  CREATE_REF;

  private final String name;

  ProjectPermission() {
    name = null;
  }

  ProjectPermission(String name) {
    this.name = name;
  }

  /** @return name used in {@code project.config} permissions. */
  public Optional<String> permissionName() {
    return Optional.ofNullable(name);
  }

  public String describeForException() {
    return toString().toLowerCase(Locale.US).replace('_', ' ');
  }
}
