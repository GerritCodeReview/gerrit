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
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.gerrit.extensions.client.Theme;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.After;
import org.junit.Test;

@NoHttpd
public class DiffPreferencesIT extends AbstractDaemonTest {
  @Inject
  private AllUsersName allUsers;

  @After
  public void cleanUp() throws Exception {
    gApi.accounts().id(admin.getId().toString())
        .setDiffPreferences(DiffPreferencesInfo.defaults());

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    try {
      fetch(allUsersRepo, RefNames.REFS_USERS_DEFAULT + ":defaults");
    } catch (TransportException e) {
      if (e.getMessage().equals("Remote does not have "
          + RefNames.REFS_USERS_DEFAULT + " available for fetch.")) {
        return;
      }
      throw e;
    }
    allUsersRepo.reset("defaults");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo,
        "Delete default preferences", VersionedAccountPreferences.PREFERENCES,
        "");
    push.rm(RefNames.REFS_USERS_DEFAULT).assertOkStatus();
  }

  @Test
  public void getDiffPreferences() throws Exception {
    DiffPreferencesInfo d = DiffPreferencesInfo.defaults();
    DiffPreferencesInfo o = gApi.accounts()
        .id(admin.getId().toString())
        .getDiffPreferences();

    assertThat(o.context).isEqualTo(d.context);
    assertThat(o.tabSize).isEqualTo(d.tabSize);
    assertThat(o.lineLength).isEqualTo(d.lineLength);
    assertThat(o.cursorBlinkRate).isEqualTo(d.cursorBlinkRate);
    assertThat(o.expandAllComments).isNull();
    assertThat(o.intralineDifference).isEqualTo(d.intralineDifference);
    assertThat(o.manualReview).isNull();
    assertThat(o.retainHeader).isNull();
    assertThat(o.showLineEndings).isEqualTo(d.showLineEndings);
    assertThat(o.showTabs).isEqualTo(d.showTabs);
    assertThat(o.showWhitespaceErrors).isEqualTo(d.showWhitespaceErrors);
    assertThat(o.skipDeleted).isNull();
    assertThat(o.skipUnchanged).isNull();
    assertThat(o.skipUncommented).isNull();
    assertThat(o.syntaxHighlighting).isEqualTo(d.syntaxHighlighting);
    assertThat(o.hideTopMenu).isNull();
    assertThat(o.autoHideDiffTableHeader).isEqualTo(d.autoHideDiffTableHeader);
    assertThat(o.hideLineNumbers).isNull();
    assertThat(o.renderEntireFile).isNull();
    assertThat(o.hideEmptyPane).isNull();
    assertThat(o.matchBrackets).isNull();
    assertThat(o.ignoreWhitespace).isEqualTo(d.ignoreWhitespace);
    assertThat(o.theme).isEqualTo(d.theme);
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

    assertThat(o.context).isEqualTo(i.context);
    assertThat(o.tabSize).isEqualTo(i.tabSize);
    assertThat(o.lineLength).isEqualTo(i.lineLength);
    assertThat(o.cursorBlinkRate).isEqualTo(i.cursorBlinkRate);
    assertThat(o.expandAllComments).isEqualTo(i.expandAllComments);
    assertThat(o.intralineDifference).isNull();
    assertThat(o.manualReview).isEqualTo(i.manualReview);
    assertThat(o.retainHeader).isEqualTo(i.retainHeader);
    assertThat(o.showLineEndings).isNull();
    assertThat(o.showTabs).isNull();
    assertThat(o.showWhitespaceErrors).isNull();
    assertThat(o.skipDeleted).isEqualTo(i.skipDeleted);
    assertThat(o.skipUnchanged).isEqualTo(i.skipUnchanged);
    assertThat(o.skipUncommented).isEqualTo(i.skipUncommented);
    assertThat(o.syntaxHighlighting).isNull();
    assertThat(o.hideTopMenu).isEqualTo(i.hideTopMenu);
    assertThat(o.autoHideDiffTableHeader).isNull();
    assertThat(o.hideLineNumbers).isEqualTo(i.hideLineNumbers);
    assertThat(o.renderEntireFile).isEqualTo(i.renderEntireFile);
    assertThat(o.hideEmptyPane).isEqualTo(i.hideEmptyPane);
    assertThat(o.matchBrackets).isEqualTo(i.matchBrackets);
    assertThat(o.ignoreWhitespace).isEqualTo(i.ignoreWhitespace);
    assertThat(o.theme).isEqualTo(i.theme);

    // Partially fill input record
    i = new DiffPreferencesInfo();
    i.tabSize = 42;
    DiffPreferencesInfo a = gApi.accounts()
        .id(admin.getId().toString())
        .setDiffPreferences(i);

    assertThat(a.context).isEqualTo(o.context);
    assertThat(a.tabSize).isEqualTo(42);
    assertThat(a.lineLength).isEqualTo(o.lineLength);
    assertThat(a.expandAllComments).isEqualTo(o.expandAllComments);
    assertThat(a.intralineDifference).isNull();
    assertThat(a.manualReview).isEqualTo(o.manualReview);
    assertThat(a.retainHeader).isEqualTo(o.retainHeader);
    assertThat(a.showLineEndings).isNull();
    assertThat(a.showTabs).isNull();
    assertThat(a.showWhitespaceErrors).isNull();
    assertThat(a.skipDeleted).isEqualTo(o.skipDeleted);
    assertThat(a.skipUnchanged).isEqualTo(o.skipUnchanged);
    assertThat(a.skipUncommented).isEqualTo(o.skipUncommented);
    assertThat(a.syntaxHighlighting).isNull();
    assertThat(a.hideTopMenu).isEqualTo(o.hideTopMenu);
    assertThat(a.autoHideDiffTableHeader).isNull();
    assertThat(a.hideLineNumbers).isEqualTo(o.hideLineNumbers);
    assertThat(a.renderEntireFile).isEqualTo(o.renderEntireFile);
    assertThat(a.hideEmptyPane).isEqualTo(o.hideEmptyPane);
    assertThat(a.ignoreWhitespace).isEqualTo(o.ignoreWhitespace);
    assertThat(a.theme).isEqualTo(o.theme);
  }

  @Test
  public void getDiffPreferencesWithConfiguredDefaults() throws Exception {
    DiffPreferencesInfo d = DiffPreferencesInfo.defaults();
    int newLineLength = d.lineLength + 10;
    int newTabSize = d.tabSize * 2;
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = newLineLength;
    update.tabSize = newTabSize;
    gApi.config().server().setDefaultDiffPreferences(update);

    DiffPreferencesInfo o = gApi.accounts()
        .id(admin.getId().toString())
        .getDiffPreferences();

    // assert configured defaults
    assertThat(o.lineLength).isEqualTo(newLineLength);
    assertThat(o.tabSize).isEqualTo(newTabSize);

    // assert hard-coded defaults
    assertThat(o.context).isEqualTo(d.context);
    assertThat(o.cursorBlinkRate).isEqualTo(d.cursorBlinkRate);
    assertThat(o.expandAllComments).isNull();
    assertThat(o.intralineDifference).isEqualTo(d.intralineDifference);
    assertThat(o.manualReview).isNull();
    assertThat(o.retainHeader).isNull();
    assertThat(o.showLineEndings).isEqualTo(d.showLineEndings);
    assertThat(o.showTabs).isEqualTo(d.showTabs);
    assertThat(o.showWhitespaceErrors).isEqualTo(d.showWhitespaceErrors);
    assertThat(o.skipDeleted).isNull();
    assertThat(o.skipUnchanged).isNull();
    assertThat(o.skipUncommented).isNull();
    assertThat(o.syntaxHighlighting).isEqualTo(d.syntaxHighlighting);
    assertThat(o.hideTopMenu).isNull();
    assertThat(o.autoHideDiffTableHeader).isEqualTo(d.autoHideDiffTableHeader);
    assertThat(o.hideLineNumbers).isNull();
    assertThat(o.renderEntireFile).isNull();
    assertThat(o.hideEmptyPane).isNull();
    assertThat(o.matchBrackets).isNull();
    assertThat(o.ignoreWhitespace).isEqualTo(d.ignoreWhitespace);
    assertThat(o.theme).isEqualTo(d.theme);
  }
}
