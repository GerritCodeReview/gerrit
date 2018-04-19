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

import com.google.gerrit.index.query.IsVisibleToPredicate;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeIsVisibleToPredicate extends IsVisibleToPredicate<ChangeData> {
  private static final Logger log = LoggerFactory.getLogger(ChangeIsVisibleToPredicate.class);

  protected final Provider<ReviewDb> db;
  protected final ChangeNotes.Factory notesFactory;
  protected final CurrentUser user;
  protected final PermissionBackend permissionBackend;

  public ChangeIsVisibleToPredicate(
      Provider<ReviewDb> db,
      ChangeNotes.Factory notesFactory,
      CurrentUser user,
      PermissionBackend permissionBackend) {
    super(ChangeQueryBuilder.FIELD_VISIBLETO, IndexUtils.describe(user));
    this.db = db;
    this.notesFactory = notesFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    if (cd.fastIsVisibleTo(user)) {
      return true;
    }
    Change change = cd.change();
    if (change == null) {
      return false;
    }

    ChangeNotes notes = notesFactory.createFromIndexedChange(change);
    boolean visible;
    try {
      visible =
          permissionBackend
              .user(user)
              .indexedChange(cd, notes)
              .database(db)
              .test(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RepositoryNotFoundException) {
        log.warn(
            "Skipping change {} because the corresponding repository was not found", cd.getId(), e);
        return false;
      }
      throw new OrmException("unable to check permissions on change " + cd.getId(), e);
    }
    if (visible) {
      cd.cacheVisibleTo(user);
      return true;
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
