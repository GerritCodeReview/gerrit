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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Access control management for a user accessing a single change. */
public class ChangeControl {
  public static class Factory {
    private final ProjectControl.Factory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    Factory(final ProjectControl.Factory p, final Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
    }

    public ChangeControl controlFor(final Change.Id id)
        throws NoSuchChangeException {
      final Change change;
      try {
        change = db.get().changes().get(id);
        if (change == null) {
          throw new NoSuchChangeException(id);
        }
      } catch (OrmException e) {
        throw new NoSuchChangeException(id, e);
      }
      return controlFor(change);
    }

    public ChangeControl controlFor(final Change change)
        throws NoSuchChangeException {
      try {
        final Project.NameKey projectKey = change.getProject();
        return projectControl.validateFor(projectKey).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      }
    }

    public ChangeControl validateFor(final Change.Id id)
        throws NoSuchChangeException {
      return validate(controlFor(id));
    }

    public ChangeControl validateFor(final Change change)
        throws NoSuchChangeException {
      return validate(controlFor(change));
    }

    private static ChangeControl validate(final ChangeControl c)
        throws NoSuchChangeException {
      if (!c.isVisible()) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }
  }

  private final ProjectControl projectControl;
  private final Change change;

  ChangeControl(final ProjectControl p, final Change c) {
    this.projectControl = p;
    this.change = c;
  }

  public ChangeControl forAnonymousUser() {
    return new ChangeControl(projectControl.forAnonymousUser(), change);
  }

  public ChangeControl forUser(final CurrentUser who) {
    return new ChangeControl(projectControl.forUser(who), change);
  }

  public CurrentUser getCurrentUser() {
    return getProjectControl().getCurrentUser();
  }

  public ProjectControl getProjectControl() {
    return projectControl;
  }

  public Project getProject() {
    return getProjectControl().getProject();
  }

  public Change getChange() {
    return change;
  }

  /** Can this user see this change? */
  public boolean isVisible() {
    return getProjectControl().isVisible();
  }

  /** Can this user abandon this change? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().isAdministrator() // site administers are god
    ;
  }

  /** Is this user the owner of the change? */
  public boolean isOwner() {
    if (getCurrentUser() instanceof IdentifiedUser) {
      final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
      return i.getAccountId().equals(change.getOwner());
    }
    return false;
  }
}
