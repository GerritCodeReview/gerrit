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
  public void getDiffPreferences() throws Exception {
    DiffPreferencesInfo result = get();
    assertPrefsEqual(result, DiffPreferencesInfo.defaults());
  }

  @Test
  public void setDiffPreferences() throws Exception {
    int newLineLength = DiffPreferencesInfo.defaults().lineLength + 10;
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = newLineLength;
    DiffPreferencesInfo result = put(update);
    assertThat(result.lineLength).named("lineLength").isEqualTo(newLineLength);

    result = get();
    DiffPreferencesInfo expected = DiffPreferencesInfo.defaults();
    expected.lineLength = newLineLength;
    assertPrefsEqual(result, expected);
  }

  private DiffPreferencesInfo get() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/preferences.diff");
    r.assertOK();
    return newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
  }

  private DiffPreferencesInfo put(DiffPreferencesInfo input) throws Exception {
    RestResponse r = adminRestSession.put(
        "/config/server/preferences.diff", input);
    r.assertOK();
    return newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
  }

  private void assertPrefsEqual(DiffPreferencesInfo actual,
      DiffPreferencesInfo expected) throws Exception {
    for (Field field : actual.getClass().getDeclaredFields()) {
      if (skipField(field)) {
        continue;
      }
      Object actualField = field.get(actual);
      Object expectedField = field.get(expected);
      Class<?> type = field.getType();
      if ((type == boolean.class || type == Boolean.class)
          && actualField == null) {
        continue;
      }
      assertThat(actualField).named(field.getName()).isEqualTo(expectedField);
    }
  }
}
