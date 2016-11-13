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

package com.google.gerrit.server.change;

import static java.util.stream.Collectors.joining;

import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.Rebuild.Input;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class Rebuild implements RestModifyView<ChangeResource, Input> {
  public static class Input {}

  private final Provider<ReviewDb> db;
  private final NotesMigration migration;
  private final ChangeRebuilder rebuilder;
  private final ChangeBundleReader bundleReader;
  private final CommentsUtil commentsUtil;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  Rebuild(
      Provider<ReviewDb> db,
      NotesMigration migration,
      ChangeRebuilder rebuilder,
      ChangeBundleReader bundleReader,
      CommentsUtil commentsUtil,
      ChangeNotes.Factory notesFactory) {
    this.db = db;
    this.migration = migration;
    this.rebuilder = rebuilder;
    this.bundleReader = bundleReader;
    this.commentsUtil = commentsUtil;
    this.notesFactory = notesFactory;
  }

  @Override
  public BinaryResult apply(ChangeResource rsrc, Input input)
      throws ResourceNotFoundException, IOException, OrmException, ConfigInvalidException {
    if (!migration.commitChangeWrites()) {
      throw new ResourceNotFoundException();
    }
    if (!migration.readChanges()) {
      // ChangeBundle#fromNotes currently doesn't work if reading isn't enabled,
      // so don't attempt a diff.
      rebuild(rsrc);
      return BinaryResult.create("Rebuilt change successfully");
    }

    // Not the same transaction as the rebuild, so may result in spurious diffs
    // in the case of races. This should be easy enough to detect by rerunning.
    ChangeBundle reviewDbBundle =
        bundleReader.fromReviewDb(ReviewDbUtil.unwrapDb(db.get()), rsrc.getId());
    rebuild(rsrc);
    ChangeNotes notes = notesFactory.create(db.get(), rsrc.getChange().getProject(), rsrc.getId());
    ChangeBundle noteDbBundle = ChangeBundle.fromNotes(commentsUtil, notes);
    List<String> diffs = reviewDbBundle.differencesFrom(noteDbBundle);
    if (diffs.isEmpty()) {
      return BinaryResult.create("No differences between ReviewDb and NoteDb");
    }
    return BinaryResult.create(
        diffs.stream().collect(joining("\n", "Differences between ReviewDb and NoteDb:\n", "\n")));
  }

  private void rebuild(ChangeResource rsrc)
      throws ResourceNotFoundException, ConfigInvalidException, OrmException, IOException {
    try {
      rebuilder.rebuild(db.get(), rsrc.getId());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(IdString.fromDecoded(rsrc.getId().toString()));
    }
  }
}
