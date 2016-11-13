// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client;

public enum GerritUiExtensionPoint {
  /* ChangeScreen */
  CHANGE_SCREEN_HEADER,
  CHANGE_SCREEN_HEADER_RIGHT_OF_BUTTONS,
  CHANGE_SCREEN_HEADER_RIGHT_OF_POP_DOWNS,
  CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK,
  CHANGE_SCREEN_BELOW_RELATED_INFO_BLOCK,
  CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK,
  CHANGE_SCREEN_HISTORY_RIGHT_OF_BUTTONS,

  /* MyPasswordScreen */
  PASSWORD_SCREEN_BOTTOM,

  /* MyPreferencesScreen */
  PREFERENCES_SCREEN_BOTTOM,

  /* MyProfileScreen */
  PROFILE_SCREEN_BOTTOM,

  /* ProjectInfoScreen */
  PROJECT_INFO_SCREEN_TOP,
  PROJECT_INFO_SCREEN_BOTTOM;

  public enum Key {
    ACCOUNT_INFO,
    CHANGE_INFO,
    PROJECT_NAME,
    REVISION_INFO
  }
}
