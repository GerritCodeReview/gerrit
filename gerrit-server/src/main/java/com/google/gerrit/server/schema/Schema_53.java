// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectParent;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Collections;

public class Schema_53 extends SchemaVersion {
  @Inject
  Schema_53(Provider<Schema_52> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException
  {
    JdbcSchema jdbc = (JdbcSchema) db;

    Statement s = jdbc.getConnection().createStatement();
    try {
      ResultSet r = s.executeQuery("SELECT name, parent_name FROM projects");
      try {
        while (r.next()) {
          final Project.NameKey project = new Project.NameKey(r.getString(1));
          final Project.NameKey parent = new Project.NameKey(r.getString(2));
          if (parent.get() != null) {
            ProjectParent pp = new ProjectParent(project, parent);
            db.projectParents().insert(Collections.singleton(pp));
          }
        }
      } finally {
        r.close();
      }
    } finally {
      s.close();
    }
  }
}
