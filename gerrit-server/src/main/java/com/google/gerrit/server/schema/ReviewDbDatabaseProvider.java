// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import javax.sql.DataSource;

/** Provides the {@code Database<ReviewDb>} database handle. */
final class ReviewDbDatabaseProvider implements Provider<Database<ReviewDb>> {
  private final DataSource datasource;

  @Inject
  ReviewDbDatabaseProvider(@Named("ReviewDb") final DataSource ds) {
    datasource = ds;
  }

  @Override
  public Database<ReviewDb> get() {
    try {
      return new Database<>(datasource, ReviewDb.class);
    } catch (OrmException e) {
      throw new ProvisionException("Cannot create ReviewDb", e);
    }
  }
}
