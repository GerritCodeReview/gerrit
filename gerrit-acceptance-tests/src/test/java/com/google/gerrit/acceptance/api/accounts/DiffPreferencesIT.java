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
import static com.google.gerrit.acceptance.AssertUtil.assertPrefs;
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.After;
import org.junit.Test;

@NoHttpd
public class DiffPreferencesIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsers;

  @After
  public void cleanUp() throws Exception {
    gApi.accounts().id(admin.getId().toString()).setDiffPreferences(DiffPreferencesInfo.defaults());

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    try {
      fetch(allUsersRepo, RefNames.REFS_USERS_DEFAULT + ":defaults");
    } catch (TransportException e) {
      if (e.getMessage()
          .equals(
              "Remote does not have " + RefNames.REFS_USERS_DEFAULT + " available for fetch.")) {
        return;
      }
      throw e;
    }
    allUsersRepo.reset("defaults");
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Delete default preferences",
            VersionedAccountPreferences.PREFERENCES,
            "");
    push.rm(RefNames.REFS_USERS_DEFAULT).assertOkStatus();
  }

  @Test
  public void getDiffPreferences() throws Exception {
    DiffPreferencesInfo d = DiffPreferencesInfo.defaults();
    DiffPreferencesInfo o = gApi.accounts().id(admin.getId().toString()).getDiffPreferences();
    assertPrefs(o, d);
  }

  @Test
  public void setDiffPreferences() throws Exception {
    DiffPreferencesInfo i = DiffPreferencesInfo.defaults();

    // change all default values
    i.context *= -1;
    i.tabSize *= -1;
    i.fontSize *= -1;
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
    i.lineWrapping ^= true;

    DiffPreferencesInfo o = gApi.accounts().id(admin.getId().toString()).setDiffPreferences(i);
    assertPrefs(o, i);

    // Partially fill input record
    i = new DiffPreferencesInfo();
    i.tabSize = 42;
    DiffPreferencesInfo a = gApi.accounts().id(admin.getId().toString()).setDiffPreferences(i);
    assertPrefs(a, o, "tabSize");
    assertThat(a.tabSize).isEqualTo(42);
  }

  @Test
  public void getDiffPreferencesWithConfiguredDefaults() throws Exception {
    DiffPreferencesInfo d = DiffPreferencesInfo.defaults();
    int newLineLength = d.lineLength + 10;
    int newTabSize = d.tabSize * 2;
    int newFontSize = d.fontSize - 2;
    DiffPreferencesInfo update = new DiffPreferencesInfo();
    update.lineLength = newLineLength;
    update.tabSize = newTabSize;
    update.fontSize = newFontSize;
    gApi.config().server().setDefaultDiffPreferences(update);

    DiffPreferencesInfo o = gApi.accounts().id(admin.getId().toString()).getDiffPreferences();

    // assert configured defaults
    assertThat(o.lineLength).isEqualTo(newLineLength);
    assertThat(o.tabSize).isEqualTo(newTabSize);
    assertThat(o.fontSize).isEqualTo(newFontSize);

    // assert hard-coded defaults
    assertPrefs(o, d, "lineLength", "tabSize", "fontSize");
  }
}
