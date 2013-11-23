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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

public class IsSubmittablePredicate extends IndexPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  public IsSubmittablePredicate(Provider<ReviewDb> db,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory) {
    super(ChangeField.SUBMITTABLE, "1");
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
  }

  @Override
  public boolean match(ChangeData c) throws OrmException {
    return c.isSubmittable(db, changeControlFactory, userFactory);
  }

  @Override
  public int getCost() {
    return 2;
  }
}
