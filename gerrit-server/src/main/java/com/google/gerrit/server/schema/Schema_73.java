// Copyright (C) 2012 The Android Open Source Project
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
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_73 extends SchemaVersion {
  @Inject
  Schema_73(Provider<Schema_72> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(final ReviewDb db, final UpdateUI ui)
      throws SQLException {
    try (Statement stmt = newStatement(db)) {
      stmt.executeUpdate("CREATE INDEX change_messages_byPatchset ON change_messages (patchset_change_id, patchset_patch_set_id )");
    }
  }
}
