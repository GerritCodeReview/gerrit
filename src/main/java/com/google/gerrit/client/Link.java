// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.account.AccountSettings;
import com.google.gerrit.client.account.NewAgreementScreen;
import com.google.gerrit.client.account.ValidateEmailScreen;
import com.google.gerrit.client.admin.AccountGroupScreen;
import com.google.gerrit.client.admin.GroupListScreen;
import com.google.gerrit.client.admin.ProjectAdminScreen;
import com.google.gerrit.client.admin.ProjectListScreen;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.AllAbandonedChangesScreen;
import com.google.gerrit.client.changes.AllMergedChangesScreen;
import com.google.gerrit.client.changes.AllOpenChangesScreen;
import com.google.gerrit.client.changes.ChangeQueryResultsScreen;
import com.google.gerrit.client.changes.ByProjectOpenChangesScreen;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.MineDraftsScreen;
import com.google.gerrit.client.changes.MineStarredScreen;
import com.google.gerrit.client.changes.PublishCommentScreen;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwtorm.client.KeyUtil;

public class Link implements ValueChangeHandler<String> {
  public static final String SETTINGS = "settings";
  public static final String SETTINGS_SSHKEYS = "settings,ssh-keys";
  public static final String SETTINGS_WEBIDENT = "settings,web-identities";
  public static final String SETTINGS_AGREEMENTS = "settings,agreements";
  public static final String SETTINGS_CONTACT = "settings,contact";
  public static final String SETTINGS_PROJECTS = "settings,projects";
  public static final String SETTINGS_NEW_AGREEMENT = "settings,new-agreement";

  public static final String MINE = "mine";
  public static final String MINE_STARRED = "mine,starred";
  public static final String MINE_DRAFTS = "mine,drafts";

  public static final String ALL_ABANDONED = "all,abandoned,n,z";
  public static final String ALL_MERGED = "all,merged,n,z";
  public static final String ALL_OPEN = "all,open,n,z";
  public static final String ALL_UNCLAIMED = "all,unclaimed";

  public static final String ADMIN_PEOPLE = "admin,people";
  public static final String ADMIN_GROUPS = "admin,groups";
  public static final String ADMIN_PROJECTS = "admin,projects";

  public static String toChange(final ChangeInfo c) {
    return toChange(c.getId());
  }

  public static String toChange(final Change.Id c) {
    return "change," + c.toString();
  }

  public static String toAccountDashboard(final AccountInfo acct) {
    return toAccountDashboard(acct.getId());
  }

  public static String toAccountDashboard(final Account.Id acct) {
    return "dashboard," + acct.toString();
  }

  public static String toPatchSideBySide(final Patch.Key id) {
    return toPatch("sidebyside", id);
  }

  public static String toPatchUnified(final Patch.Key id) {
    return toPatch("unified", id);
  }

  public static String toPatch(final String type, final Patch.Key id) {
    return "patch," + type + "," + id.toString();
  }

  public static String toAccountGroup(final AccountGroup.Id id) {
    return "admin,group," + id.toString();
  }

  public static String toProjectAdmin(final Project.Id id, final String tab) {
    return "admin,project," + id.toString() + "," + tab;
  }

  public static String toProjectOpen(final Project.NameKey proj) {
    return "project,open," + proj.toString() + ",n,z";
  }

  public static String toChangeQuery(final String query) {
    return "q," + KeyUtil.encode(query) + ",n,z";
  }

  @Override
  public void onValueChange(final ValueChangeEvent<String> event) {
    final String token = event.getValue();
    Screen s;
    try {
      s = select(token);
    } catch (RuntimeException err) {
      GWT.log("Error parsing history token: " + token, err);
      s = null;
    }

    if (s != null) {
      Gerrit.display(s);
    } else {
      Gerrit.display(new NotFoundScreen());
    }
  }

  private Screen select(final String token) {
    String p;

    if (token == null) {
      return null;
    }

    if (SETTINGS.equals(token) || token.startsWith("settings,")) {
      if (SETTINGS_NEW_AGREEMENT.equals(token)) {
        return new NewAgreementScreen();
      }
      return new AccountSettings(token);
    }

    if (MINE.equals(token)) {
      return new AccountDashboardScreen(Common.getAccountId());
    }
    if (token.startsWith("mine,")) {
      if (MINE_STARRED.equals(token)) {
        return new MineStarredScreen();
      }
      if (MINE_DRAFTS.equals(token)) {
        return new MineDraftsScreen();
      }
    }

    if (token.startsWith("all,")) {
      p = "all,abandoned,";
      if (token.startsWith(p)) {
        return new AllAbandonedChangesScreen(skip(p, token));
      }

      p = "all,merged,";
      if (token.startsWith(p)) {
        return new AllMergedChangesScreen(skip(p, token));
      }

      p = "all,open,";
      if (token.startsWith(p)) {
        return new AllOpenChangesScreen(skip(p, token));
      }
    }

    if (token.startsWith("project,")) {
      p = "project,open,";
      if (token.startsWith(p)) {
        final String s = skip(p, token);
        final int c = s.indexOf(',');
        return new ByProjectOpenChangesScreen(Project.NameKey.parse(s
            .substring(0, c)), s.substring(c + 1));
      }
    }

    if (token.startsWith("patch,")) {
      p = "patch,sidebyside,";
      if (token.startsWith(p))
        return new PatchScreen.SideBySide(Patch.Key.parse(skip(p, token)),
            0 /* patchIndex */,
            null /* patchTable */);

      p = "patch,unified,";
      if (token.startsWith(p))
        return new PatchScreen.Unified(Patch.Key.parse(skip(p, token)),
            0 /* patchIndex */,
            null /* patchTable  */);
    }

    p = "change,publish,";
    if (token.startsWith(p))
      return new PublishCommentScreen(PatchSet.Id.parse(skip(p, token)));

    p = "change,";
    if (token.startsWith(p))
      return new ChangeScreen(Change.Id.parse(skip(p, token)));

    p = "dashboard,";
    if (token.startsWith(p))
      return new AccountDashboardScreen(Account.Id.parse(skip(p, token)));

    p = "q,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final int c = s.indexOf(',');
      return new ChangeQueryResultsScreen(s.substring(0, c), s.substring(c + 1));
    }

    if (token.startsWith("admin,")) {
      p = "admin,group,";
      if (token.startsWith(p))
        return new AccountGroupScreen(AccountGroup.Id.parse(skip(p, token)));

      p = "admin,project,";
      if (token.startsWith(p)) {
        p = skip(p, token);
        final int c = p.indexOf(',');
        final String idstr = p.substring(0, c);
        return new ProjectAdminScreen(Project.Id.parse(idstr), token);
      }

      if (ADMIN_GROUPS.equals(token)) {
        return new GroupListScreen();
      }

      if (ADMIN_PROJECTS.equals(token)) {
        return new ProjectListScreen();
      }
    }

    p = "VE,";
    if (token.startsWith(p)) {
      return new ValidateEmailScreen(skip(p, token));
    }

    return null;
  }

  private static String skip(final String prefix, final String in) {
    return in.substring(prefix.length());
  }
}
