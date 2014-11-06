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

package com.google.gerrit.pgm.init;

import com.google.common.base.Joiner;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.ColumnModel;
import com.google.gwtorm.schema.RelationModel;
import com.google.gwtorm.schema.java.JavaSchemaModel;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdatePrimaryKeys implements InitStep {

  private final ConsoleUI ui;

  private SchemaFactory<ReviewDb> dbFactory;

  private ReviewDb db;
  private Connection conn;

  @Inject
  UpdatePrimaryKeys(ConsoleUI ui) {
    this.ui = ui;
  }

  @Override
  public void run() throws Exception {
  }

  @Override
  public void postRun() throws Exception {
    db = dbFactory.open();
    try {
      conn = ((JdbcSchema) db).getConnection();
      Map<String, List<String>> corrections = findPKUpdates();
      if (corrections.isEmpty()) {
        return;
      }

      ui.header("Database Primary Keys");
      if (ui.yesno(true, "Fix primary keys column order")) {
        ui.message("fixing primary keys...\n");
        JdbcExecutor executor = new JdbcExecutor(conn);
        for (Map.Entry<String, List<String>> c : corrections.entrySet()) {
          ui.message("  table: %s ... ", c.getKey());
          recreatePK(executor, c.getKey(), c.getValue());
          ui.message("done\n");
        }
        ui.message("done\n");
      }
    } finally {
      db.close();
    }
  }

  @Inject(optional = true)
  void setSchemaFactory(SchemaFactory<ReviewDb> dbFactory) {
    this.dbFactory = dbFactory;
  }

  private Map<String, List<String>> findPKUpdates()
      throws OrmException, SQLException {
    Map<String, List<String>> corrections = new HashMap<>();
    ReviewDb db = dbFactory.open();
    try {
      DatabaseMetaData meta = conn.getMetaData();
      JavaSchemaModel jsm = new JavaSchemaModel(ReviewDb.class);
      for (RelationModel rm : jsm.getRelations()) {
        String tableName = rm.getRelationName();
        List<String> relationPK = relationPK(rm);
        List<String> tablePK = dbTablePK(meta, tableName);
        if (!relationPK.equals(tablePK)) {
          corrections.put(tableName, relationPK);
        }
      }
      return corrections;
    } finally {
      db.close();
    }
  }


  private List<String> relationPK(RelationModel rm)
      throws OrmException, SQLException {
    List<String> pk = new ArrayList<>();
    for (ColumnModel cm : rm.getPrimaryKeyColumns()) {
      pk.add(cm.getColumnName().toUpperCase());
    }
    return pk;
  }

  private List<String> dbTablePK(DatabaseMetaData meta, String tableName)
      throws OrmException, SQLException {
    ResultSet cols = meta.getPrimaryKeys(null, null, tableName.toUpperCase());
    Map<Short, String> seqToName = new HashMap<>();
    while (cols.next()) {
      seqToName.put(cols.getShort("KEY_SEQ"), cols.getString("COLUMN_NAME"));
    }

    List<String> pk = new ArrayList<>();
    for (short i = 1; i <= seqToName.size(); i++) {
      pk.add(seqToName.get(i).toUpperCase());
    }
    return pk;
  }

  private void recreatePK(StatementExecutor executor, String tableName,
      List<String> cols) throws OrmException {
    try {
      executor.execute("ALTER TABLE " + tableName + " DROP PRIMARY KEY");
    } catch (OrmException ignore) {
      // maybe the primary key was dropped in a previous run but the creation failed
    }
    executor.execute("ALTER TABLE " + tableName
        + " ADD PRIMARY KEY(" + Joiner.on(",").join(cols) + ")");
  }
}
