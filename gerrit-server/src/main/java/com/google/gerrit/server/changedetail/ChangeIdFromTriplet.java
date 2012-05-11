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


package com.google.gerrit.server.changedetail;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;

public class ChangeIdFromTriplet implements Callable<Change.Id> {

  public interface Factory {
    ChangeIdFromTriplet create(final String triplet);
  }

  private final ReviewDb db;
  private final String triplet;

  @Inject
  ChangeIdFromTriplet(final ReviewDb db, @Assisted final String triplet) {
    this.db = db;
    this.triplet = triplet;
  }

  @Override
  public Change.Id call() throws OrmException {
    final String[] tokens = triplet.split(",");
    if (tokens.length != 3) {
      return null;
    }

    try {
      final Change.Key key = Change.Key.parse(tokens[2]);
      final Project.NameKey project = new Project.NameKey(tokens[0]);
      final Branch.NameKey branch = new Branch.NameKey(project, tokens[1]);
      for (final Change change : db.changes().byBranchKey(branch, key)) {
        return change.getId();
      }
    } catch (IllegalArgumentException e) {
      return null;
    }

    return null;
  }
}
