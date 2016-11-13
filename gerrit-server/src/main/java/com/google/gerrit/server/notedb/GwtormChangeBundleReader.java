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

package com.google.gerrit.server.notedb;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.ChangeBundle.Source;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class GwtormChangeBundleReader implements ChangeBundleReader {
  @Inject
  GwtormChangeBundleReader() {}

  @Override
  public ChangeBundle fromReviewDb(ReviewDb db, Change.Id id) throws OrmException {
    db.changes().beginTransaction(id);
    try {
      List<PatchSetApproval> approvals = db.patchSetApprovals().byChange(id).toList();
      return new ChangeBundle(
          db.changes().get(id),
          db.changeMessages().byChange(id),
          db.patchSets().byChange(id),
          approvals,
          db.patchComments().byChange(id),
          ReviewerSet.fromApprovals(approvals),
          Source.REVIEW_DB);
    } finally {
      db.rollback();
    }
  }
}
