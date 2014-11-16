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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
public class GetDiffPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final Provider<AllUsersName> allUsersName;
  private final GitRepositoryManager gitMgr;

  @Inject
  GetDiffPreferences(Provider<CurrentUser> self,
      Provider<ReviewDb> db,
      MetaDataUpdate.User metaDataUpdateFactory,
      Provider<AllUsersName> allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.db = db;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc)
      throws AuthException, OrmException, ConfigInvalidException, IOException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    try (Repository git = gitMgr.openRepository(allUsersName.get())) {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forUser(rsrc.getUser().getAccountId());
      p.load(git);
      DiffPreferencesInfo prefs = new DiffPreferencesInfo();
      loadSection(p.getConfig(), UserConfigSections.DIFF, null, prefs,
          DiffPreferencesInfo.defaults());
      if (prefs.migrated == null) {
        Account.Id id = rsrc.getUser().getAccountId();
        AccountDiffPreference a = db.get().accountDiffPreferences().get(id);
        initFromDb(prefs, a);
        prefs.migrated = true;
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsersName.get());
        storeSection(p.getConfig(), UserConfigSections.DIFF, null, prefs,
            DiffPreferencesInfo.defaults());
        p.commit(md);
      }
      // Reset the migrated flag to prevent it from being sent over the wire.
      prefs.migrated = null;
      return prefs;
    }
  }

  private static void initFromDb(DiffPreferencesInfo prefs,
      AccountDiffPreference a) {
    if (a != null) {
      prefs.context = (int)a.getContext();
      prefs.expandAllComments = a.isExpandAllComments();
      prefs.hideLineNumbers = a.isHideLineNumbers();
      prefs.hideTopMenu = a.isHideTopMenu();
      prefs.ignoreWhitespace = PatchListKey.WHITESPACE_TYPES.inverse().get(
          a.getIgnoreWhitespace().getCode());
      prefs.intralineDifference = a.isIntralineDifference();
      prefs.lineLength = a.getLineLength();
      prefs.manualReview = a.isManualReview();
      prefs.renderEntireFile = a.isRenderEntireFile();
      prefs.retainHeader = a.isRetainHeader();
      prefs.showLineEndings = a.isShowLineEndings();
      prefs.showTabs = a.isShowTabs();
      prefs.showWhitespaceErrors = a.isShowWhitespaceErrors();
      prefs.skipDeleted = a.isSkipDeleted();
      prefs.skipUncommented = a.isSkipUncommented();
      prefs.syntaxHighlighting = a.isSyntaxHighlighting();
      prefs.tabSize = a.getTabSize();
      prefs.theme = Theme.valueOf(a.getTheme().name());
      prefs.hideEmptyPane = a.isHideEmptyPane();
      prefs.autoHideDiffTableHeader = a.isAutoHideDiffTableHeader();
    }
  }
}
