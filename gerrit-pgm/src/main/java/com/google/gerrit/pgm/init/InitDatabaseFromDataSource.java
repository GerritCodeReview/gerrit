// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.inject.Inject;

import javax.sql.DataSource;

class InitDatabaseFromDataSource implements InitStep {

  private final DataSource dataSource;
  private final Section database;

  @Inject
  public InitDatabaseFromDataSource(DataSource dataSource, Section.Factory sections) {
    this.dataSource = dataSource;
    this.database = sections.get("database", null);
  }

  @Override
  public void run() throws Exception {
    String name = dataSource.getConnection().getMetaData().getDatabaseProductName();

    if (name.equalsIgnoreCase("postgresql")) {
      database.set("type", "POSTGRESQL");
    }
  }
}
