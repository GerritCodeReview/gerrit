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

import static com.google.gerrit.server.account.GetDiffPreferences.initFromDb;
import static com.google.gerrit.server.account.GetDiffPreferences.readFromGit;
import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collections;

@Singleton
public class SetDiffPreferences implements
    RestModifyView<AccountResource, DiffPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitMgr;
  private final boolean readFromGit;

  @Inject
  SetDiffPreferences(Provider<CurrentUser> self,
      Provider<ReviewDb> db,
      @GerritServerConfig Config cfg,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.db = db;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
    readFromGit = cfg.getBoolean("user", null, "readPrefsFromGit", false);
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, DiffPreferencesInfo in)
      throws AuthException, BadRequestException, ConfigInvalidException,
      RepositoryNotFoundException, IOException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("restricted to members of Modify Accounts");
    }

    if (in == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id userId = rsrc.getUser().getAccountId();
    DiffPreferencesInfo n = readFromGit
        ? readFromGit(userId, gitMgr, allUsersName, in)
        : merge(initFromDb(db.get().accountDiffPreferences().get(userId)), in);
    DiffPreferencesInfo out = writeToGit(n, userId);
    writeToDb(n, userId);
    return out;
  }

  private void writeToDb(DiffPreferencesInfo in, Account.Id id)
      throws OrmException {
    db.get().accounts().beginTransaction(id);
    try {
      AccountDiffPreference p = db.get().accountDiffPreferences().get(id);
      p = initAccountDiffPreferences(p, in, id);
      db.get().accountDiffPreferences().upsert(Collections.singleton(p));
      db.get().commit();
    } finally {
      db.get().rollback();
    }
  }

  private DiffPreferencesInfo writeToGit(DiffPreferencesInfo in,
      Account.Id useId) throws RepositoryNotFoundException, IOException,
          ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName);

    VersionedAccountPreferences prefs;
    DiffPreferencesInfo out = new DiffPreferencesInfo();
    try {
      prefs = VersionedAccountPreferences.forUser(useId);
      prefs.load(md);
      storeSection(prefs.getConfig(), UserConfigSections.DIFF, null, in,
          DiffPreferencesInfo.defaults());
      prefs.commit(md);
      loadSection(prefs.getConfig(), UserConfigSections.DIFF, null, out,
          DiffPreferencesInfo.defaults(), null);
    } finally {
      md.close();
    }
    return out;
  }

  // TODO(davido): Remove manual merging in follow-up change
  private DiffPreferencesInfo merge(DiffPreferencesInfo n,
      DiffPreferencesInfo i) {
    if (i.context != null) {
      n.context = i.context;
    }
    if (i.expandAllComments != null) {
      n.expandAllComments = i.expandAllComments;
    }
    if (i.hideLineNumbers != null) {
      n.hideLineNumbers = i.hideLineNumbers;
    }
    if (i.hideTopMenu != null) {
      n.hideTopMenu = i.hideTopMenu;
    }
    if (i.ignoreWhitespace != null) {
      n.ignoreWhitespace = i.ignoreWhitespace;
    }
    if (i.intralineDifference != null) {
      n.intralineDifference = i.intralineDifference;
    }
    if (i.lineLength != null) {
      n.lineLength = i.lineLength;
    }
    if (i.manualReview != null) {
      n.manualReview = i.manualReview;
    }
    if (i.renderEntireFile != null) {
      n.renderEntireFile = i.renderEntireFile;
    }
    if (i.retainHeader != null) {
      n.retainHeader = i.retainHeader;
    }
    if (i.showLineEndings != null) {
      n.showLineEndings = i.showLineEndings;
    }
    if (i.showTabs != null) {
      n.showTabs = i.showTabs;
    }
    if (i.showWhitespaceErrors != null) {
      n.showWhitespaceErrors = i.showWhitespaceErrors;
    }
    if (i.skipDeleted != null) {
      n.skipDeleted = i.skipDeleted;
    }
    if (i.skipUncommented != null) {
      n.skipUncommented = i.skipUncommented;
    }
    if (i.syntaxHighlighting != null) {
      n.syntaxHighlighting = i.syntaxHighlighting;
    }
    if (i.tabSize != null) {
      n.tabSize = i.tabSize;
    }
    if (i.theme != null) {
      n.theme = i.theme;
    }
    if (i.hideEmptyPane != null) {
      n.hideEmptyPane = i.hideEmptyPane;
    }
    if (i.autoHideDiffTableHeader != null) {
      n.autoHideDiffTableHeader = i.autoHideDiffTableHeader;
    }
    return n;
  }

  private static AccountDiffPreference initAccountDiffPreferences(
      AccountDiffPreference a, DiffPreferencesInfo i, Account.Id id) {
    if (a == null) {
      a = AccountDiffPreference.createDefault(id);
    }
    int context = i.context == null
        ? DiffPreferencesInfo.DEFAULT_CONTEXT
        :  i.context;
    a.setContext((short)context);
    a.setExpandAllComments(b(i.expandAllComments));
    a.setHideLineNumbers(b(i.hideLineNumbers));
    a.setHideTopMenu(b(i.hideTopMenu));
    a.setIgnoreWhitespace(i.ignoreWhitespace == null
        ? Whitespace.IGNORE_NONE
        : Whitespace.forCode(
            PatchListKey.WHITESPACE_TYPES.get(i.ignoreWhitespace)));
    a.setIntralineDifference(b(i.intralineDifference));
    a.setLineLength(i.lineLength == null
        ? DiffPreferencesInfo.DEFAULT_LINE_LENGTH
        : i.lineLength);
    a.setManualReview(b(i.manualReview));
    a.setRenderEntireFile(b(i.renderEntireFile));
    a.setRetainHeader(b(i.retainHeader));
    a.setShowLineEndings(b(i.showLineEndings));
    a.setShowTabs(b(i.showTabs));
    a.setShowWhitespaceErrors(b(i.showWhitespaceErrors));
    a.setSkipDeleted(b(i.skipDeleted));
    a.setSkipUncommented(b(i.skipUncommented));
    a.setSyntaxHighlighting(b(i.syntaxHighlighting));
    a.setTabSize(i.tabSize == null
        ? DiffPreferencesInfo.DEFAULT_TAB_SIZE
        : i.tabSize);
    a.setTheme(i.theme);
    a.setHideEmptyPane(b(i.hideEmptyPane));
    a.setAutoHideDiffTableHeader(b(i.autoHideDiffTableHeader));
    return a;
  }

  private static boolean b(Boolean b) {
    return b == null ? false : b;
  }
}
