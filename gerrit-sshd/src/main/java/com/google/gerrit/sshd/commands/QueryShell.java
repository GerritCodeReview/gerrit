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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.Version;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Simple interactive SQL query tool. */
public class QueryShell {
  public interface Factory {
    QueryShell create(@Assisted InputStream in, @Assisted OutputStream out);
  }

  public enum OutputFormat {
    PRETTY,
    JSON,
    JSON_SINGLE
  }

  private final BufferedReader in;
  private final PrintWriter out;
  private final SchemaFactory<ReviewDb> dbFactory;
  private OutputFormat outputFormat = OutputFormat.PRETTY;

  private ReviewDb db;
  private Connection connection;
  private Statement statement;

  @Inject
  QueryShell(
      final SchemaFactory<ReviewDb> dbFactory,
      @Assisted final InputStream in,
      @Assisted final OutputStream out) {
    this.dbFactory = dbFactory;
    this.in = new BufferedReader(new InputStreamReader(in, UTF_8));
    this.out = new PrintWriter(new OutputStreamWriter(out, UTF_8));
  }

  public void setOutputFormat(OutputFormat fmt) {
    outputFormat = fmt;
  }

  public void run() {
    try {
      db = ReviewDbUtil.unwrapDb(dbFactory.open());
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
    } catch (OrmException | SQLException err) {
      out.println("fatal: Cannot open connection: " + err.getMessage());
    } finally {
      out.flush();
    }
  }

  public void execute(String query) {
    try {
      db = dbFactory.open();
      try {
        connection = ((JdbcSchema) db).getConnection();
        connection.setAutoCommit(true);

        statement = connection.createStatement();
        try {
          executeStatement(query);
        } finally {
          statement.close();
          statement = null;
        }
      } finally {
        db.close();
        db = null;
      }
    } catch (OrmException | SQLException err) {
      out.println("fatal: Cannot open connection: " + err.getMessage());
    } finally {
      out.flush();
    }
  }

  private void readEvalPrintLoop() {
    final StringBuilder buffer = new StringBuilder();
    boolean executed = false;
    for (; ; ) {
      if (outputFormat == OutputFormat.PRETTY) {
        print(buffer.length() == 0 || executed ? "gerrit> " : "     -> ");
      }
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
          if (outputFormat == OutputFormat.PRETTY) {
            println("Bye");
          }
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
          final String msg = "'\\" + line + "' not supported";
          switch (outputFormat) {
            case JSON_SINGLE:
            case JSON:
              {
                final JsonObject err = new JsonObject();
                err.addProperty("type", "error");
                err.addProperty("message", msg);
                println(err.toString());
                break;
              }
            case PRETTY:
            default:
              println("ERROR: " + msg);
              println("");
              showHelp();
              break;
          }
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

    final String[] types = {"TABLE", "VIEW"};
    try (ResultSet rs = meta.getTables(null, null, null, types)) {
      if (outputFormat == OutputFormat.PRETTY) {
        println("                     List of relations");
      }
      showResultSet(
          rs,
          false,
          0,
          Identity.create(rs, "TABLE_SCHEM"),
          Identity.create(rs, "TABLE_NAME"),
          Identity.create(rs, "TABLE_TYPE"));
    } catch (SQLException e) {
      error(e);
    }

    println("");
  }

  private void showTable(String tableName) {
    final DatabaseMetaData meta;
    try {
      meta = connection.getMetaData();

      if (meta.storesUpperCaseIdentifiers()) {
        tableName = tableName.toUpperCase();
      } else if (meta.storesLowerCaseIdentifiers()) {
        tableName = tableName.toLowerCase();
      }
    } catch (SQLException e) {
      error(e);
      return;
    }

    try (ResultSet rs = meta.getColumns(null, null, tableName, null)) {
      if (!rs.next()) {
        throw new SQLException("Table " + tableName + " not found");
      }

      if (outputFormat == OutputFormat.PRETTY) {
        println("                     Table " + tableName);
      }
      showResultSet(
          rs,
          true,
          0,
          Identity.create(rs, "COLUMN_NAME"),
          new Function("TYPE") {
            @Override
            String apply(final ResultSet rs) throws SQLException {
              String type = rs.getString("TYPE_NAME");
              switch (rs.getInt("DATA_TYPE")) {
                case java.sql.Types.CHAR:
                case java.sql.Types.VARCHAR:
                  type += "(" + rs.getInt("COLUMN_SIZE") + ")";
                  break;
              }

              String def = rs.getString("COLUMN_DEF");
              if (def != null && !def.isEmpty()) {
                type += " DEFAULT " + def;
              }

              int nullable = rs.getInt("NULLABLE");
              if (nullable == DatabaseMetaData.columnNoNulls) {
                type += " NOT NULL";
              }
              return type;
            }
          });
    } catch (SQLException e) {
      error(e);
      return;
    }

    try (ResultSet rs = meta.getIndexInfo(null, null, tableName, false, true)) {
      Map<String, IndexInfo> indexes = new TreeMap<>();
      while (rs.next()) {
        final String indexName = rs.getString("INDEX_NAME");
        IndexInfo def = indexes.get(indexName);
        if (def == null) {
          def = new IndexInfo();
          def.name = indexName;
          indexes.put(indexName, def);
        }

        if (!rs.getBoolean("NON_UNIQUE")) {
          def.unique = true;
        }

        final int pos = rs.getInt("ORDINAL_POSITION");
        final String col = rs.getString("COLUMN_NAME");
        String desc = rs.getString("ASC_OR_DESC");
        if ("D".equals(desc)) {
          desc = " DESC";
        } else {
          desc = "";
        }
        def.addColumn(pos, col + desc);

        String filter = rs.getString("FILTER_CONDITION");
        if (filter != null && !filter.isEmpty()) {
          def.filter.append(filter);
        }
      }

      if (outputFormat == OutputFormat.PRETTY) {
        println("");
        println("Indexes on " + tableName + ":");
        for (IndexInfo def : indexes.values()) {
          println("  " + def);
        }
      }
    } catch (SQLException e) {
      error(e);
      return;
    }

    println("");
  }

  private void executeStatement(final String sql) {
    final long start = TimeUtil.nowMs();
    final boolean hasResultSet;
    try {
      hasResultSet = statement.execute(sql);
    } catch (SQLException e) {
      error(e);
      return;
    }

    try {
      if (hasResultSet) {
        try (ResultSet rs = statement.getResultSet()) {
          showResultSet(rs, false, start);
        }

      } else {
        final int updateCount = statement.getUpdateCount();
        final long ms = TimeUtil.nowMs() - start;
        switch (outputFormat) {
          case JSON_SINGLE:
          case JSON:
            {
              final JsonObject tail = new JsonObject();
              tail.addProperty("type", "update-stats");
              tail.addProperty("rowCount", updateCount);
              tail.addProperty("runTimeMilliseconds", ms);
              println(tail.toString());
              break;
            }

          case PRETTY:
          default:
            println("UPDATE " + updateCount + "; " + ms + " ms");
            break;
        }
      }
    } catch (SQLException e) {
      error(e);
    }
  }

  /**
   * Outputs a result set to stdout.
   *
   * @param rs ResultSet to show.
   * @param alreadyOnRow true if rs is already on the first row. false otherwise.
   * @param start Timestamp in milliseconds when executing the statement started. This timestamp is
   *     used to compute statistics about the statement. If no statistics should be shown, set it to
   *     0.
   * @param show Functions to map columns
   * @throws SQLException
   */
  private void showResultSet(final ResultSet rs, boolean alreadyOnRow, long start, Function... show)
      throws SQLException {
    switch (outputFormat) {
      case JSON_SINGLE:
      case JSON:
        showResultSetJson(rs, alreadyOnRow, start, show);
        break;
      case PRETTY:
      default:
        showResultSetPretty(rs, alreadyOnRow, start, show);
        break;
    }
  }

  /**
   * Outputs a result set to stdout in Json format.
   *
   * @param rs ResultSet to show.
   * @param alreadyOnRow true if rs is already on the first row. false otherwise.
   * @param start Timestamp in milliseconds when executing the statement started. This timestamp is
   *     used to compute statistics about the statement. If no statistics should be shown, set it to
   *     0.
   * @param show Functions to map columns
   * @throws SQLException
   */
  private void showResultSetJson(
      final ResultSet rs, boolean alreadyOnRow, long start, Function... show) throws SQLException {
    JsonArray collector = new JsonArray();
    final ResultSetMetaData meta = rs.getMetaData();
    final Function[] columnMap;
    if (show != null && 0 < show.length) {
      columnMap = show;

    } else {
      final int colCnt = meta.getColumnCount();
      columnMap = new Function[colCnt];
      for (int colId = 0; colId < colCnt; colId++) {
        final int p = colId + 1;
        final String name = meta.getColumnLabel(p);
        columnMap[colId] = new Identity(p, name);
      }
    }

    int rowCnt = 0;
    while (alreadyOnRow || rs.next()) {
      final JsonObject row = new JsonObject();
      final JsonObject cols = new JsonObject();
      for (Function function : columnMap) {
        String v = function.apply(rs);
        if (v == null) {
          continue;
        }
        cols.addProperty(function.name.toLowerCase(), v);
      }
      row.addProperty("type", "row");
      row.add("columns", cols);
      switch (outputFormat) {
        case JSON:
          println(row.toString());
          break;
        case JSON_SINGLE:
          collector.add(row);
          break;
        case PRETTY:
        default:
          final JsonObject obj = new JsonObject();
          obj.addProperty("type", "error");
          obj.addProperty("message", "Unsupported Json variant");
          println(obj.toString());
          return;
      }
      alreadyOnRow = false;
      rowCnt++;
    }

    JsonObject tail = null;
    if (start != 0) {
      tail = new JsonObject();
      tail.addProperty("type", "query-stats");
      tail.addProperty("rowCount", rowCnt);
      final long ms = TimeUtil.nowMs() - start;
      tail.addProperty("runTimeMilliseconds", ms);
    }

    switch (outputFormat) {
      case JSON:
        if (tail != null) {
          println(tail.toString());
        }
        break;
      case JSON_SINGLE:
        if (tail != null) {
          collector.add(tail);
        }
        println(collector.toString());
        break;
      case PRETTY:
      default:
        final JsonObject obj = new JsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", "Unsupported Json variant");
        println(obj.toString());
    }
  }

  /**
   * Outputs a result set to stdout in plain text format.
   *
   * @param rs ResultSet to show.
   * @param alreadyOnRow true if rs is already on the first row. false otherwise.
   * @param start Timestamp in milliseconds when executing the statement started. This timestamp is
   *     used to compute statistics about the statement. If no statistics should be shown, set it to
   *     0.
   * @param show Functions to map columns
   * @throws SQLException
   */
  private void showResultSetPretty(
      final ResultSet rs, boolean alreadyOnRow, long start, Function... show) throws SQLException {
    final ResultSetMetaData meta = rs.getMetaData();

    final Function[] columnMap;
    if (show != null && 0 < show.length) {
      columnMap = show;

    } else {
      final int colCnt = meta.getColumnCount();
      columnMap = new Function[colCnt];
      for (int colId = 0; colId < colCnt; colId++) {
        final int p = colId + 1;
        final String name = meta.getColumnLabel(p);
        columnMap[colId] = new Identity(p, name);
      }
    }

    final int colCnt = columnMap.length;
    final int[] widths = new int[colCnt];
    for (int c = 0; c < colCnt; c++) {
      widths[c] = columnMap[c].name.length();
    }

    final List<String[]> rows = new ArrayList<>();
    while (alreadyOnRow || rs.next()) {
      final String[] row = new String[columnMap.length];
      for (int c = 0; c < colCnt; c++) {
        row[c] = columnMap[c].apply(rs);
        if (row[c] == null) {
          row[c] = "NULL";
        }
        widths[c] = Math.max(widths[c], row[c].length());
      }
      rows.add(row);
      alreadyOnRow = false;
    }

    final StringBuilder b = new StringBuilder();
    for (int c = 0; c < colCnt; c++) {
      if (0 < c) {
        b.append(" | ");
      }

      String n = columnMap[c].name;
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

    if (start != 0) {
      final int rowCount = rows.size();
      final long ms = TimeUtil.nowMs() - start;
      println("(" + rowCount + (rowCount == 1 ? " row" : " rows") + "; " + ms + " ms)");
    }
  }

  private void warning(final String msg) {
    switch (outputFormat) {
      case JSON_SINGLE:
      case JSON:
        {
          final JsonObject obj = new JsonObject();
          obj.addProperty("type", "warning");
          obj.addProperty("message", msg);
          println(obj.toString());
          break;
        }

      case PRETTY:
      default:
        println("WARNING: " + msg);
        break;
    }
  }

  private void error(final SQLException err) {
    switch (outputFormat) {
      case JSON_SINGLE:
      case JSON:
        {
          final JsonObject obj = new JsonObject();
          obj.addProperty("type", "error");
          obj.addProperty("message", err.getMessage());
          println(obj.toString());
          break;
        }

      case PRETTY:
      default:
        println("ERROR: " + err.getMessage());
        break;
    }
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
    if (outputFormat == OutputFormat.PRETTY) {
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

  private abstract static class Function {
    final String name;

    Function(final String name) {
      this.name = name;
    }

    abstract String apply(ResultSet rs) throws SQLException;
  }

  private static class Identity extends Function {
    static Identity create(final ResultSet rs, final String name) throws SQLException {
      return new Identity(rs.findColumn(name), name);
    }

    final int colId;

    Identity(final int colId, final String name) {
      super(name);
      this.colId = colId;
    }

    @Override
    String apply(final ResultSet rs) throws SQLException {
      return rs.getString(colId);
    }
  }

  private static class IndexInfo {
    String name;
    boolean unique;
    final Map<Integer, String> columns = new TreeMap<>();
    final StringBuilder filter = new StringBuilder();

    void addColumn(int pos, String column) {
      columns.put(Integer.valueOf(pos), column);
    }

    @Override
    public String toString() {
      final StringBuilder r = new StringBuilder();
      r.append(name);
      if (unique) {
        r.append(" UNIQUE");
      }
      r.append(" (");
      boolean first = true;
      for (String c : columns.values()) {
        if (!first) {
          r.append(", ");
        }
        r.append(c);
        first = false;
      }
      r.append(")");
      if (filter.length() > 0) {
        r.append(" WHERE ");
        r.append(filter);
      }
      return r.toString();
    }
  }
}
