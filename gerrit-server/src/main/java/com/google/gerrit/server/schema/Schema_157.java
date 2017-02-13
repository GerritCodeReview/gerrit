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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Migrate draft changes to private changes or wip. */
public class Schema_157 extends SchemaVersion {

  private static enum DraftWorkflowMigrationStrategy {
    PRIVATE,
    WORK_IN_PROGRESS
  }

  @Inject
  Schema_157(Provider<Schema_156> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    DraftWorkflowMigrationStrategy strategy = DraftWorkflowMigrationStrategy.PRIVATE;
    if (ui.yesno(
        false, "Migrate draft changes to work-in-progress changes (default is private)?")) {
      strategy = DraftWorkflowMigrationStrategy.WORK_IN_PROGRESS;
    }
    ui.message(
        String.format("Replace draft changes with %s changes ...", strategy.name().toLowerCase()));
    try (StatementExecutor e = newExecutor(db)) {
      String column =
          strategy == DraftWorkflowMigrationStrategy.PRIVATE ? "is_private" : "work_in_progress";
      e.execute(String.format("UPDATE changes SET %s = 'Y' WHERE status = 'd'", column));
      e.execute("UPDATE changes SET status = 'n' WHERE status = 'd'");
    }
    ui.message("done");
  }
}
