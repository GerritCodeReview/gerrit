// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

/** Detail necessary to display{@link ChangeScreen}. */
public class ChangeDetail {
  protected Change change;
  protected PatchSet currentPatchSet;
  protected PatchSetInfo currentPatchSetInfo;

  public ChangeDetail() {
  }

  public void load(final Change c, final ReviewDb db) throws OrmException {
    change = c;

    final PatchSet.Id ps = change.currentPatchSetId();
    if (ps != null) {
      currentPatchSet = db.patchSets().get(ps);
      currentPatchSetInfo = db.patchSetInfo().get(ps);
    }
  }

  public Change getChange() {
    return change;
  }

  public PatchSet getCurrentPatchSet() {
    return currentPatchSet;
  }

  public PatchSetInfo getCurrentPatchSetInfo() {
    return currentPatchSetInfo;
  }

  public String getDescription() {
    return currentPatchSetInfo != null ? currentPatchSetInfo.getMessage() : "";
  }
}
