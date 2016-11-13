// Copyright (C) 2014 The Android Open Source Project
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
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class Schema_95 extends SchemaVersion {
  private final AllUsersCreator allUsersCreator;

  @Inject
  Schema_95(Provider<Schema_94> prior, AllUsersCreator allUsersCreator) {
    super(prior);
    this.allUsersCreator = allUsersCreator;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try {
      allUsersCreator.create();
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException(e);
    }
  }
}
