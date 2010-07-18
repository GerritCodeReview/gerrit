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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.Set;

class IsWatchedByPredicate extends OperatorPredicate<ChangeData> {
  private static String describe(CurrentUser user) {
    if (user instanceof IdentifiedUser) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    return user.toString();
  }

  private final Provider<ReviewDb> db;
  private final Project.NameKey wildProject;
  private final CurrentUser user;

  IsWatchedByPredicate(Provider<ReviewDb> db,
      @WildProjectName Project.NameKey wildProject, CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_WATCHEDBY, describe(user));
    this.db = db;
    this.wildProject = wildProject;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    Set<NameKey> watched = user.getWatchedProjects();
    if (watched.contains(wildProject)) {
      return true;
    }

    Change change = cd.change(db);
    if (change == null) {
      return false;
    }

    Project.NameKey project = change.getDest().getParentKey();
    return watched.contains(project);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
