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

public enum ChangePermission implements ChangePermissionOrLabel {
  READ(Permission.READ),
  // The change can't be restored if its current patch set is locked.
  //
  // Before checking this permission, the caller should first verify the current patch set of the
  // change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
  RESTORE,
  DELETE,
  // The change can't be abandoned if its current patch set is locked.
  //
  // Before checking this permission, the caller should first verify the current patch set of the
  // change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
  ABANDON(Permission.ABANDON),
  EDIT_ASSIGNEE(Permission.EDIT_ASSIGNEE),
  EDIT_DESCRIPTION,
  EDIT_HASHTAGS(Permission.EDIT_HASHTAGS),
  EDIT_TOPIC_NAME(Permission.EDIT_TOPIC_NAME),
  REMOVE_REVIEWER(Permission.REMOVE_REVIEWER),
  // A new patch set can't be added if the patch set is locked for the change.
  //
  // Before checking this permission, the caller should first verify the current patch set of the
  // change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
  ADD_PATCH_SET(Permission.ADD_PATCH_SET),
  // The change can't be rebased if its current patch set is locked.
  //
  // Before checking this permission, the caller should first verify the current patch set of the
  // change is not locked by calling {@code PatchSetUtil.isPatchSetLocked}.
  REBASE(Permission.REBASE),
  SUBMIT(Permission.SUBMIT),
  SUBMIT_AS(Permission.SUBMIT_AS);

  private final String name;

  ChangePermission() {
    name = null;
  }

  ChangePermission(String name) {
    this.name = name;
  }

  /** @return name used in {@code project.config} permissions. */
  @Override
  public Optional<String> permissionName() {
    return Optional.ofNullable(name);
  }

  @Override
  public String describeForException() {
    return toString().toLowerCase(Locale.US).replace('_', ' ');
  }
}
