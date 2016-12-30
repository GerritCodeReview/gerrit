// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.PostFilterPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Objects;

public class CanSeeChangePredicate extends PostFilterPredicate<AccountState> {
  private final Provider<ReviewDb> db;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeNotes changeNotes;

  CanSeeChangePredicate(
      Provider<ReviewDb> db,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeNotes changeNotes) {
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.changeNotes = changeNotes;
  }

  @Override
  public boolean match(AccountState accountState) throws OrmException {
    return changeControlFactory
        .controlFor(changeNotes, userFactory.create(accountState.getAccount().getId()))
        .isVisible(db.get());
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public Predicate<AccountState> copy(Collection<? extends Predicate<AccountState>> children) {
    return new CanSeeChangePredicate(db, changeControlFactory, userFactory, changeNotes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeNotes.getChange().getChangeId());
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    return getClass() == other.getClass()
        && changeNotes.getChange().getChangeId()
            == ((CanSeeChangePredicate) other).changeNotes.getChange().getChangeId();
  }
}
