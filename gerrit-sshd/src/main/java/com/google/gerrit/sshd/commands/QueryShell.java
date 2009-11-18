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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.Version;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Simple interactive SQL query tool. */
public class QueryShell {
  public interface Factory {
    QueryShell create(@Assisted InputStream in, @Assisted OutputStream out);
  }

  private final BufferedReader in;
  private final PrintWriter out;
  private final SchemaFactory<ReviewDb> dbFactory;

  private ReviewDb db;
  private Connection connection;
  private Statement statement;

  @Inject
  QueryShell(final SchemaFactory<ReviewDb> dbFactory,

  @Assisted final InputStream in, @Assisted final OutputStream out)
      throws UnsupportedEncodingException {
    this.dbFactory = dbFactory;
    this.in = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    this.out = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
  }

  public void run() {
    try {
      db = dbFactory.open();
      try {
        connection = ((JdbcSchema) db).getConnection();
        connection.setAutoCommit(true);

        statement = connection.createStatement();
        try {
          showBanner();
          readEvalPrintLoop();
        } finally {
          statement.close();
          statement = null;
        }
      } finally {
        db.close();
        db = null;
      }
    } catch (OrmException err) {
      out.println("fatal: Cannot open connection: " + err.getMessage());

    } catch (SQLException err) {
      out.println("fatal: Cannot open connection: " + err.getMessage());
    } finally {
      out.flush();
    }
  }

  private void readEvalPrintLoop() {
    final StringBuilder buffer = new StringBuilder();
    boolean executed = false;
    for (;;) {
      print(buffer.length() == 0 || executed ? "gerrit> " : "     -> ");
      String line = readLine();
      if (line == null) {
        return;
      }

      if (line.startsWith("\\")) {
        // Shell command, check the various cases we recognize
        //
        line = line.substring(1);
        if (line.equals("h") || line.equals("?")) {
          showHelp();

        } else if (line.equals("q")) {
          println("Bye");
          return;

        } else if (line.equals("r")) {
          buffer.setLength(0);
          executed = false;

        } else if (line.equals("p")) {
          println(buffer.toString());

        } else if (line.equals("g")) {
          if (buffer.length() > 0) {
            executeStatement(buffer.toString());
            executed = true;
          }

        } else if (line.equals("d")) {
          listTables();

        } else if (line.startsWith("d ")) {
          showTable(line.substring(2).trim());

        } else {
          println("ERROR: '\\" + line + "' not supported");
          println("");
          showHelp();
        }
        continue;
      }

      if (executed) {
        buffer.setLength(0);
        executed = false;
      }
      if (buffer.length() > 0) {
        buffer.append('\n');
      }
      buffer.append(line);

      if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == ';') {
        executeStatement(buffer.toString());
        executed = true;
      }
    }
  }

  private void listTables() {
    final DatabaseMetaData meta;
    try {
      meta = connection.getMetaData();
    } catch (SQLException e) {
      error(e);
      return;
    }

    try {
      final String[] types = {"TABLE", "VIEW"};
      ResultSet rs = meta.getTables(null, null, null, types);
      try {
        println("                     List of relations");
        showResultSet(rs, "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE");
      } finally {
        rs.close();
      }
    } catch (SQLException e) {
      error(e);
    }

    println("");
  }

  private void showTable(final String tableName) {
    final DatabaseMetaData meta;
    try {
      meta = connection.getMetaData();
    } catch (SQLException e) {
      error(e);
      return;
    }

    try {
      ResultSet rs = meta.getColumns(null, null, tableName, null);
      try {
        println("                     Table " + tableName);
        showResultSet(rs, "COLUMN_NAME", "TYPE_NAME");
      } finally {
        rs.close();
      }
    } catch (SQLException e) {
      error(e);
    }

    try {
      ResultSet rs = meta.getIndexInfo(null, null, tableName, false, true);
      try {
        if (rs.next()) {
          println("");
          println("Indexes on " + tableName + ":");
          showResultSet(rs, "INDEX_NAME", "NON_UNIQUE", "COLUMN_NAME");
        }
      } finally {
        rs.close();
      }
    } catch (SQLException e) {
      error(e);
    }

    println("");
  }

  private void executeStatement(final String sql) {
    final long start = System.currentTimeMillis();
    final boolean hasResultSet;
    try {
      hasResultSet = statement.execute(sql);
    } catch (SQLException e) {
      error(e);
      return;
    }

    try {
      if (hasResultSet) {
        final ResultSet rs = statement.getResultSet();
        try {
          final int rowCount = showResultSet(rs);
          final long ms = System.currentTimeMillis() - start;
          println("(" + rowCount + (rowCount == 1 ? " row" : " rows") //
              + "; " + ms + " ms)");
          println("");
        } finally {
          rs.close();
        }

      } else {
        final int updateCount = statement.getUpdateCount();
        final long ms = System.currentTimeMillis() - start;
        println("UPDATE " + updateCount + "; " + ms + " ms");
      }
    } catch (SQLException e) {
      error(e);
    }
  }

  private int showResultSet(final ResultSet rs, String... show)
      throws SQLException {
    final ResultSetMetaData meta = rs.getMetaData();

    final int[] columnMap;
    if (show != null && 0 < show.length) {
      final int colCnt = meta.getColumnCount();
      columnMap = new int[show.length];
      for (int colId = 0; colId < colCnt; colId++) {
        final String name = meta.getColumnName(colId + 1);
        for (int j = 0; j < show.length; j++) {
          if (show[j].equalsIgnoreCase(name)) {
            columnMap[j] = colId + 1;
            break;
          }
        }
      }
    } else {
      final int colCnt = meta.getColumnCount();
      columnMap = new int[colCnt];
      for (int colId = 0; colId < colCnt; colId++)
        columnMap[colId] = colId + 1;
    }

    final int colCnt = columnMap.length;
    final String[] names = new String[colCnt];
    final int[] widths = new int[colCnt];
    for (int c = 0; c < colCnt; c++) {
      final int colId = columnMap[c];
      names[c] = meta.getColumnLabel(colId);
      widths[c] = names[c].length();
    }

    final List<String[]> rows = new ArrayList<String[]>();
    while (rs.next()) {
      final String[] row = new String[columnMap.length];
      for (int c = 0; c < colCnt; c++) {
        final int colId = columnMap[c];
        String val = rs.getString(colId);
        if (val == null) {
          val = "NULL";
        }
        row[c] = val;
        widths[c] = Math.max(widths[c], val.length());
      }
      rows.add(row);
    }

    final StringBuilder b = new StringBuilder();
    for (int c = 0; c < colCnt; c++) {
      final int colId = columnMap[c];
      if (0 < c) {
        b.append(" | ");
      }

      String n = names[c];
      if (widths[c] < n.length()) {
        n = n.substring(0, widths[c]);
      }
      b.append(n);

      if (c < colCnt - 1) {
        for (int pad = n.length(); pad < widths[c]; pad++) {
          b.append(' ');
        }
      }
    }
    println(" " + b.toString());

    b.setLength(0);
    for (int c = 0; c < colCnt; c++) {
      if (0 < c) {
        b.append("-+-");
      }
      for (int pad = 0; pad < widths[c]; pad++) {
        b.append('-');
      }
    }
    println(" " + b.toString());

    boolean dataTruncated = false;
    for (String[] row : rows) {
      b.setLength(0);
      b.append(' ');

      for (int c = 0; c < colCnt; c++) {
        final int colId = columnMap[c];
        final int max = widths[c];
        if (0 < c) {
          b.append(" | ");
        }

        String s = row[c];
        if (1 < colCnt && max < s.length()) {
          s = s.substring(0, max);
          dataTruncated = true;
        }
        b.append(s);

        if (c < colCnt - 1) {
          for (int pad = s.length(); pad < max; pad++) {
            b.append(' ');
          }
        }
      }
      println(b.toString());
    }

    if (dataTruncated) {
      warning("some column data was truncated");
    }
    return rows.size();
  }

  private void warning(final String msg) {
    println("WARNING: " + msg);
  }

  private void error(final SQLException err) {
    println("ERROR: " + err.getMessage());
  }

  private void print(String s) {
    out.print(s);
    out.flush();
  }

  private void println(String s) {
    out.print(s);
    out.print('\n');
    out.flush();
  }

  private String readLine() {
    try {
      return in.readLine();
    } catch (IOException e) {
      return null;
    }
  }

  private void showBanner() {
    println("Welcome to Gerrit Code Review " + Version.getVersion());
    try {
      print("(");
      print(connection.getMetaData().getDatabaseProductName());
      print(" ");
      print(connection.getMetaData().getDatabaseProductVersion());
      println(")");
    } catch (SQLException err) {
      error(err);
    }
    println("");
    println("Type '\\h' for help.  Type '\\r' to clear the buffer.");
    println("");
  }

  private void showHelp() {
    final StringBuilder help = new StringBuilder();
    help.append("General\n");
    help.append("  \\q        quit\n");

    help.append("\n");
    help.append("Query Buffer\n");
    help.append("  \\g        execute the query buffer\n");
    help.append("  \\p        display the current buffer\n");
    help.append("  \\r        clear the query buffer\n");

    help.append("\n");
    help.append("Informational\n");
    help.append("  \\d        list all tables\n");
    help.append("  \\d NAME   describe table\n");

    help.append("\n");
    print(help.toString());
  }
}
