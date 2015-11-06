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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.Theme;

import org.junit.Test;

import java.io.IOException;

public class EditPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getSetEditPreferences() throws Exception {
    String endPoint = "/accounts/" + admin.email + "/preferences.edit";
    RestResponse r = adminSession.get(endPoint);
    r.assertOK();
    EditPreferencesInfo out = getEditPrefInfo(r);

    assertThat(out.lineLength).isEqualTo(100);
    assertThat(out.tabSize).isEqualTo(8);
    assertThat(out.cursorBlinkRate).isEqualTo(0);
    assertThat(out.hideTopMenu).isNull();
    assertThat(out.showTabs).isTrue();
    assertThat(out.showWhitespaceErrors).isNull();
    assertThat(out.syntaxHighlighting).isTrue();
    assertThat(out.hideLineNumbers).isNull();
    assertThat(out.matchBrackets).isTrue();
    assertThat(out.autoCloseBrackets).isNull();
    assertThat(out.theme).isEqualTo(Theme.DEFAULT);
    assertThat(out.keyMapType).isEqualTo(KeyMapType.DEFAULT);

    // change some default values
    out.lineLength = 80;
    out.tabSize = 4;
    out.cursorBlinkRate = 500;
    out.hideTopMenu = true;
    out.showTabs = false;
    out.showWhitespaceErrors = true;
    out.syntaxHighlighting = false;
    out.hideLineNumbers = true;
    out.matchBrackets = false;
    out.autoCloseBrackets = true;
    out.theme = Theme.TWILIGHT;
    out.keyMapType = KeyMapType.EMACS;

    adminSession.put(endPoint, out).assertNoContent();

    r = adminSession.get(endPoint);
    r.assertOK();
    EditPreferencesInfo info = getEditPrefInfo(r);
    assertEditPreferences(info, out);

    // Partially filled input record
    EditPreferencesInfo in = new EditPreferencesInfo();
    in.tabSize = 42;
    adminSession.put(endPoint, in).assertNoContent();

    r = adminSession.get(endPoint);
    r.assertOK();
    info = getEditPrefInfo(r);
    out.tabSize = in.tabSize;
    assertEditPreferences(info, out);
  }

  private EditPreferencesInfo getEditPrefInfo(RestResponse r)
      throws IOException {
    return newGson().fromJson(r.getReader(),
        EditPreferencesInfo.class);
  }

  private void assertEditPreferences(EditPreferencesInfo out,
      EditPreferencesInfo in) {
    assertThat(out.lineLength).isEqualTo(in.lineLength);
    assertThat(out.tabSize).isEqualTo(in.tabSize);
    assertThat(out.cursorBlinkRate).isEqualTo(in.cursorBlinkRate);
    assertThat(out.hideTopMenu).isEqualTo(in.hideTopMenu);
    assertThat(out.showTabs).isNull();
    assertThat(out.showWhitespaceErrors).isEqualTo(in.showWhitespaceErrors);
    assertThat(out.syntaxHighlighting).isNull();
    assertThat(out.hideLineNumbers).isEqualTo(in.hideLineNumbers);
    assertThat(out.matchBrackets).isNull();
    assertThat(out.autoCloseBrackets).isEqualTo(in.autoCloseBrackets);
    assertThat(out.theme).isEqualTo(in.theme);
    assertThat(out.keyMapType).isEqualTo(in.keyMapType);
  }
}
