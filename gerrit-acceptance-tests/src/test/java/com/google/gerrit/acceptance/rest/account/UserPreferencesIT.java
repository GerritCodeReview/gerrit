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
import com.google.gerrit.extensions.common.Theme;
import com.google.gerrit.extensions.common.UserPreferences;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class UserPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getSetUserPreferences() throws Exception {
    String endPoint = "/accounts/" + admin.email + "/preferences.user";
    RestResponse r = adminSession.get(endPoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    UserPreferences out = newGson().fromJson(r.getReader(),
        UserPreferences.class);

    // compare to default values
    assertUserPreferences(out, new UserPreferences());

    // change all defaults values
    out.edit.lineLength = 80;
    out.edit.tabSize = 8;
    out.edit.showLineEndings = false;
    out.edit.showTabs = false;
    out.edit.syntaxHighlighting = false;
    out.edit.hideLineNumbers = true;
    out.edit.theme = Theme.TWILIGHT;

    r = adminSession.put(endPoint, out);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    r = adminSession.get(endPoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);

    assertUserPreferences(out, newGson().fromJson(r.getReader(),
        UserPreferences.class));
  }

  private void assertUserPreferences(UserPreferences out, UserPreferences in) {
    assertThat(out.edit.lineLength).isEqualTo(in.edit.lineLength);
    assertThat(out.edit.tabSize).isEqualTo(in.edit.tabSize);
    assertThat(out.edit.showLineEndings).isEqualTo(in.edit.showLineEndings);
    assertThat(out.edit.showTabs).isEqualTo(in.edit.showTabs);
    assertThat(out.edit.syntaxHighlighting).isEqualTo(in.edit.syntaxHighlighting);
    assertThat(out.edit.hideLineNumbers).isEqualTo(in.edit.hideLineNumbers);
    assertThat(out.edit.theme).isEqualTo(in.edit.theme);
  }
}
