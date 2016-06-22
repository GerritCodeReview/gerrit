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

package com.google.gerrit.testutil;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class NoteDbChecker {
  static final Logger log = LoggerFactory.getLogger(NoteDbChecker.class);

  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final TestNotesMigration notesMigration;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeRebuilder changeRebuilder;
  private final PatchLineCommentsUtil plcUtil;

  @Inject
  NoteDbChecker(Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      TestNotesMigration notesMigration,
      ChangeNotes.Factory notesFactory,
      ChangeRebuilder changeRebuilder,
      PatchLineCommentsUtil plcUtil) {
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.notesMigration = notesMigration;
    this.notesFactory = notesFactory;
    this.changeRebuilder = changeRebuilder;
    this.plcUtil = plcUtil;
  }

  public void rebuildAndCheckAllChanges() throws Exception {
    rebuildAndCheckChanges(
        Iterables.transform(
            getUnwrappedDb().changes().all(),
            ReviewDbUtil.changeIdFunction()));
  }

  public void rebuildAndCheckChanges(Change.Id... changeIds) throws Exception {
    rebuildAndCheckChanges(Arrays.asList(changeIds));
  }

  public void rebuildAndCheckChanges(Iterable<Change.Id> changeIds)
      throws Exception {
    ReviewDb db = getUnwrappedDb();

    List<ChangeBundle> allExpected = readExpected(changeIds);

    boolean oldWrite = notesMigration.writeChanges();
    boolean oldRead = notesMigration.readChanges();
    try {
      notesMigration.setWriteChanges(true);
      notesMigration.setReadChanges(true);
      List<String> msgs = new ArrayList<>();
      for (ChangeBundle expected : allExpected) {
        Change c = expected.getChange();
        try {
          changeRebuilder.rebuild(db, c.getId());
        } catch (RepositoryNotFoundException e) {
          msgs.add("Repository not found for change, cannot convert: " + c);
        }
      }

      checkActual(allExpected, msgs);
    } finally {
      notesMigration.setReadChanges(oldRead);
      notesMigration.setWriteChanges(oldWrite);
    }
  }

  public void checkChanges(Change.Id... changeIds) throws Exception {
    checkChanges(Arrays.asList(changeIds));
  }

  public void checkChanges(Iterable<Change.Id> changeIds) throws Exception {
    checkActual(readExpected(changeIds), new ArrayList<String>());
  }

  public void assertNoChangeRef(Project.NameKey project, Change.Id changeId)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNull();
    }
  }

  private List<ChangeBundle> readExpected(Iterable<Change.Id> changeIds)
      throws Exception {
    ReviewDb db = getUnwrappedDb();
    boolean old = notesMigration.readChanges();
    try {
      notesMigration.setReadChanges(false);
      List<Change.Id> sortedIds =
          ReviewDbUtil.intKeyOrdering().sortedCopy(changeIds);
      List<ChangeBundle> expected = new ArrayList<>(sortedIds.size());
      for (Change.Id id : sortedIds) {
        expected.add(ChangeBundle.fromReviewDb(db, id));
      }
      return expected;
    } finally {
      notesMigration.setReadChanges(old);
    }
  }

  private void checkActual(List<ChangeBundle> allExpected, List<String> msgs)
      throws Exception {
    ReviewDb db = getUnwrappedDb();
    boolean oldRead = notesMigration.readChanges();
    boolean oldWrite = notesMigration.writeChanges();
    try {
      notesMigration.setWriteChanges(true);
      notesMigration.setReadChanges(true);
      for (ChangeBundle expected : allExpected) {
        Change c = expected.getChange();
        ChangeBundle actual;
        try {
          actual = ChangeBundle.fromNotes(
              plcUtil, notesFactory.create(db, c.getProject(), c.getId()));
        } catch (Throwable t) {
          String msg = "Error converting change: " + c;
          msgs.add(msg);
          log.error(msg, t);
          continue;
        }
        List<String> diff = expected.differencesFrom(actual);
        if (!diff.isEmpty()) {
          msgs.add("Differences between ReviewDb and NoteDb for " + c + ":");
          msgs.addAll(diff);
          msgs.add("");
        } else {
          System.err.println(
              "NoteDb conversion of change " + c.getId() + " successful");
        }
      }
    } finally {
      notesMigration.setReadChanges(oldRead);
      notesMigration.setWriteChanges(oldWrite);
    }
    if (!msgs.isEmpty()) {
      throw new AssertionError(Joiner.on('\n').join(msgs));
    }
  }

  private ReviewDb getUnwrappedDb() {
    ReviewDb db = dbProvider.get();
    return  ReviewDbUtil.unwrapDb(db);
  }
}
