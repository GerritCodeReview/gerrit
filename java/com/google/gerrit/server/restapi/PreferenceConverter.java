// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.restapi;

import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.server.account.UserPreferenceFields;
import com.google.gerrit.server.account.UserPreferences;

/** Convert preference API objects into internal representations and vice versa. */
public class PreferenceConverter {

  public GeneralPreferencesInfo general(
      UserPreferences.Default defaults, UserPreferences.User user) {
    UserPreferences.Mixed mixed = UserPreferences.overlayDefaults(defaults, user);
    GeneralPreferencesInfo info = new GeneralPreferencesInfo();
    info.changesPerPage = UserPreferenceFields.General.CHANGES_PER_PAGE.getOrDefault(mixed);
    // TODO(hiesel): Add all fields.
    return info;
  }

  public UserPreferences.Mixed preferences(GeneralPreferencesInfo info) {
    UserPreferences.Mixed.Builder builder = UserPreferences.Mixed.newBuilder();
    builder.add(UserPreferenceFields.General.CHANGES_PER_PAGE, info.changesPerPage);
    // TODO(hiesel): Add all fields.
    return builder.build();
  }
}
