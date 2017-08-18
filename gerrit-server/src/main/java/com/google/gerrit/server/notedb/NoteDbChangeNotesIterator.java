package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ScanResult;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NoteDbChangeNotesIterator implements ChangeNotesIterator {
  private static final Logger log = LoggerFactory.getLogger(NoteDbChangeNotesIterator.class);

  private final ChangeNotes.Factory factory;
  private final Repository repo;
  private final ReviewDb db;
  private final Project.NameKey project;
  private final PrimaryStorage defaultStorage;

  private ScanResult sr;
  private Iterator<Change.Id> all;
  private ChangeNotes next;
  private boolean failed;

  NoteDbChangeNotesIterator(
      ChangeNotes.Factory factory,
      Repository repo,
      ReviewDb db,
      Project.NameKey project,
      PrimaryStorage defaultStorage) {
    this.factory = factory;
    this.project = project;
    this.repo = repo;
    this.db = db;
    this.defaultStorage = defaultStorage;
  }

  @Override
  public boolean hasNext() throws OrmException, IOException {
    if (failed) {
      return false;
    }
    if (next != null) {
      return true;
    }
    boolean ok = false;
    try {
      if (sr == null) {
        sr = ChangeNotes.Factory.scanChangeIds(repo);
        all = sr.all().iterator();
      }

      while (all.hasNext()) {
        Change.Id id = all.next();
        Change change;
        try {
          change = ChangeNotes.readOneReviewDbChange(db, id);
        } catch (OrmException e) {
          throw new NextChangeNotesException(id, e);
        }

        if (change == null) {
          if (!sr.fromMetaRefs().contains(id)) {
            // Stray patch set refs can happen due to normal error conditions, e.g. failed push
            // processing, so aren't worth even a warning.
            continue;
          }
          if (defaultStorage == PrimaryStorage.REVIEW_DB) {
            // If changes should exist in ReviewDb, it's worth warning about a meta ref with no
            // corresponding ReviewDb data.
            log.warn("skipping change {} found in project {} but not in ReviewDb", id, project);
            continue;
          }
          // TODO(dborowitz): See discussion in NoteDbBatchUpdate#newChangeContext.
          change = ChangeNotes.Factory.newNoteDbOnlyChange(project, id);
        } else if (!change.getProject().equals(project)) {
          log.error(
              "skipping change {} found in project {} because ReviewDb change has project {}",
              id,
              project,
              change.getProject());
          continue;
        }
        log.debug("adding change {} found in project {}", id, project);
        next = factory.createFromChangeOnlyWithProperNoteDbState(change);
        ok = true;
        return true;
      }
      ok = true;
      return false;
    } finally {
      if (!ok) {
        failed = true;
      }
    }
  }

  @Override
  public ChangeNotes next() throws OrmException, IOException {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    ChangeNotes n = checkNotNull(next);
    next = null;
    return n;
  }
}
