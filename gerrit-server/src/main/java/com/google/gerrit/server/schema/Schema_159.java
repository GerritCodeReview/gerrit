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

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Migrate draft changes to private or wip changes. */
public class Schema_159 extends SchemaVersion {

  public static enum DraftWorkflowMigrationStrategy {
    PRIVATE,
    WORK_IN_PROGRESS
  }

  @Inject
  Schema_159(Provider<Schema_158> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    String strategy =
            ui.readString(
                    ui.getDraftMigrationStrategy().name().toLowerCase(),
                    Sets.newHashSet(
                            DraftWorkflowMigrationStrategy.PRIVATE.name().toLowerCase(),
                            DraftWorkflowMigrationStrategy.WORK_IN_PROGRESS.name().toLowerCase()),
                    "Choose draft changes migration strategy");
    ui.message(String.format("Replace draft changes with %s changes ...", strategy));
    try (StatementExecutor e = newExecutor(db)) {
      String column =
              strategy.equalsIgnoreCase(DraftWorkflowMigrationStrategy.PRIVATE.name())
                      ? "is_private"
                      : "work_in_progress";
      // Mark changes private/WIP and NEW if either:
      // * they have status DRAFT
      // * they have status NEW and have any draft patch sets
      e.execute(
          String.format(
              "UPDATE changes "
                  + "SET %s = 'Y', "
                  + "    status = 'n', "
                  + "    created_on = created_on "
                  + "WHERE status = 'd' "
                  + "  OR (status = 'n' "
                  + "      AND EXISTS "
                  + "        (SELECT * "
                  + "         FROM patch_sets "
                  + "         WHERE patch_sets.change_id = changes.change_id "
                  + "           AND patch_sets.draft = 'Y')) ",
              column));
    }
    ui.message("done");
  }
}
