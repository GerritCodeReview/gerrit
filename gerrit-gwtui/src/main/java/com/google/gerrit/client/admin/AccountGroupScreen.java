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

import static com.google.gerrit.client.Dispatcher.toGroup;

import com.google.gerrit.client.groups.GroupApi;
import com.google.gerrit.client.groups.GroupInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.reviewdb.client.AccountGroup;

public abstract class AccountGroupScreen extends MenuScreen {
  public static final String INFO = "info";
  public static final String MEMBERS = "members";

  private final GroupInfo group;
  private final String membersTabToken;

  public AccountGroupScreen(final GroupInfo toShow, final String token) {
    setRequiresSignIn(true);

    this.group = toShow;
    this.membersTabToken = getTabToken(token, MEMBERS);

    link(Util.C.groupTabGeneral(), getTabToken(token, INFO));
    link(Util.C.groupTabMembers(), membersTabToken,
        AccountGroup.isInternalGroup(group.getGroupUUID())
        && !AccountGroup.isSystemGroup(group.getGroupUUID()));
  }

  private String getTabToken(final String token, final String tab) {
    if (token.startsWith("/admin/groups/uuid-")) {
      return toGroup(group.getGroupUUID(), tab);
    } else {
      return toGroup(group.getGroupId(), tab);
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setPageTitle(Util.M.group(group.name()));
    display();
    GroupApi.isGroupOwner(group.name(), new GerritCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        display(group, result);
      }
    });
  }

  protected abstract void display(final GroupInfo group, final boolean canModify);

  protected AccountGroup.UUID getGroupUUID() {
    return group.getGroupUUID();
  }

  protected void updateOwnerGroup(GroupInfo ownerGroup) {
    group.setOwnerUUID(ownerGroup.getGroupUUID());
    group.owner(ownerGroup.name());
  }

  protected AccountGroup.UUID getOwnerGroupUUID() {
    return group.getOwnerUUID();
  }

  protected void setMembersTabVisible(final boolean visible) {
    setLinkVisible(membersTabToken, visible);
  }
}
