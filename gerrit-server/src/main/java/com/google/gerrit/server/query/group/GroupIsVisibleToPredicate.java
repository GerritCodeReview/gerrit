// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.group;

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.query.IsVisibleToPredicate;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gwtorm.server.OrmException;

public class GroupIsVisibleToPredicate extends IsVisibleToPredicate<AccountGroup> {
  private final GroupControl.GenericFactory groupControlFactory;
  private final CurrentUser user;

  GroupIsVisibleToPredicate(GroupControl.GenericFactory groupControlFactory, CurrentUser user) {
    super(AccountQueryBuilder.FIELD_VISIBLETO, describe(user));
    this.groupControlFactory = groupControlFactory;
    this.user = user;
  }

  @Override
  public boolean match(AccountGroup group) throws OrmException {
    try {
      return groupControlFactory.controlFor(user, group.getGroupUUID()).isVisible();
    } catch (NoSuchGroupException e) {
      // Ignored
      return false;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
