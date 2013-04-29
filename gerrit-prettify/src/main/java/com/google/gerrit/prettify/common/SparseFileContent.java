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

package com.google.gerrit.prettify.common;


import org.eclipse.jgit.diff.Edit;

import java.util.ArrayList;
import java.util.List;

public class SparseFileContent {
  protected String path;
  protected List<Range> ranges;
  protected int size;
  protected boolean missingNewlineAtEnd;

  private transient int currentRangeIdx;

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

  public String getPath() {
    return path;
  }

  public void setPath(String filePath) {
    path = filePath;
  }

  public boolean isWholeFile() {
    if (size == 0) {
      return true;

    } else if (1 == ranges.size()) {
      Range r = ranges.get(0);
      return r.base == 0 && r.end() == size;

    } else {
      return false;
    }
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

  public int first() {
    return ranges.isEmpty() ? size() : ranges.get(0).base;
  }

  public int next(final int idx) {
    // Most requests are sequential in nature, fetching the next
    // line from the current range, or the immediate next range.
    //
    int high = ranges.size();
    if (currentRangeIdx < high) {
      Range cur = ranges.get(currentRangeIdx);
      if (cur.contains(idx + 1)) {
        return idx + 1;
      }

      if (++currentRangeIdx < high) {
        // Its not plus one, its the base of the next range.
        //
        return ranges.get(currentRangeIdx).base;
      }
    }

    // Binary search for the current value, since we know its a sorted list.
    //
    int low = 0;
    do {
      final int mid = (low + high) / 2;
      final Range cur = ranges.get(mid);

      if (cur.contains(idx)) {
        if (cur.contains(idx + 1)) {
          // Trivial plus one case above failed due to wrong currentRangeIdx.
          // Reset the cache so we don't miss in the future.
          //
          currentRangeIdx = mid;
          return idx + 1;
        }

        if (mid + 1 < ranges.size()) {
          // Its the base of the next range.
          currentRangeIdx = mid + 1;
          return ranges.get(currentRangeIdx).base;
        }

        // No more lines in the file.
        //
        return size();
      }

      if (idx < cur.base)
        high = mid;
      else
        low = mid + 1;
    } while (low < high);

    return size();
  }

  public int mapIndexToLine(int arrayIndex) {
    final int origIndex = arrayIndex;
    for (Range r : ranges) {
      if (arrayIndex < r.lines.size()) {
        return r.base + arrayIndex;
      }
      arrayIndex -= r.lines.size();
    }
    throw new ArrayIndexOutOfBoundsException(origIndex);
  }

  private String getLine(final int idx) {
    // Most requests are sequential in nature, fetching the next
    // line from the current range, or the next range.
    //
    int high = ranges.size();
    if (currentRangeIdx < high) {
      Range cur = ranges.get(currentRangeIdx);
      if (cur.contains(idx)) {
        return cur.get(idx);
      }

      if (++currentRangeIdx < high) {
        final Range next = ranges.get(currentRangeIdx);
        if (next.contains(idx)) {
          return next.get(idx);
        }
      }
    }

    // Binary search for the range, since we know its a sorted list.
    //
    if (ranges.isEmpty()) {
      return null;
    }

    int low = 0;
    do {
      final int mid = (low + high) / 2;
      final Range cur = ranges.get(mid);
      if (cur.contains(idx)) {
        currentRangeIdx = mid;
        return cur.get(idx);
      }
      if (idx < cur.base)
        high = mid;
      else
        low = mid + 1;
    } while (low < high);
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

  public String asString() {
    final StringBuilder b = new StringBuilder();
    for (Range r : ranges) {
      for (String l : r.lines) {
        b.append(l);
        b.append('\n');
      }
    }
    if (0 < b.length() && isMissingNewlineAtEnd()) {
      b.setLength(b.length() - 1);
    }
    return b.toString();
  }

  public SparseFileContent apply(SparseFileContent a, List<Edit> edits) {
    EditList list = new EditList(edits, size, a.size(), size);
    ArrayList<String> lines = new ArrayList<String>(size);
    for (final EditList.Hunk hunk : list.getHunks()) {
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          if (contains(hunk.getCurB())) {
            lines.add(get(hunk.getCurB()));
          } else {
            lines.add(a.get(hunk.getCurA()));
          }
          hunk.incBoth();
          continue;
        }

        if (hunk.isDeletedA()) {
          hunk.incA();
        }

        if (hunk.isInsertedB()) {
          lines.add(get(hunk.getCurB()));
          hunk.incB();
        }
      }
    }

    Range range = new Range();
    range.lines = lines;

    SparseFileContent r = new SparseFileContent();
    r.setSize(lines.size());
    r.setMissingNewlineAtEnd(isMissingNewlineAtEnd());
    r.setPath(getPath());
    r.ranges.add(range);

    return r;
  }

  @Override
  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("SparseFileContent[\n");
    for (Range r : ranges) {
      b.append("  ");
      b.append(r.toString());
      b.append('\n');
    }
    b.append("]");
    return b.toString();
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

    @Override
    public String toString() {
      // Usage of [ and ) is intentional to denote inclusive/exclusive range
      return "Range[" + base + "," + end() + ")";
    }
  }
}
