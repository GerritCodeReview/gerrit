// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.server.account.GetDiffPreferences.DiffPreferencesInfo;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class GetDiffPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getDiffPreferencesOfNonExistingAccount_NotFound()
      throws Exception {
    assertThat(adminSession.get("/accounts/non-existing/preferences.diff").getStatusCode())
      .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void getDiffPreferences() throws Exception {
    RestResponse r = adminSession.get("/accounts/" + admin.email + "/preferences.diff");
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    DiffPreferencesInfo diffPreferences =
        newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
    assertDiffPreferences(new AccountDiffPreference(admin.id), diffPreferences);
  }

  private static void assertDiffPreferences(AccountDiffPreference expected, DiffPreferencesInfo actual) {
    assertThat(actual.context).isEqualTo(expected.getContext());
    assertThat(toBoolean(actual.expandAllComments)).isEqualTo(expected.isExpandAllComments());
    assertThat(actual.ignoreWhitespace).isEqualTo(expected.getIgnoreWhitespace());
    assertThat(toBoolean(actual.intralineDifference)).isEqualTo(expected.isIntralineDifference());
    assertThat(actual.lineLength).isEqualTo(expected.getLineLength());
    assertThat(toBoolean(actual.manualReview)).isEqualTo(expected.isManualReview());
    assertThat(toBoolean(actual.retainHeader)).isEqualTo(expected.isRetainHeader());
    assertThat(toBoolean(actual.showLineEndings)).isEqualTo(expected.isShowLineEndings());
    assertThat(toBoolean(actual.showTabs)).isEqualTo(expected.isShowTabs());
    assertThat(toBoolean(actual.showWhitespaceErrors)).isEqualTo(expected.isShowWhitespaceErrors());
    assertThat(toBoolean(actual.skipDeleted)).isEqualTo(expected.isSkipDeleted());
    assertThat(toBoolean(actual.skipUncommented)).isEqualTo(expected.isSkipUncommented());
    assertThat(toBoolean(actual.syntaxHighlighting)).isEqualTo(expected.isSyntaxHighlighting());
    assertThat(actual.tabSize).isEqualTo(expected.getTabSize());
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
