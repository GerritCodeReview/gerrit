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
import com.google.gerrit.extensions.common.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.Theme;
import com.google.gerrit.extensions.common.DiffPreferencesInfo.Whitespace;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class DiffPreferencesIT extends AbstractDaemonTest {
  @Test
  public void getDiffPreferencesOfNonExistingAccount_NotFound()
      throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/accounts/non-existing/preferences.diff")
        .getStatusCode());
  }

  @Test
  public void getDiffPreferences() throws Exception {
    RestResponse r = adminSession.get("/accounts/" + admin.email
        + "/preferences.diff");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    DiffPreferencesInfo diffPreferences =
        newGson().fromJson(r.getReader(), DiffPreferencesInfo.class);

    assertDiffPreferences(new DiffPreferencesInfo(), diffPreferences);
  }

  @Test
  public void setDiffPreferences() throws Exception {
    DiffPreferencesInfo in = new DiffPreferencesInfo();

    // change all default values
    in.context *= -1;
    in.tabSize *= -1;
    in.lineLength *= -1;
    in.theme = Theme.MIDNIGHT;
    in.ignoreWhitespace = Whitespace.IGNORE_ALL_SPACE;
    in.expandAllComments ^= true;
    in.intralineDifference ^= true;
    in.manualReview ^= true;
    in.retainHeader ^= true;
    in.showLineEndings ^= true;
    in.showTabs ^= true;
    in.showWhitespaceErrors ^= true;
    in.skipDeleted ^= true;
    in.skipUncommented ^= true;
    in.syntaxHighlighting ^= true;
    in.hideTopMenu ^= true;
    in.autoHideDiffTableHeader ^= true;
    in.hideLineNumbers ^= true;
    in.renderEntireFile ^= true;
    in.hideEmptyPane ^= true;

    RestResponse r = adminSession.put("/accounts/" + admin.email
        + "/preferences.diff", in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertDiffPreferences(in, newGson().fromJson(r.getReader(),
        DiffPreferencesInfo.class));
  }

  private static void assertDiffPreferences(DiffPreferencesInfo expected,
      DiffPreferencesInfo actual) {
    assertEquals(expected.context, actual.context);
    assertEquals(expected.tabSize, actual.tabSize);
    assertEquals(expected.lineLength, actual.lineLength);
    assertEquals(expected.expandAllComments, actual.expandAllComments);
    assertEquals(expected.intralineDifference, actual.intralineDifference);
    assertEquals(expected.manualReview, actual.manualReview);
    assertEquals(expected.retainHeader, actual.retainHeader);
    assertEquals(expected.showLineEndings, actual.showLineEndings);
    assertEquals(expected.showTabs, actual.showTabs);
    assertEquals(expected.showWhitespaceErrors, actual.showWhitespaceErrors);
    assertEquals(expected.skipDeleted, actual.skipDeleted);
    assertEquals(expected.skipUncommented, actual.skipUncommented);
    assertEquals(expected.syntaxHighlighting, actual.syntaxHighlighting);
    assertEquals(expected.hideTopMenu, actual.hideTopMenu);
    assertEquals(expected.autoHideDiffTableHeader,
        actual.autoHideDiffTableHeader);
    assertEquals(expected.hideLineNumbers, actual.hideLineNumbers);
    assertEquals(expected.renderEntireFile, actual.renderEntireFile);
    assertEquals(expected.hideEmptyPane, actual.hideEmptyPane);
    assertEquals(expected.ignoreWhitespace, actual.ignoreWhitespace);
    assertEquals(expected.theme, actual.theme);
  }
}
