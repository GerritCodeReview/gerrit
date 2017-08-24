// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.AssertUtil.assertPrefs;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import org.junit.Test;

@NoHttpd
public class DiffPreferencesIT extends AbstractDaemonTest {

  @Test
  public void getDiffPreferences() throws Exception {
    DiffPreferencesInfo result = gApi.config().server().getDefaultDiffPreferences();
    assertPrefs(result, DiffPreferencesInfo.defaults());
  }

  @Test
  public void setDiffPreferences() throws Exception {
    int newLineLength = DiffPreferencesInfo.defaults().lineLength + 10;
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = newLineLength;
    DiffPreferencesInfo result = gApi.config().server().setDefaultDiffPreferences(update);
    assertThat(result.lineLength).named("lineLength").isEqualTo(newLineLength);

    result = gApi.config().server().getDefaultDiffPreferences();
    DiffPreferencesInfo expected = DiffPreferencesInfo.defaults();
    expected.lineLength = newLineLength;
    assertPrefs(result, expected);
  }
}
