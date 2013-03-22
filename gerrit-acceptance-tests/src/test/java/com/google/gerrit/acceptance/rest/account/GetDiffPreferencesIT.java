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
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GetDiffPreferencesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> dbProvider;

  private TestAccount admin;
  private RestSession session;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(admin);
  }

  @Test
  public void getDiffPreferencesOfNonExistingAccount_NotFound()
      throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        session.get("/accounts/non-existing/preferences.diff").getStatusCode());
  }

  @Test
  public void getDiffPreferences() throws IOException, OrmException {
    RestResponse r = session.get("/accounts/" + admin.email + "/preferences.diff");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    DiffPreferencesInfo diffPreferences =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<DiffPreferencesInfo>() {}.getType());
    ReviewDb db = dbProvider.open();
    try {
      assertDiffPreferences(db.accountDiffPreferences().get(admin.id), diffPreferences);
    } finally {
      db.close();
    }
  }

  private static void assertDiffPreferences(AccountDiffPreference expected, DiffPreferencesInfo actual) {
    assertEquals(expected.getContext(), actual.context);
    assertEquals(expected.isExpandAllComments(), toBoolean(actual.expand_all_comments));
    assertEquals(expected.getIgnoreWhitespace(), actual.ignore_whitespace);
    assertEquals(expected.isIntralineDifference(), toBoolean(actual.intraline_difference));
    assertEquals(expected.getLineLength(), actual.line_length);
    assertEquals(expected.isManualReview(), toBoolean(actual.manual_review));
    assertEquals(expected.isRetainHeader(), toBoolean(actual.retain_header));
    assertEquals(expected.isShowLineEndings(), toBoolean(actual.show_line_endings));
    assertEquals(expected.isShowTabs(), toBoolean(actual.show_tabs));
    assertEquals(expected.isShowWhitespaceErrors(), toBoolean(actual.show_whitespace_errors));
    assertEquals(expected.isSkipDeleted(), toBoolean(actual.skip_deleted));
    assertEquals(expected.isSkipUncommented(), toBoolean(actual.skip_uncommented));
    assertEquals(expected.isSyntaxHighlighting(), toBoolean(actual.syntax_highlighting));
    assertEquals(expected.getTabSize(), actual.tab_size);
  }

  private static boolean toBoolean(Boolean b) {
    if (b == null) {
      return false;
    }
    return b.booleanValue();
  }

  static class DiffPreferencesInfo {
    short context;
    Boolean expand_all_comments;
    Whitespace ignore_whitespace;
    Boolean intraline_difference;
    int line_length;
    Boolean manual_review;
    Boolean retain_header;
    Boolean show_line_endings;
    Boolean show_tabs;
    Boolean show_whitespace_errors;
    Boolean skip_deleted;
    Boolean skip_uncommented;
    Boolean syntax_highlighting;
    int tab_size;
  }
}
