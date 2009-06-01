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

package com.google.gerrit.pgm;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;

import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Export system_config from schema version 11 to gerrit.config file. */
public class ConvertSystemConfig {
  public static void main(final String[] argv) throws OrmException,
      SQLException, IOException {
    final ReviewDb db = GerritServer.createDatabase().open();
    try {
      final Statement s = ((JdbcSchema) db).getConnection().createStatement();
      try {
        final ResultSet r = s.executeQuery("SELECT * FROM system_config");
        if (r.next()) {
          final File sitePath = new File(r.getString("site_path"));
          final File file = new File(sitePath, "gerrit.config");
          final RepositoryConfig config = new RepositoryConfig(null, file);
          String action;
          try {
            config.load();
            action = "Updated";
          } catch (FileNotFoundException noFile) {
            action = "Created";
          }
          export(config, r);
          config.save();
          System.err.println(action + " " + file);
        }
      } finally {
        s.close();
      }
    } finally {
      db.close();
    }
  }

  private static void export(RepositoryConfig config, ResultSet rs)
      throws SQLException {
    sshd(config, rs);
  }

  private static void sshd(RepositoryConfig config, ResultSet rs)
      throws SQLException {
    int port = rs.getInt("sshd_port");
    if (port == 29418) {
      config.unsetString("sshd", null, "listenaddress");
    } else {
      config.setString("sshd", null, "listenaddress", "*:" + port);
    }
  }

  private static void copy(RepositoryConfig config, String section, String key,
      ResultSet rs, String colName) throws SQLException {
    final String value = rs.getString(colName);
    if (value != null) {
      config.setString(section, null, key, value);
    } else {
      config.unsetString(section, null, key);
    }
  }
}
