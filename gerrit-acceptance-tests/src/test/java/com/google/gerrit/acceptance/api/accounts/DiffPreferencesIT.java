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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.config.ConfigUtil.skipField;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Theme;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@NoHttpd
public class DiffPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getDiffPreferences() throws Exception {
    DiffPreferencesInfo d = DiffPreferencesInfo.defaults();
    DiffPreferencesInfo o = gApi.accounts()
        .id(admin.getId().toString())
        .getDiffPreferences();
    assertPrefs(o, d);
  }

  @Test
  public void setDiffPreferences() throws Exception {
    DiffPreferencesInfo i = DiffPreferencesInfo.defaults();

    // change all default values
    i.context *= -1;
    i.tabSize *= -1;
    i.lineLength *= -1;
    i.cursorBlinkRate = 500;
    i.theme = Theme.MIDNIGHT;
    i.ignoreWhitespace = Whitespace.IGNORE_ALL;
    i.expandAllComments ^= true;
    i.intralineDifference ^= true;
    i.manualReview ^= true;
    i.retainHeader ^= true;
    i.showLineEndings ^= true;
    i.showTabs ^= true;
    i.showWhitespaceErrors ^= true;
    i.skipDeleted ^= true;
    i.skipUnchanged ^= true;
    i.skipUncommented ^= true;
    i.syntaxHighlighting ^= true;
    i.hideTopMenu ^= true;
    i.autoHideDiffTableHeader ^= true;
    i.hideLineNumbers ^= true;
    i.renderEntireFile ^= true;
    i.hideEmptyPane ^= true;
    i.matchBrackets ^= true;

    DiffPreferencesInfo o = gApi.accounts()
        .id(admin.getId().toString())
        .setDiffPreferences(i);
    assertPrefs(o, i);

    // Partially fill input record
    i = new DiffPreferencesInfo();
    i.tabSize = 42;
    DiffPreferencesInfo a = gApi.accounts()
        .id(admin.getId().toString())
        .setDiffPreferences(i);
    assertPrefs(a, o, "tabSize");
    assertThat(a.tabSize).isEqualTo(42);
  }

  private static void assertPrefs(DiffPreferencesInfo actual,
      DiffPreferencesInfo expected, String... fieldsToExclude)
          throws IllegalArgumentException, IllegalAccessException {
    List<String> exludedFields = Arrays.asList(fieldsToExclude);
    for (Field field : actual.getClass().getDeclaredFields()) {
      if (exludedFields.contains(field.getName()) || skipField(field)) {
        continue;
      }
      Object actualVal = field.get(actual);
      Object expectedVal = field.get(expected);
      if (field.getType().isAssignableFrom(Boolean.class)) {
        if (actualVal == null) {
          actualVal = false;
        }
        if (expectedVal == null) {
          expectedVal = false;
        }
      }
      assertWithMessage("field " + field.getName()).that(actualVal)
          .isEqualTo(expectedVal);
    }
  }
}
