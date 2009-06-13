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

package com.google.gerrit.client.data;

import java.util.ArrayList;
import java.util.List;

public class SparseFileContent {
  protected List<Range> ranges;
  protected int size;
  protected boolean missingNewlineAtEnd;
  private transient int lastGetRange;

  public SparseFileContent() {
    ranges = new ArrayList<Range>();
  }

  public int size() {
    return size;
  }

  public void setSize(final int s) {
    size = s;
  }

  public boolean isMissingNewlineAtEnd() {
    return missingNewlineAtEnd;
  }

  public void setMissingNewlineAtEnd(final boolean missing) {
    missingNewlineAtEnd = missing;
  }

  public String get(final int idx) {
    final String line = getLine(idx);
    if (line == null) {
      throw new ArrayIndexOutOfBoundsException(idx);
    }
    return line;
  }

  public boolean contains(final int idx) {
    return getLine(idx) != null;
  }

  private String getLine(final int idx) {
    for (int i = lastGetRange; i < ranges.size(); i++) {
      final Range r = ranges.get(i);
      if (r.contains(idx)) {
        lastGetRange = i;
        return r.get(idx);
      }
    }
    if (lastGetRange != 0) {
      lastGetRange = 0;
      return getLine(idx);
    }
    return null;
  }

  public void addLine(final int i, final String content) {
    final Range r;
    if (!ranges.isEmpty() && i == last().end()) {
      r = last();
    } else {
      r = new Range(i);
      ranges.add(r);
    }
    r.lines.add(content);
  }

  private Range last() {
    return ranges.get(ranges.size() - 1);
  }

  static class Range {
    protected int base;
    protected List<String> lines;

    private Range(final int b) {
      base = b;
      lines = new ArrayList<String>();
    }

    protected Range() {
    }

    private String get(final int i) {
      return lines.get(i - base);
    }

    private int end() {
      return base + lines.size();
    }

    private boolean contains(final int i) {
      return base <= i && i < end();
    }
  }
}
