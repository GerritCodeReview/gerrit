// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.gerrit.acceptance.PreferencesAssertionUtil.assertPrefs;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import org.junit.Test;

@NoHttpd
public class EditPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getSetEditPreferences() throws Exception {
    EditPreferencesInfo out = gApi.accounts().id(admin.id().get()).getEditPreferences();

    assertThat(out.lineLength).isEqualTo(100);
    assertThat(out.indentUnit).isEqualTo(2);
    assertThat(out.tabSize).isEqualTo(8);
    assertThat(out.cursorBlinkRate).isEqualTo(0);
    assertThat(out.hideTopMenu).isNull();
    assertThat(out.showTabs).isTrue();
    assertThat(out.showWhitespaceErrors).isNull();
    assertThat(out.syntaxHighlighting).isTrue();
    assertThat(out.hideLineNumbers).isNull();
    assertThat(out.matchBrackets).isTrue();
    assertThat(out.lineWrapping).isNull();
    assertThat(out.indentWithTabs).isNull();
    assertThat(out.autoCloseBrackets).isNull();
    assertThat(out.showBase).isNull();

    // change some default values
    out.lineLength = 80;
    out.indentUnit = 4;
    out.tabSize = 4;
    out.cursorBlinkRate = 500;
    out.hideTopMenu = true;
    out.showTabs = false;
    out.showWhitespaceErrors = true;
    out.syntaxHighlighting = false;
    out.hideLineNumbers = true;
    out.matchBrackets = false;
    out.lineWrapping = true;
    out.indentWithTabs = true;
    out.autoCloseBrackets = true;
    out.showBase = true;

    EditPreferencesInfo info = gApi.accounts().id(admin.id().get()).setEditPreferences(out);

    assertPrefs(info, out);

    // Partially filled input record
    EditPreferencesInfo in = new EditPreferencesInfo();
    in.tabSize = 42;

    info = gApi.accounts().id(admin.id().get()).setEditPreferences(in);

    out.tabSize = in.tabSize;
    assertPrefs(info, out);
  }
}
