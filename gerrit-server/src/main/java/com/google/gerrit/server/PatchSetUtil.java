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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Utilities for manipulating patch sets. */
@Singleton
public class PatchSetUtil {
  @Inject
  PatchSetUtil() {
  }

  public PatchSet latest(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    return get(db, notes, notes.getChange().currentPatchSetId());
  }

  @SuppressWarnings("unused") // TODO(dborowitz): Read from notedb.
  public PatchSet get(ReviewDb db, ChangeNotes notes, PatchSet.Id psId)
      throws OrmException {
    return db.patchSets().get(psId);
  }

  public ImmutableList<PatchSet> byChange(ReviewDb db, ChangeNotes notes)
      throws OrmException {
    return ChangeUtil.PS_ID_ORDER.immutableSortedCopy(
        db.patchSets().byChange(notes.getChangeId()));
  }
}
