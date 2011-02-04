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

package com.google.gerrit.server;

import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Access control management for owners. */
public class OwnerControl {
  public static class Factory {
    private final Provider<CurrentUser> user;
    private final ProjectControl.Factory projectControlFactory;
    private final GroupControl.Factory groupControlFactory;

    @Inject
    Factory(final Provider<CurrentUser> cu,
        final ProjectControl.Factory projectControlFactory,
        final GroupControl.Factory groupControlFactory) {
      user = cu;
      this.projectControlFactory = projectControlFactory;
      this.groupControlFactory = groupControlFactory;
    }

    public OwnerControl controlFor(final Owner.Id owner)
        throws NoSuchEntityException, NoSuchProjectException,
        NoSuchGroupException {
      switch(owner.getType()) {
        case GROUP:
           final GroupControl gc = groupControlFactory.validateFor(
              owner.getAccountGroupId());
            return new OwnerControl(gc, user.get(), owner);
        case PROJECT:
            final ProjectControl pc = projectControlFactory.validateFor(
              owner.getProjectNameKey(),
              ProjectControl.OWNER | ProjectControl.VISIBLE);
            return new OwnerControl(pc, user.get(), owner);
      }
      return new OwnerControl(user.get(), owner);
    }

    public OwnerControl validateFor(final Owner.Id owner)
        throws NoSuchEntityException, NoSuchProjectException,
        NoSuchGroupException {
      final OwnerControl c = controlFor(owner);
      if (!c.isVisible()) {
        throw new NoSuchEntityException();
      }
      return c;
    }
  }

  private final CurrentUser user;
  private final Owner.Id owner;
  private ProjectControl pc = null;
  private GroupControl gc = null;

  OwnerControl(final ProjectControl pc, final CurrentUser who,
      final Owner.Id owner) {
    this(who, owner);
    this.pc = pc;
  }

  OwnerControl(final GroupControl gc, final CurrentUser who,
      final Owner.Id owner) {
    this(who, owner);
    this.gc = gc;
  }

  OwnerControl(final CurrentUser who, final Owner.Id owner) {
    user = who;
    this.owner = owner;
  }

  public CurrentUser getCurrentUser() {
    return user;
  }

  /** Can this user see this owner? */
  public boolean isVisible() {
    switch(owner.getType()) {
      case USER:    return true;
      case GROUP:   return gc.isVisible();
      case PROJECT: return pc.isVisible();
      case SITE:    return true;
    }
    return false;
  }

  public boolean isOwner() {
    switch(owner.getType()) {
      case USER:
          if (user instanceof IdentifiedUser) {
            return owner.getAccountId().equals(
              ((IdentifiedUser) user).getAccountId());
          }
          break;
      case GROUP:
          return user.getEffectiveGroups().contains(owner.getAccountGroupId());
      case PROJECT:
          return pc.isOwner();
      case SITE:
          return user.isAdministrator();
    }
    return false;
  }

  public boolean canEdit() {
    switch(owner.getType()) {
      case USER:
        if (user instanceof IdentifiedUser) {
          return owner.getAccountId().equals(
            ((IdentifiedUser) user).getAccountId());
        }
        break;
      case GROUP:
          return user.getEffectiveGroups().contains(owner.getAccountGroupId());
      case PROJECT:
          return pc.isOwnerAnyRef();
      case SITE:
          return user.isAdministrator();
    }
    return false;
  }
}
