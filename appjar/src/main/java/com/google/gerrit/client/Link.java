// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.MineStarredScreen;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.patches.PatchSideBySideScreen;
import com.google.gerrit.client.patches.PatchUnifiedScreen;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.HistoryListener;

public class Link implements HistoryListener {
  public static final String SETTINGS = "settings";

  public static final String MINE = "mine";
  public static final String MINE_UNCLAIMED = "mine,unclaimed";
  public static final String MINE_STARRED = "mine,starred";

  public static final String ALL = "all";
  public static final String ALL_OPEN = "all,open";
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

  public void onHistoryChanged(final String token) {
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

    if (SETTINGS.equals(token)) {
      return new AccountSettings();
    }

    if (MINE.equals(token)) {
      return new AccountDashboardScreen(RpcUtil.getAccountId());
    }

    if (MINE_STARRED.equals(token)) {
      return new MineStarredScreen();
    }

    p = "patch,sidebyside,";
    if (token.startsWith(p))
      return new PatchSideBySideScreen(Patch.Key.parse(skip(p, token)));

    p = "patch,unified,";
    if (token.startsWith(p))
      return new PatchUnifiedScreen(Patch.Key.parse(skip(p, token)));

    p = "change,";
    if (token.startsWith(p))
      return new ChangeScreen(Change.Id.parse(skip(p, token)));

    p = "dashboard,";
    if (token.matches(p))
      return new AccountDashboardScreen(Account.Id.parse(skip(p, token)));

    return null;
  }

  private static String skip(final String prefix, final String in) {
    return in.substring(prefix.length());
  }
}
