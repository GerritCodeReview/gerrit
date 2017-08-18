// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import org.eclipse.jgit.lib.Repository;

class ReviewDbChangeNotesIterator implements ChangeNotesIterator {
  // A batch size of N may overload get(Iterable), so use something smaller, but still >1.
  private static final int BATCH_SIZE = 30;

  private final ChangeNotes.Factory factory;
  private final Repository repo;
  private final ReviewDb db;
  private final Queue<Change> batch;

  private Iterator<Change.Id> all;
  private boolean failed;

  ReviewDbChangeNotesIterator(ChangeNotes.Factory factory, Repository repo, ReviewDb db) {
    this.factory = factory;
    this.repo = repo;
    this.db = ReviewDbUtil.unwrapDb(db);
    batch = new ArrayDeque<>(BATCH_SIZE);
  }

  @Override
  public boolean hasNext() throws OrmException, IOException {
    if (failed) {
      return false;
    }
    boolean ok = false;
    try {
      if (all == null) {
        // Scan IDs that might exist in ReviewDb, assuming that each change has at least one patch
        // set ref. Not all changes might exist: some patch set refs might have been written where
        // the corresponding ReviewDb write failed. These will be silently filtered out by the batch
        // get call below, which is intended.
        all = ChangeNotes.Factory.scanChangeIds(repo).fromPatchSetRefs().iterator();
      }
      if (batch.isEmpty() && all.hasNext()) {
        Iterables.addAll(
            batch, db.changes().get(Lists.newArrayList(Iterators.limit(all, BATCH_SIZE))));
      }
      boolean ret = !batch.isEmpty();
      ok = true;
      return ret;
    } finally {
      if (!ok) {
        failed = true;
      }
    }
  }

  @Override
  public ChangeNotes next() throws OrmException, IOException {
    hasNext();
    Change c = batch.remove();
    try {
      return factory.createFromChangeOnlyWhenNoteDbDisabled(c);
    } catch (OrmException e) {
      throw new NextChangeNotesException(c.getId(), e);
    }
  }
}
