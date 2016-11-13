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

package com.google.gerrit.server.notedb.rebuild;

import com.google.common.collect.Table;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.sql.Timestamp;

class ReviewerEvent extends Event {
  private Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> reviewer;

  ReviewerEvent(
      Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> reviewer,
      Timestamp changeCreatedOn) {
    super(
        // Reviewers aren't generally associated with a particular patch set
        // (although as an implementation detail they were in ReviewDb). Just
        // use the latest patch set at the time of the event.
        null,
        reviewer.getColumnKey(),
        // TODO(dborowitz): Real account ID shouldn't really matter for
        // reviewers, but we might have to deal with this to avoid ChangeBundle
        // diffs when run against real data.
        reviewer.getColumnKey(),
        reviewer.getValue(),
        changeCreatedOn,
        null);
    this.reviewer = reviewer;
  }

  @Override
  boolean uniquePerUpdate() {
    return false;
  }

  @Override
  void apply(ChangeUpdate update) throws IOException, OrmException {
    checkUpdate(update);
    update.putReviewer(reviewer.getColumnKey(), reviewer.getRowKey());
  }
}
