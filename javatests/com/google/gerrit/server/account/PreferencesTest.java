// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.types.DiffPreferencesInfo;
import com.google.gerrit.extensions.types.EditPreferencesInfo;
import com.google.gerrit.extensions.types.GeneralPreferencesInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import org.junit.Test;

public class PreferencesTest {

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  @Test
  public void generalPreferencesRoundTrip() {
    GeneralPreferencesInfo original = GeneralPreferencesInfo.defaults();
    assertThat(GSON.toJson(original))
        .isEqualTo(GSON.toJson(Preferences.General.fromInfo(original).toInfo()));
  }

  @Test
  public void diffPreferencesRoundTrip() {
    DiffPreferencesInfo original = DiffPreferencesInfo.defaults();
    assertThat(GSON.toJson(original))
        .isEqualTo(GSON.toJson(Preferences.Diff.fromInfo(original).toInfo()));
  }

  @Test
  public void editPreferencesRoundTrip() {
    EditPreferencesInfo original = EditPreferencesInfo.defaults();
    assertThat(GSON.toJson(original))
        .isEqualTo(GSON.toJson(Preferences.Edit.fromInfo(original).toInfo()));
  }
}
