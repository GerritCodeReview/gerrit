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

import com.google.gerrit.reviewdb.server.DisallowReadFromChangesReviewDbWrapper;
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

    // There are two levels at which this class disables access to Changes and related tables,
    // corresponding to two phases of the NoteDb migration:
    //
    // 1. When changes are read from NoteDb but some changes might still have their primary storage
    //    in ReviewDb, it is generally programmer error to read changes from ReviewDb. However,
    //    since ReviewDb is still the primary storage for most or all changes, we still need to
    //    support writing to ReviewDb. This behavior is accomplished by wrapping in a
    //    DisallowReadFromChangesReviewDbWrapper.
    //
    //    Some codepaths might need to be able to read from ReviewDb if they really need to, because
    //    they need to operate on the underlying source of truth, for example when reading a change
    //    to determine its primary storage. To support this, ReviewDbUtil#unwrapDb can detect and
    //    unwrap databases of this type.
    //
    // 2. After all changes have their primary storage in NoteDb, we can completely shut off access
    //    to the change tables. At this point in the migration, we are by definition not using the
    //    ReviewDb tables at all; we could even delete the tables at this point, and Gerrit would
    //    continue to function.
    //
    //    This is accomplished by setting the delegate ReviewDb *underneath* DisallowReadFromChanges
    //    to be a complete no-op, with NoChangesReviewDbWrapper. With this wrapper, all read
    //    operations return no results, and write operations silently do nothing. This wrapper is
    //    not a public class and nobody should ever attempt to unwrap it.

    if (migration.disableChangeReviewDb()) {
      db = new NoChangesReviewDbWrapper(db);
    }
    return new DisallowReadFromChangesReviewDbWrapper(db);
  }
}
