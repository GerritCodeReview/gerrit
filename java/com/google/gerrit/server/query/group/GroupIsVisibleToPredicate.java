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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.index.query.IsVisibleToPredicate;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.query.account.AccountQueryBuilder;

public class GroupIsVisibleToPredicate extends IsVisibleToPredicate<InternalGroup> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final GroupControl.GenericFactory groupControlFactory;
  protected final CurrentUser user;

  public GroupIsVisibleToPredicate(
      GroupControl.GenericFactory groupControlFactory, CurrentUser user) {
    super(AccountQueryBuilder.FIELD_VISIBLETO, IndexUtils.describe(user));
    this.groupControlFactory = groupControlFactory;
    this.user = user;
  }

  @Override
  public boolean match(InternalGroup group) {
    try {
      boolean canSee = groupControlFactory.controlFor(user, group.getGroupUUID()).isVisible();
      if (!canSee) {
        logger.atFine().log("Filter out non-visisble group: %s", group.getGroupUUID());
      }
      return canSee;
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
