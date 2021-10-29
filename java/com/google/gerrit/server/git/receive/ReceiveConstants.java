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

package com.google.gerrit.server.git.receive;

import com.google.common.annotations.VisibleForTesting;

public final class ReceiveConstants {
  public static final String PUSH_OPTION_SKIP_VALIDATION = "skip-validation";

  @VisibleForTesting
  public static final String ONLY_USERS_WITH_TOGGLE_WIP_STATE_PERM_CAN_MODIFY_WIP =
      "only users with Toggle-Wip-State permission can modify Work-in-Progress";

  static final String COMMAND_REJECTION_MESSAGE_FOOTER =
      "Contact an administrator to fix the permissions";

  static final String SAME_CHANGE_ID_IN_MULTIPLE_CHANGES =
      "same Change-Id in multiple changes.\n"
          + "Squash the commits with the same Change-Id or "
          + "ensure Change-Ids are unique for each commit";

  private ReceiveConstants() {}
}
