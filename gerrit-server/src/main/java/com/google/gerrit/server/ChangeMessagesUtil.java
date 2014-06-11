package com.google.gerrit.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
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
  public static List<ChangeMessage> sortChangeMessages(
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
    List<ChangeMessage> changeMessages;
    if (!migration.readChangeMessages()) {
      ImmutableListMultimap.Builder<PatchSet.Id, ChangeMessage> result =
          ImmutableListMultimap.builder();
      for (ChangeMessage cm
          : db.changeMessages().byChange(notes.getChangeId())) {
        result.put(cm.getPatchSetId(), cm);
      }
      changeMessages = sortChangeMessages(result.build().values());
    } else {
      changeMessages =
          sortChangeMessages(notes.load().getChangeMessages().values());
    }
    return changeMessages;

  }


  public List<ChangeMessage> byPatchSet(ReviewDb db, ChangeNotes notes,
      PatchSet.Id psId) throws OrmException {
    if (!migration.readChangeMessages()) {
      return sortChangeMessages(db.changeMessages().byPatchSet(psId));
    }
    return notes.load().getChangeMessages().get(psId);
  }

  public void addChangeMessage(ReviewDb db, ChangeUpdate update,
      ChangeMessage changeMessage) throws OrmException {
    if (changeMessage != null) {
      update.setChangeMessage(changeMessage.getMessage());
      db.changeMessages().insert(Collections.singleton(changeMessage));
    }
  }
}