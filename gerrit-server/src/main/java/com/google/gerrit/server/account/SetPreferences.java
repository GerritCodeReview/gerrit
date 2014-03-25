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

import static com.google.gerrit.server.account.GetPreferences.KEY_ID;
import static com.google.gerrit.server.account.GetPreferences.KEY_TARGET;
import static com.google.gerrit.server.account.GetPreferences.KEY_URL;
import static com.google.gerrit.server.account.GetPreferences.MY;
import static com.google.gerrit.server.account.GetPreferences.PREFERENCES;
import static com.google.gerrit.server.account.GetPreferences.REFS_META_USER;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ChangeScreen;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.CommentVisibilityStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.SetPreferences.Input;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SetPreferences implements RestModifyView<AccountResource, Input> {
  static class Input {
    Short changesPerPage;
    Boolean showSiteHeader;
    Boolean useFlashClipboard;
    DownloadScheme downloadScheme;
    DownloadCommand downloadCommand;
    Boolean copySelfOnEmail;
    DateFormat dateFormat;
    TimeFormat timeFormat;
    Boolean reversePatchSetOrder;
    Boolean showUsernameInReviewCategory;
    Boolean relativeDateInChangeTable;
    Boolean sizeBarInChangeTable;
    CommentVisibilityStrategy commentVisibilityStrategy;
    DiffView diffView;
    ChangeScreen changeScreen;
    List<TopMenu.MenuItem> my;
  }

  private final Provider<CurrentUser> self;
  private final AccountCache cache;
  private final ReviewDb db;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final ProjectState allProjects;

  @Inject
  SetPreferences(Provider<CurrentUser> self, AccountCache cache, ReviewDb db,
      MetaDataUpdate.User metaDataUpdateFactory, ProjectCache projectCache) {
    this.self = self;
    this.cache = cache;
    this.db = db;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjects = projectCache.getAllProjects();
  }

  @Override
  public GetPreferences.PreferenceInfo apply(AccountResource rsrc, Input i)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }
    if (i == null) {
      i = new Input();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    AccountGeneralPreferences p;
    db.accounts().beginTransaction(accountId);
    try {
      Account a = db.accounts().get(accountId);
      if (a == null) {
        throw new ResourceNotFoundException();
      }

      p = a.getGeneralPreferences();
      if (p == null) {
        p = new AccountGeneralPreferences();
        a.setGeneralPreferences(p);
      }

      if (i.changesPerPage != null) {
        p.setMaximumPageSize(i.changesPerPage);
      }
      if (i.showSiteHeader != null) {
        p.setShowSiteHeader(i.showSiteHeader);
      }
      if (i.useFlashClipboard != null) {
        p.setUseFlashClipboard(i.useFlashClipboard);
      }
      if (i.downloadScheme != null) {
        p.setDownloadUrl(i.downloadScheme);
      }
      if (i.downloadCommand != null) {
        p.setDownloadCommand(i.downloadCommand);
      }
      if (i.copySelfOnEmail != null) {
        p.setCopySelfOnEmails(i.copySelfOnEmail);
      }
      if (i.dateFormat != null) {
        p.setDateFormat(i.dateFormat);
      }
      if (i.timeFormat != null) {
        p.setTimeFormat(i.timeFormat);
      }
      if (i.reversePatchSetOrder != null) {
        p.setReversePatchSetOrder(i.reversePatchSetOrder);
      }
      if (i.showUsernameInReviewCategory != null) {
        p.setShowUsernameInReviewCategory(i.showUsernameInReviewCategory);
      }
      if (i.relativeDateInChangeTable != null) {
        p.setRelativeDateInChangeTable(i.relativeDateInChangeTable);
      }
      if (i.sizeBarInChangeTable != null) {
        p.setSizeBarInChangeTable(i.sizeBarInChangeTable);
      }
      if (i.commentVisibilityStrategy != null) {
        p.setCommentVisibilityStrategy(i.commentVisibilityStrategy);
      }
      if (i.diffView != null) {
        p.setDiffView(i.diffView);
      }
      if (i.changeScreen != null) {
        p.setChangeScreen(i.changeScreen);
      }

      db.accounts().update(Collections.singleton(a));
      db.commit();
      storeMyMenus(i.my);
      cache.evict(accountId);
    } finally {
      db.rollback();
    }
    return new GetPreferences.PreferenceInfo(p, allProjects);
  }

  private void storeMyMenus(List<TopMenu.MenuItem> my) throws IOException {
    IdentifiedUser user = (IdentifiedUser)self.get();
    ProjectLevelConfig prefsCfg =
        allProjects.getConfig(PREFERENCES, REFS_META_USER
            + user.getAccountId().get());
    Config cfg = prefsCfg.get();
    if (my != null) {
      unsetSection(cfg, MY);
      for (TopMenu.MenuItem item : my) {
        cfg.setString(MY, item.name, KEY_URL, item.url);
        cfg.setString(MY, item.name, KEY_TARGET, item.target);
        cfg.setString(MY, item.name, KEY_ID, item.id);
      }
    }
    MetaDataUpdate md =
        metaDataUpdateFactory.create(allProjects.getProject().getNameKey());
    md.setMessage("Update preferences for " + formatUser(user) + "\n");
    prefsCfg.commit(md);
  }

  private static String formatUser(IdentifiedUser user) {
    StringBuilder u = new StringBuilder();
    u.append(user.getNameEmail());
    u.append(" (");
    u.append(user.getAccountId().get());
    u.append(")");
    return u.toString();
  }

  private static void unsetSection(Config cfg, String section) {
    cfg.unsetSection(section, null);
    for (String subsection: cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }
}
