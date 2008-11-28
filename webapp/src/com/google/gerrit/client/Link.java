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
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.MineStarredScreen;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.user.client.HistoryListener;

public class Link implements HistoryListener {
  public static final String SETTINGS = "settings";

  public static final String MINE = "mine";
  public static final String MINE_UNCLAIMED = "mine,unclaimed";
  public static final String MINE_STARRED = "mine,starred";

  public static final String ALL = "all";
  public static final String ALL_UNCLAIMED = "all,unclaimed";

  public static final String ADMIN_PEOPLE = "admin,people";
  public static final String ADMIN_GROUPS = "admin,groups";
  public static final String ADMIN_PROJECTS = "admin,projects";

  public static String toChange(final ChangeInfo c) {
    return "change," + c.getId().get();
  }

  public static String toAccountDashboard(final AccountInfo acct) {
    return "dashboard," + acct.getId().get();
  }

  public void onHistoryChanged(final String token) {
    final Screen s = select(token);
    if (s != null) {
      Gerrit.display(s);
    } else {
      Gerrit.display(new NotFoundScreen());
    }
  }

  private Screen select(final String token) {
    if (token == null)
      return null;

    else if (SETTINGS.equals(token))
      return new AccountSettings();

    else if (MINE.equals(token))
      return new AccountDashboardScreen();

    else if (MINE_STARRED.equals(token))
      return new MineStarredScreen();

    else if (token.matches("^change,\\d+$")) {
      final String id = token.substring("change,".length());
      return new ChangeScreen(Integer.parseInt(id));
    }

    else if (token.matches("^dashboard,\\d+$")) {
      final String id = token.substring("dashboard,".length());
      return new AccountDashboardScreen(new Account.Id(Integer.parseInt(id)));
    }

    return null;
  }
}
