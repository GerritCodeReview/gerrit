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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.access.GerritPermission;
import java.util.Optional;

public enum ChangePermission implements ChangePermissionOrLabel {
  READ,
  /**
   * The change can't be restored if its current patch set is locked.
   *
   * <p>Before checking this permission, the caller should first verify the current patch set of the
   * change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
   */
  RESTORE,
  DELETE,
  /**
   * The change can't be abandoned if its current patch set is locked.
   *
   * <p>Before checking this permission, the caller should first verify the current patch set of the
   * change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
   */
  ABANDON,
  EDIT_ASSIGNEE,
  EDIT_DESCRIPTION,
  EDIT_HASHTAGS,
  EDIT_TOPIC_NAME,
  REMOVE_REVIEWER,
  /**
   * A new patch set can't be added if the patch set is locked for the change.
   *
   * <p>Before checking this permission, the caller should first verify the current patch set of the
   * change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
   */
  ADD_PATCH_SET,
  /**
   * The change can't be rebased if its current patch set is locked.
   *
   * <p>Before checking this permission, the caller should first verify the current patch set of the
   * change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
   */
  REBASE(
      /* description= */ null,
      /* hint= */ "change owners and users with the 'Submit' or 'Rebase' permission can rebase"
          + " if they have the 'Push' permission"),
  REBASE_ON_BEHALF_OF_UPLOADER(
      /* description= */ null,
      /* hint= */ "change owners and users with the 'Submit' or 'Rebase' permission can rebase on"
          + " behalf of the uploader"),
  REVERT,
  SUBMIT,
  SUBMIT_AS("submit on behalf of other users"),
  TOGGLE_WORK_IN_PROGRESS_STATE;

  private final String description;
  private final String hint;

  ChangePermission() {
    this.description = null;
    this.hint = null;
  }

  ChangePermission(String description) {
    this.description = requireNonNull(description);
    this.hint = null;
  }

  ChangePermission(@Nullable String description, String hint) {
    this.description = description;
    this.hint = requireNonNull(hint);
  }

  @Override
  public String describeForException() {
    return description != null ? description : GerritPermission.describeEnumValue(this);
  }

  @Override
  public Optional<String> hintForException() {
    return Optional.ofNullable(hint);
  }
}
