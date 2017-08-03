// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.IsVisibleToPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

public class ChangeIsVisibleToPredicate extends IsVisibleToPredicate<ChangeData> {
  protected final Provider<ReviewDb> db;
  protected final ChangeNotes.Factory notesFactory;
  protected final ChangeControl.GenericFactory changeControl;
  protected final CurrentUser user;
  protected final PermissionBackend permissionBackend;

  public ChangeIsVisibleToPredicate(
      Provider<ReviewDb> db,
      ChangeNotes.Factory notesFactory,
      ChangeControl.GenericFactory changeControlFactory,
      CurrentUser user,
      PermissionBackend permissionBackend) {
    super(ChangeQueryBuilder.FIELD_VISIBLETO, describe(user));
    this.db = db;
    this.notesFactory = notesFactory;
    this.changeControl = changeControlFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    if (cd.fastIsVisibleTo(user)) {
      return true;
    }
    Change change;
    try {
      change = cd.change();
      if (change == null) {
        return false;
      }
    } catch (NoSuchChangeException e) {
      // Ignored
      return false;
    }

    ChangeNotes notes = notesFactory.createFromIndexedChange(change);
    ChangeControl cc = changeControl.controlFor(notes, user);
    boolean visible;
    try {
      visible =
          permissionBackend
              .user(user)
              .indexedChange(cd, notes)
              .database(db)
              .test(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      throw new OrmException("unable to check permissions", e);
    }
    if (visible) {
      cd.cacheVisibleTo(cc);
      return true;
    }

    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
