// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import static com.google.gerrit.client.Dispatcher.toAccountGroup;
import static com.google.gerrit.client.Dispatcher.toGroup;

import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.reviewdb.AccountGroup;

public abstract class AccountGroupScreen extends MenuScreen {
  public static final String INFO = "info";
  public static final String MEMBERS = "members";

  private final GroupDetail groupDetail;
  private final String membersTabToken;

  public AccountGroupScreen(final GroupDetail toShow, final String token) {
    setRequiresSignIn(true);

    this.groupDetail = toShow;
    this.membersTabToken = getTabToken(token, MEMBERS);

    link(Util.C.groupTabGeneral(), getTabToken(token, INFO));
    link(Util.C.groupTabMembers(), membersTabToken,
        groupDetail.group.getType() == AccountGroup.Type.INTERNAL);
  }

  private String getTabToken(final String token, final String tab) {
    if (token.startsWith("admin,group,uuid-")) {
      return toGroup(groupDetail.group.getGroupUUID(), tab);
    } else {
      return toAccountGroup(groupDetail.group.getId(), tab);
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setPageTitle(Util.M.group(groupDetail.group.getName()));
    display();
    display(groupDetail);
  }

  protected abstract void display(final GroupDetail groupDetail);

  protected AccountGroup.Id getGroupId() {
    return groupDetail.group.getId();
  }

  protected void setMembersTabVisible(final boolean visible) {
    setLinkVisible(membersTabToken, visible);
  }
}
