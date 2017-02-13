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

/** Replace draft changes with private changes. */
public class Schema_143 extends SchemaVersion {
  @Inject
  Schema_143(Provider<Schema_142> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    ui.message("Replace draft changes to private changes ...");
    try (StatementExecutor e = newExecutor(db)) {
      // The migration ignores draft patch sets for regular changes.
      // Those draft patch sets are effectively published and turned to regular patch sets.
      e.execute("UPDATE changes SET is_private = 'Y' WHERE status = 'd'");
      e.execute("UPDATE changes SET status = 'n' WHERE status = 'd'");
    }
    ui.message("done");
  }
}
