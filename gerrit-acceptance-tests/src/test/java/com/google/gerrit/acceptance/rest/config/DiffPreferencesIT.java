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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.config.ConfigUtil.skipField;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;

import org.junit.Test;

import java.lang.reflect.Field;

public class DiffPreferencesIT extends AbstractDaemonTest {

  @Test
  public void GetDiffPreferences() throws Exception {
    DiffPreferencesInfo result = getResult(
        adminRestSession.get("/config/server/preferences.diff"));
    compare(result, DiffPreferencesInfo.defaults());
  }

  public void SetDiffPreferences() throws Exception {
    int defaultLineLength = DiffPreferencesInfo.defaults().lineLength;
    int newLineLength = defaultLineLength + 10;
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = newLineLength;
    DiffPreferencesInfo result = getResult(
        adminRestSession.put("/config/server/preferences.diff", update));
    assertThat(result.lineLength).named("lineLength").isEqualTo(newLineLength);

    result = getResult(
        adminRestSession.get("/config/server/preferences.diff"));
    assertThat(result.lineLength).named("lineLength").isEqualTo(newLineLength);

    DiffPreferencesInfo expectedDPI = DiffPreferencesInfo.defaults();
    expectedDPI.lineLength = newLineLength;
    compare(result, expectedDPI);
  }

  private DiffPreferencesInfo getResult(RestResponse r) {
    r.assertOK();
    return newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
  }

  public void compare(DiffPreferencesInfo n, DiffPreferencesInfo o)
      throws Exception {
    for (Field field : n.getClass().getDeclaredFields()) {
      if (skipField(field)) {
        continue;
      }
      Object nO = field.get(n);
      Object oO = field.get(o);
      Class type = field.getType();
      if ((type == boolean.class || type == Boolean.class) && nO == null) {
        continue;
      }
      assertThat(nO).named(field.getName()).isEqualTo(oO);
    }
  }
}