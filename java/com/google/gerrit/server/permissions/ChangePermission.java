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

import com.google.common.base.CaseFormat;
import com.google.gerrit.common.data.Permission;

public enum ChangePermission implements ChangePermissionOrLabel {
  READ(Permission.READ),
  RESTORE,
  DELETE,
  ABANDON(Permission.ABANDON),
  EDIT_ASSIGNEE(Permission.EDIT_ASSIGNEE),
  EDIT_DESCRIPTION,
  EDIT_HASHTAGS(Permission.EDIT_HASHTAGS),
  EDIT_TOPIC_NAME(Permission.EDIT_TOPIC_NAME),
  REMOVE_REVIEWER(Permission.REMOVE_REVIEWER),
  ADD_PATCH_SET(Permission.ADD_PATCH_SET),
  REBASE(Permission.REBASE),
  SUBMIT(Permission.SUBMIT),
  SUBMIT_AS(Permission.SUBMIT_AS);

  private final String name;

  ChangePermission() {
    name = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
  }

  ChangePermission(String name) {
    this.name = name;
  }

  @Override
  public String permissionName() {
    return name;
  }
}
