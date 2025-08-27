// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.assistedinject.Assisted;
import javax.inject.Inject;

/** Access control management for a user accessing a single change. */
public class ChangeControl extends AbstractChangeControl {
  public interface Factory {
    ChangeControl create(
        ProjectControl projectControl, RefControl refControl, ChangeData changeData);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeData changeData;

  @Inject
  protected ChangeControl(
      PermissionBackend permissionBackend,
      @Assisted ProjectControl projectControl,
      @Assisted RefControl refControl,
      @Assisted ChangeData changeData) {
    super(
        projectControl,
        refControl,
        permissionBackend,
        changeData.change().isNew(),
        isOwner(refControl, changeData));
    this.changeData = changeData;
  }

  private static boolean isOwner(RefControl refControl, ChangeData changeData) {
    CurrentUser user = refControl.getUser();
    if (user.isIdentifiedUser()) {
      Account.Id id = user.asIdentifiedUser().getAccountId();
      return id.equals(changeData.change().getOwner());
    }
    return false;
  }

  @Override
  public ForChange asForChange() {
    return new ForChangeImpl(changeData.getId());
  }

  /** Can this user see this change? */
  @Override
  protected boolean isVisible() {
    if (changeData.isPrivateOrThrow() && !isPrivateVisible(changeData)) {
      return false;
    }
    return super.isVisible();
  }

  private boolean isPrivateVisible(ChangeData cd) {
    if (projectControl.isAdmin()) {
      logger.atFine().log(
          "%s can see private change %s because this user is an admin",
          getUser().getLoggableName(), cd.getId());
      return true;
    }

    if (isOwner) {
      logger.atFine().log(
          "%s can see private change %s because this user is the change owner",
          getUser().getLoggableName(), cd.getId());
      return true;
    }

    if (isReviewer(cd)) {
      logger.atFine().log(
          "%s can see private change %s because this user is a reviewer",
          getUser().getLoggableName(), cd.getId());
      return true;
    }

    if (refControl.canPerform(Permission.VIEW_PRIVATE_CHANGES)) {
      logger.atFine().log(
          "%s can see private change %s because this user can view private changes",
          getUser().getLoggableName(), cd.getId());
      return true;
    }

    if (getUser().isInternalUser()) {
      logger.atFine().log(
          "%s can see private change %s because this user is an internal user",
          getUser().getLoggableName(), cd.getId());
      return true;
    }

    logger.atFine().log("%s cannot see private change %s", getUser().getLoggableName(), cd.getId());
    return false;
  }

  /** Is this user a reviewer for the change? */
  private boolean isReviewer(ChangeData cd) {
    if (getUser().isIdentifiedUser()) {
      ImmutableSet<Account.Id> results = cd.reviewers().all();
      return results.contains(getUser().getAccountId());
    }
    return false;
  }
}
