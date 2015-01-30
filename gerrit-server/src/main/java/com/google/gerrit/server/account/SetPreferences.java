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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ReviewCategoryStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.SetPreferences.Input;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Singleton
public class SetPreferences implements RestModifyView<AccountResource, Input> {
  public static class Input {
    public Short changesPerPage;
    public Boolean showSiteHeader;
    public Boolean useFlashClipboard;
    public DownloadScheme downloadScheme;
    public DownloadCommand downloadCommand;
    public Boolean copySelfOnEmail;
    public DateFormat dateFormat;
    public TimeFormat timeFormat;
    public Boolean relativeDateInChangeTable;
    public Boolean sizeBarInChangeTable;
    public Boolean legacycidInChangeTable;
    public Boolean muteCommonPathPrefixes;
    public ReviewCategoryStrategy reviewCategoryStrategy;
    public DiffView diffView;
    public List<TopMenu.MenuItem> my;
  }

  private final Provider<CurrentUser> self;
  private final AccountCache cache;
  private final Provider<ReviewDb> db;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final AllUsersName allUsersName;

  @Inject
  SetPreferences(Provider<CurrentUser> self, AccountCache cache,
      Provider<ReviewDb> db, MetaDataUpdate.User metaDataUpdateFactory,
      AllUsersName allUsersName) {
    this.self = self;
    this.cache = cache;
    this.db = db;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
  }

  @Override
  public GetPreferences.PreferenceInfo apply(AccountResource rsrc, Input i)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("restricted to members of Modify Accounts");
    }
    if (i == null) {
      i = new Input();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    AccountGeneralPreferences p;
    VersionedAccountPreferences versionedPrefs;
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsersName);
    db.get().accounts().beginTransaction(accountId);
    try {
      Account a = db.get().accounts().get(accountId);
      if (a == null) {
        throw new ResourceNotFoundException();
      }

      versionedPrefs = VersionedAccountPreferences.forUser(accountId);
      versionedPrefs.load(md);

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
      if (i.relativeDateInChangeTable != null) {
        p.setRelativeDateInChangeTable(i.relativeDateInChangeTable);
      }
      if (i.sizeBarInChangeTable != null) {
        p.setSizeBarInChangeTable(i.sizeBarInChangeTable);
      }
      if (i.legacycidInChangeTable != null) {
        p.setLegacycidInChangeTable(i.legacycidInChangeTable);
      }
      if (i.muteCommonPathPrefixes != null) {
        p.setMuteCommonPathPrefixes(i.muteCommonPathPrefixes);
      }
      if (i.reviewCategoryStrategy != null) {
        p.setReviewCategoryStrategy(i.reviewCategoryStrategy);
      }
      if (i.diffView != null) {
        p.setDiffView(i.diffView);
      }

      db.get().accounts().update(Collections.singleton(a));
      db.get().commit();
      storeMyMenus(versionedPrefs, i.my);
      versionedPrefs.commit(md);
      cache.evict(accountId);
      return new GetPreferences.PreferenceInfo(
          p, versionedPrefs,
          md.getRepository());
    } finally {
      md.close();
      db.get().rollback();
    }
  }

  public static void storeMyMenus(VersionedAccountPreferences prefs,
      List<TopMenu.MenuItem> my) {
    Config cfg = prefs.getConfig();
    if (my != null) {
      unsetSection(cfg, MY);
      for (TopMenu.MenuItem item : my) {
        set(cfg, item.name, KEY_URL, item.url);
        set(cfg, item.name, KEY_TARGET, item.target);
        set(cfg, item.name, KEY_ID, item.id);
      }
    }
  }

  private static void set(Config cfg, String section, String key, String val) {
    if (Strings.isNullOrEmpty(val)) {
      cfg.unset(MY, section, key);
    } else {
      cfg.setString(MY, section, key, val);
    }
  }

  private static void unsetSection(Config cfg, String section) {
    cfg.unsetSection(section, null);
    for (String subsection: cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }
}
