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

import static org.junit.Assert.assertEquals;

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
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing/preferences.diff").getStatusCode());
  }

  @Test
  public void getDiffPreferences() throws Exception {
    RestResponse r = adminSession.get("/accounts/" + admin.email + "/preferences.diff");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    DiffPreferencesInfo diffPreferences =
        newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);
    assertDiffPreferences(new AccountDiffPreference(admin.id), diffPreferences);
  }

  private static void assertDiffPreferences(AccountDiffPreference expected, DiffPreferencesInfo actual) {
    assertEquals(expected.getContext(), actual.context);
    assertEquals(expected.isExpandAllComments(), toBoolean(actual.expandAllComments));
    assertEquals(expected.getIgnoreWhitespace(), actual.ignoreWhitespace);
    assertEquals(expected.isIntralineDifference(), toBoolean(actual.intralineDifference));
    assertEquals(expected.getLineLength(), actual.lineLength);
    assertEquals(expected.isManualReview(), toBoolean(actual.manualReview));
    assertEquals(expected.isRetainHeader(), toBoolean(actual.retainHeader));
    assertEquals(expected.isShowLineEndings(), toBoolean(actual.showLineEndings));
    assertEquals(expected.isShowTabs(), toBoolean(actual.showTabs));
    assertEquals(expected.isShowWhitespaceErrors(), toBoolean(actual.showWhitespaceErrors));
    assertEquals(expected.isSkipDeleted(), toBoolean(actual.skipDeleted));
    assertEquals(expected.isSkipUncommented(), toBoolean(actual.skipUncommented));
    assertEquals(expected.isSyntaxHighlighting(), toBoolean(actual.syntaxHighlighting));
    assertEquals(expected.getTabSize(), actual.tabSize);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }
}
