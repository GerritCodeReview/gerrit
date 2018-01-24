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
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum RefPermission {
  READ(Permission.READ),
  CREATE(Permission.CREATE),
  DELETE(Permission.DELETE),
  UPDATE(Permission.PUSH),
  FORCE_UPDATE,
  SET_HEAD,

  FORGE_AUTHOR(Permission.FORGE_AUTHOR),
  FORGE_COMMITTER(Permission.FORGE_COMMITTER),
  FORGE_SERVER(Permission.FORGE_SERVER),
  MERGE,
  SKIP_VALIDATION,

  /** Create a change to code review a commit. */
  CREATE_CHANGE,

  /** Create a tag. */
  CREATE_TAG(Permission.CREATE_TAG),

  /** Create a signed tag. */
  CREATE_SIGNED_TAG(Permission.CREATE_SIGNED_TAG),

  /**
   * Creates changes, then also immediately submits them during {@code push}.
   *
   * <p>This is similar to {@link #UPDATE} except it constructs changes first, then submits them
   * according to the submit strategy, which may include cherry-pick or rebase. By creating changes
   * for each commit, automatic server side rebase, and post-update review are enabled.
   */
  UPDATE_BY_SUBMIT,

  /**
   * Can read all private changes on the ref. Typically granted to CI systems if they should run on
   * private changes.
   */
  READ_PRIVATE_CHANGES(Permission.VIEW_PRIVATE_CHANGES),

  /** Read access to ref's config section in {@code project.config}. */
  READ_CONFIG,

  /** Write access to ref's config section in {@code project.config}. */
  WRITE_CONFIG;

  private final String name;

  RefPermission() {
    name = null;
  }

  RefPermission(String name) {
    this.name = name;
  }

  /** @return name used in {@code project.config} permissions. */
  public Optional<String> permissionName() {
    return Optional.ofNullable(name);
  }

  public String describeForException() {
    return toString().toLowerCase(Locale.US).replace('_', ' ');
  }

  /** @return the enum constant for a given permission name if present. */
  public static Optional<RefPermission> fromName(String name) {
    return Arrays.stream(RefPermission.values()).filter(p -> name.equals(p.name)).findFirst();
  }
}
