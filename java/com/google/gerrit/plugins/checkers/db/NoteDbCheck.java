package com.google.gerrit.plugins.checkers.db;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checkers.Check;
import com.google.gerrit.plugins.checkers.CheckKey;
import com.google.gerrit.plugins.checkers.CheckUpdate;
import com.google.gerrit.plugins.checkers.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.sql.Timestamp;
import java.util.Objects;

/** Representation of {@link Check} that can be serialized with GSON. */
class NoteDbCheck {

  private NoteDbCheck() {}

  public String checkerUUID;

  public CheckState state;
  @Nullable public String url;
  @Nullable public Timestamp started;
  @Nullable public Timestamp finished;

  public Timestamp created;
  public Timestamp updated;

  Check toCheck(Project.NameKey project, PatchSet.Id patchSetId) {
    CheckKey key = CheckKey.create(project, patchSetId, checkerUUID);
    Check.Builder newCheck =
        Check.builder(key).setState(state).setCreated(created).setUpdated(updated);
    if (url != null) {
      newCheck.setUrl(url);
    }
    if (started != null) {
      newCheck.setStarted(started);
    }
    if (finished != null) {
      newCheck.setFinished(finished);
    }
    return newCheck.build();
  }

  static NoteDbCheck fromCheckCreation(CheckKey checkCreation, CheckUpdate checkUpdate) {
    NoteDbCheck noteDbCheck = new NoteDbCheck();
    noteDbCheck.checkerUUID = checkCreation.checkerUUID();
    noteDbCheck.applyUpdate(checkUpdate);
    return noteDbCheck;
  }

  /**
   * Applies the given update and returns {@code true} if at least a single fields value was changed
   * to a different value, {@code false} otherwise. Does not update timestamps.
   */
  boolean applyUpdate(CheckUpdate update) {
    boolean modified = false;
    if (!Objects.equals(update.state().orElse(null), state)) {
      state = update.state().get();
      modified = true;
    }
    if (!Objects.equals(update.url().orElse(null), url)) {
      url = update.url().get();
      modified = true;
    }
    if (!Objects.equals(update.started().orElse(null), started)) {
      started = update.started().get();
      modified = true;
    }
    if (!Objects.equals(update.finished().orElse(null), finished)) {
      finished = update.finished().get();
      modified = true;
    }
    return modified;
  }
}
