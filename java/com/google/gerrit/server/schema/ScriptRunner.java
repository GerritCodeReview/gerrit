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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CharMatcher;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Parses an SQL script from a resource file and later runs it. */
class ScriptRunner {
  private final String name;
  private final List<String> commands;

  static final ScriptRunner NOOP =
      new ScriptRunner(null, null) {
        @Override
        void run(ReviewDb db) {}
      };

  ScriptRunner(String scriptName, InputStream script) {
    this.name = scriptName;
    try {
      this.commands = script != null ? parse(script) : null;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot parse " + name, e);
    }
  }

  void run(ReviewDb db) throws OrmException {
    try {
      final JdbcSchema schema = (JdbcSchema) db;
      final Connection c = schema.getConnection();
      final SqlDialect dialect = schema.getDialect();
      try (Statement stmt = c.createStatement()) {
        for (String sql : commands) {
          try {
            if (!dialect.isStatementDelimiterSupported()) {
              sql = CharMatcher.is(';').trimTrailingFrom(sql);
            }
            stmt.execute(sql);
          } catch (SQLException e) {
            throw new OrmException("Error in " + name + ":\n" + sql, e);
          }
        }
      }
    } catch (SQLException e) {
      throw new OrmException("Cannot run statements for " + name, e);
    }
  }

  private List<String> parse(InputStream in) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8))) {
      String delimiter = ";";
      List<String> commands = new ArrayList<>();
      StringBuilder buffer = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        if (line.isEmpty()) {
          continue;
        }
        if (line.startsWith("--")) {
          continue;
        }

        if (buffer.length() == 0 && line.toLowerCase().startsWith("delimiter ")) {
          delimiter = line.substring("delimiter ".length()).trim();
          continue;
        }

        if (buffer.length() > 0) {
          buffer.append('\n');
        }
        buffer.append(line);

        if (isDone(delimiter, line, buffer)) {
          String cmd = buffer.toString();
          commands.add(cmd);
          buffer = new StringBuilder();
        }
      }
      if (buffer.length() > 0) {
        commands.add(buffer.toString());
      }
      return commands;
    }
  }

  private boolean isDone(String delimiter, String line, StringBuilder buffer) {
    if (";".equals(delimiter)) {
      return buffer.charAt(buffer.length() - 1) == ';';

    } else if (line.equals(delimiter)) {
      buffer.setLength(buffer.length() - delimiter.length());
      return true;

    } else {
      return false;
    }
  }
}
