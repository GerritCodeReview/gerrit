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
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.extensions.client.EditPreferencesInfo;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class EditPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getSetEditPreferences() throws Exception {
    String endPoint = "/accounts/" + admin.email + "/preferences.edit";
    RestResponse r = adminSession.get(endPoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    EditPreferencesInfo out = getEditPrefInfo(r);

    assertThat(out.lineLength).isNull();
    assertThat(out.tabSize).isNull();
    assertThat(out.showTabs).isNull();
    assertThat(out.syntaxHighlighting).isNull();
    assertThat(out.showWhitespaceErrors).isNull();
    assertThat(out.hideLineNumbers).isNull();
    assertThat(out.theme).isNull();

    // change some default values
    out.lineLength = 80;
    out.tabSize = 4;
    out.showTabs = false;
    out.showWhitespaceErrors = false;
    out.syntaxHighlighting = false;
    out.hideLineNumbers = true;
    out.theme = Theme.TWILIGHT;

    r = adminSession.put(endPoint, out);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    r = adminSession.get(endPoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    EditPreferencesInfo info = getEditPrefInfo(r);

    out.showWhitespaceErrors = null;
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
    assertThat(out.showTabs).isEqualTo(in.showTabs);
    assertThat(out.syntaxHighlighting).isEqualTo(in.syntaxHighlighting);
    assertThat(out.hideLineNumbers).isEqualTo(in.hideLineNumbers);
    assertThat(out.theme).isEqualTo(in.theme);
  }
}
