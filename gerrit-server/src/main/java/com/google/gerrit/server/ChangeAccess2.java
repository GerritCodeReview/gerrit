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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.List;

@Singleton
public class ChangeAccess2 {
  private final ChangeNotes.Factory notesFactory;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  ChangeAccess2(ChangeNotes.Factory notesFactory,
      Provider<InternalChangeQuery> queryProvider) {
    this.notesFactory = notesFactory;
    this.queryProvider = queryProvider;
  }

  public ChangeNotes get(ReviewDb db, Change c)
      throws OrmException, NoSuchChangeException {
    ChangeNotes notes = notesFactory.create(db, c.getProject(), c.getId());
    if (notes.getChange() == null) {
      throw new NoSuchChangeException(c.getId());
    }
    return notes;
  }

  public ChangeNotes get(ReviewDb db, Project.NameKey project,
      Change.Id changeId) throws OrmException, NoSuchChangeException {
    ChangeNotes notes = notesFactory.create(db, project, changeId);
    if (notes.getChange() == null) {
      throw new NoSuchChangeException(changeId);
    }
    return notes;
  }

  public ChangeNotes get(Change.Id changeId)
      throws OrmException, NoSuchChangeException {
    InternalChangeQuery query =
        queryProvider.get().setRequestedFields(ImmutableSet.<String> of());
    List<ChangeData> changes = query.byLegacyChangeId(changeId);
    if (changes.size() != 1) {
      throw new NoSuchChangeException(changeId);
    }
    return changes.get(0).notes();
  }
}
