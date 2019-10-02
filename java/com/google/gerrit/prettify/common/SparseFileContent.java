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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * A class to store subset of a file's lines in a memory efficient way. Internally, it stores lines
 * as a list of ranges. Each range represents continuous set of lines and has information about line
 * numbers in original file (zero-based).
 *
 * <p>{@link SparseFileContent.Accessor} must be used to work with the stored content.
 */
@AutoValue
public abstract class SparseFileContent {
  abstract ImmutableList<Range> getRanges();

  public abstract int getSize();

  public static SparseFileContent create(ImmutableList<Range> ranges, int size) {
    return new AutoValue_SparseFileContent(ranges, size);
  }

  @VisibleForTesting
  public int getRangesCount() {
    return getRanges().size();
  }

  public Accessor createAccessor() {
    return new Accessor(this);
  }

  /**
   * Provide a methods to work with the content of a {@link SparseFileContent}.
   *
   * <p>The class hides internal representation of a {@link SparseFileContent} and provides
   * convenient way for accessing a content.
   */
  public static class Accessor {
    private final SparseFileContent content;
    private int currentRangeIdx;

    private Accessor(SparseFileContent content) {
      this.content = content;
    }

    public String get(int idx) {
      final String line = getLine(idx);
      if (line == null) {
        throw new ArrayIndexOutOfBoundsException(idx);
      }
      return line;
    }

    public int getSize() {
      return content.getSize();
    }

    public boolean contains(int idx) {
      return getLine(idx) != null;
    }

    public int first() {
      return content.getRanges().isEmpty() ? getSize() : content.getRanges().get(0).getBase();
    }

    public int next(int idx) {
      // Most requests are sequential in nature, fetching the next
      // line from the current range, or the immediate next range.
      //
      ImmutableList<Range> ranges = content.getRanges();
      int high = ranges.size();
      if (currentRangeIdx < high) {
        Range cur = ranges.get(currentRangeIdx);
        if (cur.contains(idx + 1)) {
          return idx + 1;
        }

        if (++currentRangeIdx < high) {
          // Its not plus one, its the base of the next range.
          //
          return ranges.get(currentRangeIdx).getBase();
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
            return ranges.get(currentRangeIdx).getBase();
          }

          // No more lines in the file.
          //
          return getSize();
        }

        if (idx < cur.getBase()) {
          high = mid;
        } else {
          low = mid + 1;
        }
      } while (low < high);

      return getSize();
    }

    private String getLine(int idx) {
      // Most requests are sequential in nature, fetching the next
      // line from the current range, or the next range.
      //
      ImmutableList<Range> ranges = content.getRanges();
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
        if (idx < cur.getBase()) {
          high = mid;
        } else {
          low = mid + 1;
        }
      } while (low < high);
      return null;
    }
  }

  @Override
  public final String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("SparseFileContent[\n");
    for (Range r : getRanges()) {
      b.append("  ");
      b.append(r.toString());
      b.append('\n');
    }
    b.append("]");
    return b.toString();
  }

  @AutoValue
  abstract static class Range {
    static Range create(int base, ImmutableList<String> lines) {
      return new AutoValue_SparseFileContent_Range(base, lines);
    }

    abstract int getBase();

    abstract ImmutableList<String> getLines();

    private String get(int i) {
      return getLines().get(i - getBase());
    }

    private int end() {
      return getBase() + getLines().size();
    }

    private boolean contains(int i) {
      return getBase() <= i && i < end();
    }

    @Override
    public final String toString() {
      // Usage of [ and ) is intentional to denote inclusive/exclusive range
      return "Range[" + getBase() + "," + end() + ")";
    }
  }
}
