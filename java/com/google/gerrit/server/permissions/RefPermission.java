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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.extensions.api.access.GerritPermission;

public enum RefPermission implements GerritPermission {
  READ,
  CREATE,

  /**
   * Before checking this permission, the caller needs to verify the branch is deletable and reject
   * early if the branch should never be deleted. For example, the refs/meta/config branch should
   * never be deleted because deleting this branch would destroy all Gerrit specific metadata about
   * the project, including its access rules. If a project is to be removed from Gerrit, its
   * repository should be removed first.
   */
  DELETE,
  UPDATE,
  FORCE_UPDATE,
  SET_HEAD("set HEAD"),

  FORGE_AUTHOR,
  FORGE_COMMITTER,
  FORGE_SERVER,
  MERGE_REVIEW,
  MERGE_REGULAR,
  /**
   * Before checking this permission, the caller should verify {@code USE_SIGNED_OFF_BY} is false.
   * If it's true, the request should be rejected directly without further check this permission.
   */
  SKIP_VALIDATION,

  /** Create a change to code review a commit. */
  CREATE_CHANGE,

  /** Create a tag. */
  CREATE_TAG,

  /** Create a signed tag. */
  CREATE_SIGNED_TAG,

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
  READ_PRIVATE_CHANGES,

  /** Read access to ref's config section in {@code project.config}. */
  READ_CONFIG("read ref config"),

  /** Write access to ref's config section in {@code project.config}. */
  WRITE_CONFIG("write ref config");

  private final String description;

  RefPermission() {
    this.description = null;
  }

  RefPermission(String description) {
    this.description = requireNonNull(description);
  }

  @Override
  public String describeForException() {
    return description != null ? description : GerritPermission.describeEnumValue(this);
  }
}
