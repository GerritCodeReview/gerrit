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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.server.DisabledChangesReviewDbWrapper;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class NotesMigrationSchemaFactory implements SchemaFactory<ReviewDb> {
  private final SchemaFactory<ReviewDb> delegate;
  private final NotesMigration migration;

  @Inject
  NotesMigrationSchemaFactory(
      @ReviewDbFactory SchemaFactory<ReviewDb> delegate, NotesMigration migration) {
    this.delegate = delegate;
    this.migration = migration;
  }

  @Override
  public ReviewDb open() throws OrmException {
    ReviewDb db = delegate.open();
    if (!migration.readChanges()) {
      return db;
    }
    return new DisabledChangesReviewDbWrapper(db);
  }
}
