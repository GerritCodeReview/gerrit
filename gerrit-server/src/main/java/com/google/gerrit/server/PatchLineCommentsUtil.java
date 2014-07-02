// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utility functions to manipulate PatchLineComments.
 * <p>
 * These methods either query for and update PatchLineComments in the NoteDb or
 * ReviewDb, depending on the state of the NotesMigration.
 */
@Singleton
public class PatchLineCommentsUtil {
  private final NotesMigration migration;

  @VisibleForTesting
  @Inject
  public PatchLineCommentsUtil(NotesMigration migration) {
    this.migration = migration;
  }

  public List<PatchLineComment> publishedByChangeFile(ReviewDb db,
      ChangeNotes notes, Change.Id changeId, String file) throws OrmException {
    if (!migration.readPublishedComments()) {
      return db.patchComments().publishedByChangeFile(changeId, file).toList();
    }
    notes.load();
    List<PatchLineComment> commentsOnFile = new ArrayList<PatchLineComment>();

    // We must iterate through all comments to find the ones on this file.
    addCommentsInFile(commentsOnFile, notes.getBaseComments().values(), file);
    addCommentsInFile(commentsOnFile, notes.getPatchSetComments().values(),
        file);

    Collections.sort(commentsOnFile, ChangeNotes.PatchLineCommentComparator);
    return commentsOnFile;
  }

  public List<PatchLineComment> publishedByPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readPublishedComments()) {
      return db.patchComments().publishedByPatchSet(psId).toList();
    }
    notes.load();
    List<PatchLineComment> commentsOnPs = new ArrayList<PatchLineComment>();
    commentsOnPs.addAll(notes.getPatchSetComments().get(psId));
    commentsOnPs.addAll(notes.getBaseComments().get(psId));
    return commentsOnPs;
  }

  private static Collection<PatchLineComment> addCommentsInFile(
      Collection<PatchLineComment> commentsOnFile,
      Collection<PatchLineComment> allComments,
      String file) {
    for (PatchLineComment c : allComments) {
      String currentFilename = c.getKey().getParentKey().getFileName();
      if (currentFilename.equals(file)) {
        commentsOnFile.add(c);
      }
    }
    return commentsOnFile;
  }

  public void addPublishedComments(ReviewDb db, ChangeUpdate update,
      Iterable<PatchLineComment> comments) throws OrmException {
    for (PatchLineComment c : comments) {
      update.putComment(c);
    }
    db.patchComments().upsert(comments);
  }
}
