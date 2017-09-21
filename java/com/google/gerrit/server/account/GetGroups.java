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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.GroupJson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class GetGroups implements RestReadView<AccountResource> {
  private final GroupControl.Factory groupControlFactory;
  private final GroupJson json;

  @Inject
  GetGroups(GroupControl.Factory groupControlFactory, GroupJson json) {
    this.groupControlFactory = groupControlFactory;
    this.json = json;
  }

  @Override
  public List<GroupInfo> apply(AccountResource resource) throws OrmException {
    IdentifiedUser user = resource.getUser();
    Account.Id userId = user.getAccountId();
    List<GroupInfo> groups = new ArrayList<>();
    for (AccountGroup.UUID uuid : user.getEffectiveGroups().getKnownGroups()) {
      GroupControl ctl;
      try {
        ctl = groupControlFactory.controlFor(uuid);
      } catch (NoSuchGroupException e) {
        continue;
      }
      if (ctl.isVisible() && ctl.canSeeMember(userId)) {
        groups.add(json.format(ctl.getGroup()));
      }
    }
    return groups;
  }
}
