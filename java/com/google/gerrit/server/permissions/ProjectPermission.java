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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.api.access.GerritPermission;
import com.google.gerrit.reviewdb.client.RefNames;

public enum ProjectPermission implements GerritPermission {
  /**
   * Can access at least one reference or change within the repository.
   *
   * <p>Checking this permission instead of {@link #READ} may require filtering to hide specific
   * references or changes, which can be expensive.
   */
  ACCESS("access at least one ref"),

  /**
   * Can read all references in the repository.
   *
   * <p>This is a stronger form of {@link #ACCESS} where no filtering is required.
   */
  READ,

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
  CREATE_REF,

  /**
   * Can create at least one change in the project.
   *
   * <p>This project level permission only validates the user may create a change for some branch
   * within the project. The exact reference name must be checked at creation:
   *
   * <pre>permissionBackend
   *    .user(user)
   *    .project(proj)
   *    .ref(ref)
   *    .check(RefPermission.CREATE_CHANGE);
   * </pre>
   */
  CREATE_CHANGE,

  /** Can run receive pack. */
  RUN_RECEIVE_PACK("run receive-pack"),

  /** Can run upload pack. */
  RUN_UPLOAD_PACK("run upload-pack"),

  /** Allow read access to refs/meta/config. */
  READ_CONFIG("read " + RefNames.REFS_CONFIG),

  /** Allow write access to refs/meta/config. */
  WRITE_CONFIG("write " + RefNames.REFS_CONFIG),

  /** Allow banning commits from Gerrit preventing pushes of these commits. */
  BAN_COMMIT,

  /** Allow accessing the project's reflog. */
  READ_REFLOG,

  /** Can push to at least one reference within the repository. */
  PUSH_AT_LEAST_ONE_REF("push to at least one ref");

  private final String description;

  private ProjectPermission() {
    this.description = null;
  }

  private ProjectPermission(String description) {
    this.description = checkNotNull(description);
  }

  @Override
  public String describeForException() {
    return description != null ? description : GerritPermission.describeEnumValue(this);
  }
}
