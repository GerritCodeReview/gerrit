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
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.List;

/**
 * Utility functions to manipulate ChangeMessages.
 * <p>
 * These methods either query for and update ChangeMessages in the NoteDb or
 * ReviewDb, depending on the state of the NotesMigration.
 */
@Singleton
public class ChangeMessagesUtil {
  private static List<ChangeMessage> sortChangeMessages(
      Iterable<ChangeMessage> changeMessage) {
    return ChangeNotes.MESSAGE_BY_TIME.sortedCopy(changeMessage);
  }

  private final NotesMigration migration;

  @VisibleForTesting
  @Inject
  public ChangeMessagesUtil(NotesMigration migration) {
    this.migration = migration;
  }

  public List<ChangeMessage> byChange(ReviewDb db, ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return
          sortChangeMessages(db.changeMessages().byChange(notes.getChangeId()));
    } else {
      return notes.load().getChangeMessages();
    }
  }

  public Iterable<ChangeMessage> byPatchSet(ReviewDb db, ChangeNotes notes,
      PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return db.changeMessages().byPatchSet(psId);
    }
    return notes.load().getChangeMessagesByPatchSet().get(psId);
  }

  public void addChangeMessage(ReviewDb db, ChangeUpdate update,
      ChangeMessage changeMessage) throws OrmException {
    update.setChangeMessage(changeMessage.getMessage());
    db.changeMessages().insert(Collections.singleton(changeMessage));
  }
}
