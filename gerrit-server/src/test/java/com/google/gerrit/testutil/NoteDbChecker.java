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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class NoteDbChecker {
  private final Provider<ReviewDb> dbProvider;
  private final TestNotesMigration notesMigration;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeRebuilder changeRebuilder;
  private final PatchLineCommentsUtil plcUtil;

  @Inject
  NoteDbChecker(Provider<ReviewDb> dbProvider,
      TestNotesMigration notesMigration,
      ChangeNotes.Factory notesFactory,
      ChangeRebuilder changeRebuilder,
      PatchLineCommentsUtil plcUtil) {
    this.dbProvider = dbProvider;
    this.notesMigration = notesMigration;
    this.notesFactory = notesFactory;
    this.changeRebuilder = changeRebuilder;
    this.plcUtil = plcUtil;
  }

  public void checkAllChanges() throws Exception {
    checkChanges(
        Iterables.transform(
            unwrapDb().changes().all(),
            ReviewDbUtil.changeIdFunction()));
  }

  public void checkChanges(Change.Id... changeIds) throws Exception {
    checkChanges(Arrays.asList(changeIds));
  }

  public void checkChanges(Iterable<Change.Id> changeIds) throws Exception {
    ReviewDb db = unwrapDb();

    notesMigration.setReadChanges(false);
    List<Change.Id> sortedIds =
        ReviewDbUtil.intKeyOrdering().sortedCopy(changeIds);
    List<ChangeBundle> allExpected = new ArrayList<>();
    for (Change.Id id : sortedIds) {
      allExpected.add(ChangeBundle.fromReviewDb(db, id));
    }

    notesMigration.setWriteChanges(true);
    notesMigration.setReadChanges(true);
    for (ChangeBundle expected : allExpected) {
      Change c = expected.getChange();
      changeRebuilder.rebuild(db, c.getId());
      ChangeBundle actual = ChangeBundle.fromNotes(
          plcUtil, notesFactory.create(db, c.getProject(), c.getId()));
      List<String> diff = expected.differencesFrom(actual);
      if (!diff.isEmpty()) {
        throw new AssertionError(
            "Differences between ReviewDb and NoteDb for " + c + ":\n"
            + Joiner.on('\n').join(diff));
      } else {
        System.err.println(
            "NoteDb conversion of change " + c.getId() + " successful");
      }
    }
  }

  private ReviewDb unwrapDb() {
    ReviewDb db = dbProvider.get();
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }
}
