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

package com.google.gerrit.common;

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gwtorm.client.KeyUtil;

public class PageLinks {
  public static final String SETTINGS = "settings";
  public static final String SETTINGS_SSHKEYS = "settings,ssh-keys";
  public static final String SETTINGS_WEBIDENT = "settings,web-identities";
  public static final String SETTINGS_MYGROUPS = "settings,group-memberships";
  public static final String SETTINGS_AGREEMENTS = "settings,agreements";
  public static final String SETTINGS_CONTACT = "settings,contact";
  public static final String SETTINGS_PROJECTS = "settings,projects";
  public static final String SETTINGS_NEW_AGREEMENT = "settings,new-agreement";
  public static final String REGISTER = "register";

  public static final String MINE = "mine";
  public static final String MINE_STARRED = "mine,starred";
  public static final String MINE_DRAFTS = "mine,drafts";

  public static final String ALL_ABANDONED = "all,abandoned,n,z";
  public static final String ALL_MERGED = "all,merged,n,z";
  public static final String ALL_OPEN = "all,open,n,z";

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

  public static String toChangeQuery(final String query) {
    return "q," + KeyUtil.encode(query) + ",n,z";
  }

  protected PageLinks() {
  }
}
