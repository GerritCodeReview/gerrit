// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabFile {
  protected static class Row {
    public String left;
    public String right;

    public Row(String left, String right) {
      this.left = left;
      this.right = right;
    }
  }

  protected static List<Row> parse(String text, String filename,
      ValidationError.Sink errors) throws IOException {
    List<Row> rows = new ArrayList<>();
    BufferedReader br = new BufferedReader(new StringReader(text));
    String s;
    for (int lineNumber = 1; (s = br.readLine()) != null; lineNumber++) {
      if (s.isEmpty() || s.startsWith("#")) {
        continue;
      }

      int tab = s.indexOf('\t');
      if (tab < 0) {
        errors.error(new ValidationError(filename, lineNumber,
            "missing tab delimiter"));
        continue;
      }

      rows.add(new Row(s.substring(0, tab).trim(),
          s.substring(tab + 1).trim()));
    }
    return rows;
  }

  protected static Map<String, String> toMap(List<Row> rows) {
    Map<String, String> map = new HashMap<>(rows.size());
    for (Row row : rows) {
      map.put(row.left, row.right);
    }
    return map;
  }

  protected static String asText(String left, String right,
      Map<String, String> entries) {
    if (entries.isEmpty()) {
      return null;
    }

    List<Row> rows = new ArrayList<>(entries.size());
    for (String key : sort(entries.keySet())) {
      rows.add(new Row(key, entries.get(key)));
    }
    return asText(left, right, rows);
  }

  protected static String asText(String left, String right, List<Row> rows) {
    if (rows.isEmpty()) {
      return null;
    }

    left = "# " + left;
    int leftLen = left.length();
    for (Row row : rows) {
      leftLen = Math.max(leftLen, row.left.length());
    }

    StringBuilder buf = new StringBuilder();
    buf.append(pad(leftLen, left));
    buf.append('\t');
    buf.append(right);
    buf.append('\n');

    buf.append('#');
    buf.append('\n');

    for (Row row : rows) {
      buf.append(pad(leftLen, row.left));
      buf.append('\t');
      buf.append(row.right);
      buf.append('\n');
    }
    return buf.toString();
  }

  protected static <T extends Comparable<? super T>> List<T> sort(Collection<T> m) {
    ArrayList<T> r = new ArrayList<>(m);
    Collections.sort(r);
    return r;
  }

  protected static String pad(int len, String src) {
    if (len <= src.length()) {
      return src;
    }

    StringBuilder r = new StringBuilder(len);
    r.append(src);
    while (r.length() < len) {
      r.append(' ');
    }
    return r.toString();
  }
}
