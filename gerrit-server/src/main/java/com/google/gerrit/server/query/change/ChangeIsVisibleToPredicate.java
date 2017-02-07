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
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.IsVisibleToPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class ChangeIsVisibleToPredicate extends IsVisibleToPredicate<ChangeData> {
  private static String describe(CurrentUser user) {
    if (user.isIdentifiedUser()) {
      return user.getAccountId().toString();
    }
    if (user instanceof SingleGroupUser) {
      return "group:"
          + user.getEffectiveGroups()
              .getKnownGroups() //
              .iterator()
              .next()
              .toString();
    }
    return user.toString();
  }

  private final Provider<ReviewDb> db;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeControl.GenericFactory changeControl;
  private final CurrentUser user;

  ChangeIsVisibleToPredicate(
      Provider<ReviewDb> db,
      ChangeNotes.Factory notesFactory,
      ChangeControl.GenericFactory changeControlFactory,
      CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_VISIBLETO, describe(user));
    this.db = db;
    this.notesFactory = notesFactory;
    this.changeControl = changeControlFactory;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    if (cd.fastIsVisibleTo(user)) {
      return true;
    }
    try {
      Change c = cd.change();
      if (c == null) {
        return false;
      }

      ChangeNotes notes = notesFactory.createFromIndexedChange(c);
      ChangeControl cc = changeControl.controlFor(notes, user);
      if (cc.isVisible(db.get(), cd)) {
        cd.cacheVisibleTo(cc);
        return true;
      }
    } catch (NoSuchChangeException e) {
      // Ignored
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
