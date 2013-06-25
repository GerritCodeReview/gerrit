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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.io.IOException;

/**
 * Predicate to match changes that contains specified text in commit messages
 * body.
 */
public class MessagePredicate extends IndexPredicate<ChangeData> {
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final String value;

  public MessagePredicate(Provider<ReviewDb> db,
      GitRepositoryManager repoManager, String value) {
    super(ChangeField.COMMIT_MSG, value);
    this.db = db;
    this.repoManager = repoManager;
    this.value = value;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    try {
      return object.commitMessage(repoManager, db).contains(value);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public boolean isIndexOnly() {
    return true;
  }
}
